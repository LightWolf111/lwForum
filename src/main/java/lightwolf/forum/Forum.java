package lightwolf.forum;

import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;

public class Forum extends JavaPlugin {

    private ForumRegisterPlugin registerPlugin;

    @Override
    public void onEnable() {
        File dataFolder = getDataFolder();
        registerPlugin = new ForumRegisterPlugin(this, dataFolder);
        getCommand("forum").setExecutor(registerPlugin);
    }

    @Override
    public void onDisable() {
        if (registerPlugin != null) {
            registerPlugin.saveUsersToDatabase();
        }
    }
}
