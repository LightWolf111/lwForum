package lightwolf.forum;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;

public class ForumRegisterPlugin implements CommandExecutor {
    private final Logger logger = Logger.getLogger("ForumRegisterPlugin");

    private Map<String, String> users;
    private FileConfiguration config;
    private Connection connection;
    private String databaseInitErrorMessage;
    private String databaseConnectionErrorMessage;

    private String url;
    private String username;
    private String password;
    private final JavaPlugin plugin;

    public ForumRegisterPlugin(JavaPlugin plugin, File dataFolder) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        this.config.options().copyDefaults(true);
        plugin.saveDefaultConfig();

        // Исправление: Убран лишний вызов initDatabase()
        this.url = "jdbc:mysql://" + config.getString("database.host") + ":" +
                config.getInt("database.port") + "/" + config.getString("database.name");
        this.username = config.getString("database.username");
        this.password = config.getString("database.password");

        initDatabase();
        this.users = new HashMap<>();
        loadUsersFromDatabase();
        loadErrorMessages();
        plugin.getCommand("forum").setExecutor(this);
    }

    private void loadErrorMessages() {
        databaseInitErrorMessage = config.getString("message.databaseInit");
        databaseConnectionErrorMessage = config.getString("message.databaseConnection");
    }

    public String applyColor(String message) {
        String[] parts = message.split("&");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {

            if (i != 0 && !parts[i].isEmpty()) {
                ChatColor color = ChatColor.getByChar(parts[i].charAt(0));

                if (color != null) {
                    result.append(color);
                }

                if (parts[i].length() > 1) {
                    result.append(parts[i].substring(1));
                }
            } else {
                result.append(parts[i]);
            }
        }

        return result.toString();
    }



    private void initDatabase() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");

            connection = DriverManager.getConnection(url, username, password);

            if (connection == null || connection.isClosed()) {
                logger.severe("Unable to establish a connection to the database. Disabling the plugin.");
                plugin.getServer().getPluginManager().disablePlugin(plugin);
                return;
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS xf_user (user_id INT AUTO_INCREMENT PRIMARY KEY, " +
                            "username VARCHAR(255) UNIQUE, email VARCHAR(255), language_id INT, " +
                            "style_id INT, timezone VARCHAR(255), user_group_id INT, secondary_group_ids VARCHAR(255), " +
                            "display_style_group_id INT, permission_combination_id INT, register_date BIGINT, last_activity BIGINT, " +
                            "secret_key VARCHAR(255))"
            )) {
                statement.executeUpdate();
            }
        } catch (ClassNotFoundException | SQLException e) {
            logger.severe(databaseInitErrorMessage);
            e.printStackTrace();
            logger.severe(databaseConnectionErrorMessage);
        }
    }


    private void loadUsersFromDatabase() {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM xf_user");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String username = resultSet.getString("username");
                users.put(username, password);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        System.out.println("Использована команда: " + command.getName());
        int minLength = config.getInt("password.minLength", 8);
        int maxLength = config.getInt("password.maxLength", 16);
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (args.length == 1) {
                if (!sender.hasPermission("lForum.reload")) {
                    sender.sendMessage(ChatColor.RED + "У вас нет разрешения для перезагрузки плагина.");
                    return true;
                }
                System.out.println("Перезагрузка конфигурации...");
                plugin.reloadConfig();
                config = plugin.getConfig();

                System.out.println("Конфигурация перезагружена.");
                System.out.println("Загрузка сообщений об ошибках...");
                loadErrorMessages();
                System.out.println("Сообщения об ошибках загружены.");

                sender.sendMessage(applyColor(config.getString("prefix") + "&a&lПлагин успешно перезагружен."));
                return true;
            } else {
                sender.sendMessage(ChatColor.RED + "Использование: /forum reload");
                return true;
            }
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(applyColor(config.getString("prefix") + getErrorMessage("notPlayer")));
            return true;
        }
        Player player = (Player) sender;

        if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            if (!player.hasPermission("lForum.remove")) {
                player.sendMessage(applyColor(config.getString("prefix") + getErrorMessage("noPermission")));
                return true;
            }
            String usernameToRemove = args[1];
            if (unregisterPlayer(usernameToRemove, sender)) {
                sender.sendMessage(applyColor(config.getString("prefix") + getErrorMessage("removeSuccess")));
            } else {
                sender.sendMessage(applyColor(config.getString("prefix") + getErrorMessage("removeNotFound")));
            }

            return true;
        }

        if (args.length != 2) {
            player.sendMessage(applyColor(config.getString("prefix") + getErrorMessage("invalidCommandUsage")));
            return true;
        }

        String password = args[1];
        String username = player.getName();

        if (password.length() < minLength) {
            player.sendMessage(applyColor(config.getString("prefix") + getErrorMessage("MinlengthPassword").replace("{minLength}", String.valueOf(minLength))));
            return true;
        }

        if (password.length() > maxLength) {
            player.sendMessage(applyColor(config.getString("prefix") + getErrorMessage("MaxlengthPassword").replace("{maxLength}", String.valueOf(maxLength))));
            return true;
        }

        if (registerPlayer(username, password)) {
            sender.sendMessage(applyColor(config.getString("prefix") + getErrorMessage("successfully")));
        } else {
            player.sendMessage(applyColor(config.getString("prefix") + getErrorMessage("alreadyRegistered")));
        }

        return true;
    }


    private String getErrorMessage(String key) {
        String errorMessage = config.getString("message." + key);
        return errorMessage != null ? errorMessage : "noPermission";
    }

    public boolean registerPlayer(String username, String password) {
        if (password.length() < 8 || password.length() > 16) {
            return false;
        }

        if (isUserRegistered(username)) {
            return false;
        }

        users.put(username, password);
        saveUserToDatabase(username, password);
        return true;
    }




    public boolean unregisterPlayer(String username, CommandSender sender) {
        if (users.containsKey(username)) {
            users.remove(username);
            try {
                deleteUserFromDatabase(username, sender);
                return true;
            } catch (SQLException e) {
                e.printStackTrace();
                sender.sendMessage(ChatColor.RED + "Произошла ошибка при отмене регистрации пользователя. Пожалуйста, повторите попытку позже.");
            }
        }
        return false;
    }

    private int getUserIdByUsername(String username) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT user_id FROM xf_user WHERE username = ?")) {
            statement.setString(1, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt("user_id") : -1;
            }
        }
    }

    private void deleteFromTable(String query, int userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, userId);
            statement.executeUpdate();
        }
    }
    private void deleteUserFromDatabase(String username, CommandSender sender) throws SQLException {
        try {
            int userId = getUserIdByUsername(username);

            if (userId != -1) {
                deleteFromTable("DELETE FROM xf_user WHERE user_id = ?", userId);
                System.out.println("Пользователь " + username + " был удален из таблицы xf_user.");

                deleteRelatedRecords(userId);

                System.out.println("Пользователь " + username + " был удален из всех связанных таблиц форума.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            logger.severe("Error deleting user from database: " + e.getMessage());
            sender.sendMessage(ChatColor.RED + "Произошла ошибка при отмене регистрации пользователя. Пожалуйста, повторите попытку позже.");
        }
    }
    private void deleteRelatedRecords(int userId) throws SQLException {
        deleteFromTable("DELETE FROM xf_user_authenticate WHERE user_id = ?", userId);
        deleteFromTable("DELETE FROM xf_user_privacy WHERE user_id = ?", userId);
        deleteFromTable("DELETE FROM xf_user_option WHERE user_id = ?", userId);
        deleteFromTable("DELETE FROM xf_user_profile WHERE user_id = ?", userId);
    }

    public boolean isUserRegistered(String username) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM xf_user WHERE username = ?")) {
            statement.setString(1, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }


    public boolean saveUserToDatabase(String playerName, String password) {
        int userId = -1;
        try {
            Random r = new Random();
            int randint = r.nextInt(1000000);
            String salt = null;
            try {
                salt = Encryption.md5("" + randint);
                salt = Encryption.SHA256(salt.substring(0, 10));
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }

            long timestamp = System.currentTimeMillis() / 1000L;
            String hash = hash(1, salt, password);

            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO `xf_user` (`username`, `email`, `language_id`, `style_id`, `timezone`, `user_group_id`, `secondary_group_ids`, `display_style_group_id`, `permission_combination_id`, `register_date`, `last_activity`, `secret_key`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '')",
                    PreparedStatement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, playerName);
                ps.setString(2, playerName + "@gldmine.com");
                ps.setInt(3, 1);
                ps.setInt(4, 0);
                ps.setString(5, "Europe/London");
                ps.setInt(6, 2);
                ps.setString(7, "");
                ps.setInt(8, 2);
                ps.setInt(9, 9);
                ps.setLong(10, timestamp);
                ps.setLong(11, timestamp);

                ps.executeUpdate();

                ResultSet rs = ps.getGeneratedKeys();
                if (rs.next()) {
                    userId = rs.getInt(1);
                } else {
                    return false;
                }

            }


            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO `xf_user_authenticate` (`user_id`, `scheme_class`, `data`) VALUES (?, ?, ?)")) {
                ps.setInt(1, userId);
                ps.setString(2, "XenForo_Authentication_Core");
                String xenForoData = "a:3:{s:4:\"hash\";s:64:\"" + hash + "\";s:4:\"salt\";s:64:\"" + salt + "\";s:8:\"hashFunc\";s:6:\"sha256\";}";
                try {
                    ps.setBytes(3, xenForoData.getBytes("UTF-8"));
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }

                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO `xf_user_privacy` (`user_id`, `allow_post_profile`, `allow_send_personal_conversation`) VALUES (?, ?, ?)")) {
                ps.setInt(1, userId);
                ps.setString(2, "members");
                ps.setString(3, "members");
                ps.executeUpdate();
            }

            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO `xf_user_option` (`user_id`, `show_dob_year`, `show_dob_date`, `content_show_signature`, `receive_admin_email`, `email_on_conversation`, `push_on_conversation`, `is_discouraged`, `creation_watch_state`, `interaction_watch_state`, `alert_optout`, `push_optout`, `use_tfa`) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setInt(1, userId);
                ps.setInt(2, 0);
                ps.setInt(3, 0);
                ps.setInt(4, 1);
                ps.setInt(5, 0);
                ps.setInt(6, 0);
                ps.setInt(7, 0);
                ps.setInt(8, 0);
                ps.setString(9, "watch_no_email");
                ps.setString(10, "watch_no_email");
                ps.setString(11, "");
                ps.setString(12, "");
                ps.setInt(13, 0);
                ps.executeUpdate();
            }


            String stringdata1 = "a:0:{}";
            byte[] bArr1 = stringdata1.getBytes();
            ByteArrayInputStream bIn1 = new ByteArrayInputStream(bArr1);
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO `xf_user_profile` (`user_id`, `dob_day`, `dob_month`, `dob_year`, `signature`, `website`, `location`, `following`, `ignored`, `avatar_crop_x`, `avatar_crop_y`, `about`, `custom_fields`, `connected_accounts`, `password_date`) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setInt(1, userId);
                ps.setInt(2, 0);
                ps.setInt(3, 0);
                ps.setInt(4, 0);
                ps.setString(5, "");
                ps.setString(6, "");
                ps.setString(7, "");
                ps.setString(8, "");
                ps.setString(9, "[]");
                ps.setInt(10, 0);
                ps.setInt(11, 0);
                ps.setString(12, "");
                ps.setBytes(13, new byte[0]);
                ps.setString(14, "");
                ps.setInt(15, 1);

                ps.executeUpdate();
            }



            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }

    }

    private String hash(int checkId, String salt, String password) {
        try {
            if (checkId == 1) {
                return passwordHash(password, salt);
            } else if (checkId == 2) {
                return Encryption.SHA1(salt + password);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "fail";
    }

    private String passwordHash(String password, String salt) {
        try {
            return Encryption.SHA256(Encryption.SHA256(password) + salt);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        return "fail";
    }
    public void saveUsersToDatabase() {
        try {
            for (Map.Entry<String, String> entry : users.entrySet()) {
                String username = entry.getKey();
                String password = entry.getValue();

                // Проверка существования пользователя перед вставкой
                if (isUserRegistered(username)) {
                    // Обновление существующей записи
                    updateUserInDatabase(username, password);
                } else {
                    // Вставка новой записи
                    saveUserToDatabase(username, password);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void updateUserInDatabase(String username, String password) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE users SET password = ? WHERE username = ?")) {
            ps.setString(1, password);
            ps.setString(2, username);
            ps.executeUpdate();
        }
    }




}
