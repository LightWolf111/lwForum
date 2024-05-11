import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class XenForoRegistrationPlugin extends JavaPlugin {

    private final String xenForoApiUrl = "https://your-xenforo-site.com/api/register";

    @Override
    public void onEnable() {
        // Ваш код для инициализации плагина
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("register")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                String playerName = player.getName();
                String playerUuid = player.getUniqueId().toString();

                // Выполняем регистрацию на форуме
                if (registerOnXenForo(playerName, playerUuid)) {
                    player.sendMessage("Регистрация успешна!");
                } else {
                    player.sendMessage("Ошибка регистрации. Пожалуйста, попробуйте позже.");
                }

                return true;
            }
        }

        return false;
    }

    private boolean registerOnXenForo(String playerName, String playerUuid) {
        try {
            // Создаем запрос к API XenForo
            URL url = new URL(xenForoApiUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            // Подготавливаем данные для отправки
            String jsonInputString = "{\"username\": \"" + playerName + "\", \"uuid\": \"" + playerUuid + "\"}";
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // Получаем ответ от сервера
            int responseCode = connection.getResponseCode();

            // Ваш код обработки ответа, например, проверка на успешность регистрации

            return responseCode == HttpURLConnection.HTTP_OK;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
