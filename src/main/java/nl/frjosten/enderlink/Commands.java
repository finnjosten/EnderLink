package nl.frjosten.enderlink;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.net.http.WebSocket;

public class Commands implements CommandExecutor, TabCompleter {
    private final EnderLink plugin;
    private final String serverId;
    private final WebSocket ws;

    public Commands(EnderLink plugin, String serverId, WebSocket ws) {
        this.plugin = plugin;
        this.serverId = serverId;
        this.ws = ws;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = Arrays.asList("reconnect", "id", "status", "help");
            String current = args[0].toLowerCase();
            if (current.isEmpty()) return subs;
            return subs.stream().filter(s -> s.startsWith(current)).toList();
        }
        return Collections.emptyList();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§aEnderLink plugin is active. Use /enderlink <reconnect|id|status>");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reconnect":
                plugin.getWebsocketClass().reconnect();
                sender.sendMessage("§a[EnderLink] Attempting to reconnect to WebSocket...");
                return true;
            case "id":
                sender.sendMessage(plugin.getMessagesConfig().getString("settings-server-id").replace("{server_id}", serverId));
                sender.sendMessage(plugin.getMessagesConfig().getString("settings-room-id").replace("{room_id}", plugin.roomId));
                return true;
            case "status":
                sender.sendMessage(plugin.getMessagesConfig().getString("settings-status").replace("{status}", (plugin.getWebsocketClass().connected() ? "§aConnected" : "§cDisconnected")));
                return true;
            case "help":
                sender.sendMessage("§7[§dEnderLink§7] §fAvailable commands:");
                sender.sendMessage("§d/enderlink reconnect §7- §fReconnect to the WebSocket server.");
                sender.sendMessage("§d/enderlink id §7- §fShow the server and room ID.");
                sender.sendMessage("§d/enderlink status §7- §fShow the current connection status.");
                sender.sendMessage("§d/enderlink help §7- §fShow this help message.");
                return true;
            default:
                sender.sendMessage(plugin.getMessagesConfig().getString("settings-unknown"));
                return true;
        }
    }
}
