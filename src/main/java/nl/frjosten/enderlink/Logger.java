package nl.frjosten.enderlink;

import org.bukkit.Bukkit;

public class Logger {
    public void info(String msg) {
        Bukkit.getLogger().info(msg);
    }

    public void warning(String msg) {
        Bukkit.getLogger().warning(msg);
    }

    public void error(String msg) {
        Bukkit.getLogger().severe(msg);
    }

    public void severe(String msg) {
        error(msg);
    }
}
