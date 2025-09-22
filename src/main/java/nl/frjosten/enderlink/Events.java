package nl.frjosten.enderlink;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.json.JSONObject;
import org.bukkit.event.Listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import java.util.List;

public class Events implements Listener {
    private final EnderLink plugin;
    private final String serverId;
    private final Logger logger;

    public Events(EnderLink plugin, String serverId) {
        this.plugin = plugin;
        this.serverId = serverId;

        this.logger = plugin.getLoggerClass();
    }






    // Event handling
    @EventHandler
    public void onChat(AsyncChatEvent  event) {
        if (!checkEvent("chat")) return;
        String playerName = event.getPlayer().getName();
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());

        wsSend("{ \"serverId\": \"" + serverId + "\", \"type\": \"chat\", \"sender\": \"minecraft\", \"player\": \"" + playerName + "\", \"message\": \"" + message + "\" }");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!checkEvent("join")) return;
        wsSend("{ \"serverId\": \"" + serverId + "\", \"type\": \"mc_join\", \"player\": \"" + event.getPlayer().getName() + "\" }");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (!checkEvent("leave")) return;
        wsSend("{ \"serverId\": \"" + serverId + "\", \"type\": \"mc_quit\", \"player\": \"" + event.getPlayer().getName() + "\" }");
    }

    @EventHandler
    public void onDead(PlayerDeathEvent event) {
        if (!checkEvent("death")) return;
        String playerName = event.getPlayer().getName();

        // deathMessage() returns a Component
        Component deathMessage = event.deathMessage();

        // Convert Component → plain String (no colors, just text)
        String reason = deathMessage != null 
            ? PlainTextComponentSerializer.plainText().serialize(deathMessage)
            : "Unknown";

        wsSend("{ \"serverId\": \"" + serverId + "\", \"type\": \"mc_dead\", \"player\": \"" + playerName + "\", \"reason\": \"" + reason + "\" }");
    }
    
    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        if (!checkEvent("server-start")) return;
        if (event.getType() == ServerLoadEvent.LoadType.STARTUP) {
            wsSend("{ \"serverId\": \"" + serverId + "\", \"type\": \"mc_power\", \"event\": \"up\" }");
        }
    }






    // Chat handling
    private void sendChat(JSONObject json) {
        String sender = json.optString("sender", "");
        if (!"minecraft".equalsIgnoreCase(sender)) {
            String player = json.optString("player", "Unknown");
            String message = json.optString("message", "");
            
            String finalMessage = plugin.getMessagesConfig().getString("mc-chat", "[{sender}] {player} > {message}")
                .replace("{sender}", sender.toUpperCase())
                .replace("{player}", player)
                .replace("{message}", message);
            Component componentMsg = Component.text(finalMessage);
            Bukkit.getServer().broadcast(componentMsg);
        }
    }

    private void sendChatReply(JSONObject json) {
        String sender = json.optString("sender", "");
        if (!"minecraft".equalsIgnoreCase(sender)) {
            String player = json.optString("player", "Unknown");
            String original = json.optString("original", "");
            String message = json.optString("message", "");
            
            String finalMessage = plugin.getMessagesConfig().getString("mc-reply", "[{sender}] {player} > {message} (in reply to {reply_to})")
                .replace("{sender}", sender.toUpperCase())
                .replace("{player}", player)
                .replace("{message}", message)
                .replace("{reply_to}", original);
            Component componentMsg = Component.text(finalMessage);
            Bukkit.getServer().broadcast(componentMsg);
        }
    }






    // Helper functions
    private boolean checkEvent(String type) {
        List<String> events = plugin.getGeneralConfig().getStringList("events");
        return events.contains(type);
    }






    // WS handling
    public void wsOnMessage(String msg) {

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                JSONObject json = new JSONObject(msg);
                String type = json.optString("type", "");

                if ("registered".equalsIgnoreCase(type)) {
                    logger.info("WebSocket registration successful for server: " + serverId);
                } else if ("pong".equalsIgnoreCase(type)) {
                } else if ("chat".equalsIgnoreCase(type) || "message".equalsIgnoreCase(type)) {
                    sendChat(json);
                } else if ("chat_reply".equalsIgnoreCase(type) || "message".equalsIgnoreCase(type)) {
                    sendChatReply(json);
                }
            } catch (Exception e) {
                logger.error("Failed to process WebSocket message: " + e.getMessage());
            }
        });
    }

    private void wsSend(String msg) {
        if (plugin.getWebsocketClass() != null) {
            plugin.getWebsocketClass().msg(msg);
        }
    }
}
