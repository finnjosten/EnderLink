package nl.frjosten.enderlink;

import org.bukkit.Bukkit;

public class Logger {
    private String prefix = "[EnderLink] ";

    public void info(String msg) {
        Bukkit.getLogger().info(prefix + msg);
    }

    public void warning(String msg) {
        Bukkit.getLogger().warning(prefix + msg);
    }

    public void error(String msg) {
        Bukkit.getLogger().severe(prefix + msg);
    }

    // Alias for error
    public void severe(String msg) {
        error(msg);
    }
}
