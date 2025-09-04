package com.example.enderlink;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public class EnderLink extends JavaPlugin implements Listener {
    private WebSocket ws;
    private String serverId;
    private String websocketUrl;
    private String apiUrl;
    private String roomId;
    private boolean registered = false;
    private boolean wsConnected = false;
    private long lastPingTime = 0;
    private long lastPongTime = 0;
    private FileConfiguration messagesConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveDefaultMessagesConfig();
        reloadConfig();

        // Load in messages.yml
        messagesConfig = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));

        FileConfiguration config = getConfig();
        websocketUrl = config.getString("websocket-url", "ws://james.vacso.cloud:10000");
        apiUrl = config.getString("api-url", "https://api.james.vacso.cloud");
        serverId = config.getString("server-id", "UUID");
        roomId = config.getString("room-id", serverId);

        ensureServerIdAsync();
        Bukkit.getPluginManager().registerEvents(this, this);
        connectWebSocket();
    }

    private void ensureServerIdAsync() {
        if (serverId == null || serverId.equals("UUID") || serverId.isEmpty()) {
            serverId = UUID.randomUUID().toString();
        } else {
            this.registered = true;
            getConfig().set("server-id", this.serverId);
            getConfig().set("room-id", this.roomId);
            getLogger().info("Using existing server UUID: " + serverId);
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .build();

            int count = 0;
            while (!this.registered) {
                try {
                    HttpRequest checkRequest = HttpRequest.newBuilder()
                            .uri(URI.create(apiUrl + "/uuid/check.php?uuid=" + serverId))
                            .GET()
                            .build();

                    HttpResponse<String> checkResponse = client.send(checkRequest, HttpResponse.BodyHandlers.ofString());
                    boolean isFree = checkResponse.body().contains("false");

                    if (checkResponse.statusCode() == 200 && isFree) {
                        HttpRequest registerRequest = HttpRequest.newBuilder()
                                .uri(URI.create(apiUrl + "/uuid/register.php"))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(
                                        "{\"uuid\":\"" + serverId + "\",\"name\":\"" + getServer().getName() + "\"}"
                                ))
                                .build();

                        HttpResponse<String> registerResponse = client.send(registerRequest, HttpResponse.BodyHandlers.ofString());

                        if (registerResponse.statusCode() == 200 && registerResponse.body().contains("false")) {
                            this.registered = true;
                            getLogger().info("Registered server UUID: " + serverId);
                            break;
                        } else {
                            getLogger().severe("Failed to register server UUID: " + serverId + " Response: " + registerResponse.body());
                        }
                    } else {
                        getLogger().warning("Server UUID is already registered: " + serverId);
                    }

                    if (!this.registered) {
                        serverId = UUID.randomUUID().toString();
                        count++;
                        getLogger().info("Checking new server UUID: " + serverId);
                    }

                    if (count >= 5) {
                        getLogger().severe("Failed to register a unique server UUID after 5 attempts. Stopping.");
                        break;
                    }
                } catch (Exception e) {
                    getLogger().severe("Failed to check/register UUID: " + e.getMessage());
                    break;
                }
            }

            Bukkit.getScheduler().runTask(this, () -> {
                if (this.registered) {
                    getConfig().set("server-id", serverId);
                    if (roomId == null || roomId.isEmpty() || roomId.equals("UUID")) {
                        roomId = serverId;
                    }
                    getConfig().set("room-id", roomId);
                    try {
                        getConfig().save(new File(getDataFolder(), "config.yml"));
                    } catch (IOException e) {
                        getLogger().severe("Failed to save config.yml with server-id!");
                    }
                }
            });
        });
    }

    private void connectWebSocket() {
        HttpClient client = HttpClient.newHttpClient();
        client.newWebSocketBuilder()
                .buildAsync(URI.create(websocketUrl), new WebSocket.Listener() {
                    private boolean registeredWs = false;

                    @Override
                    public void onOpen(WebSocket webSocket) {
                        getLogger().info("Connected to WebSocket relay as " + roomId);
                        ws = webSocket;

                        // Send register message
                        ws.sendText("{\"type\":\"register\",\"roomId\":\"" + roomId + "\"}", true);
                        lastPingTime = System.currentTimeMillis();
                        wsConnected = true;

                        // Ping every minute
                        Bukkit.getScheduler().runTaskTimerAsynchronously(EnderLink.this, () -> {
                            if (ws != null) ws.sendText("{\"type\":\"ping\"}", true);

                            // Check pong timeout
                            lastPingTime = System.currentTimeMillis();

                            // Reconnect if no pong in 2 minutes
                            if (lastPongTime > 0 && (System.currentTimeMillis() - lastPongTime) > 120000) {
                                wsConnected = false;
                                getLogger().warning("WebSocket pong timeout, attempting to reconnect...");
                                try {
                                    if (ws != null) ws.sendClose(WebSocket.NORMAL_CLOSURE, "Pong timeout");
                                } catch (Exception ignored) {}
                                connectWebSocket();

                                Bukkit.getScheduler().runTask(EnderLink.this, () ->
                                    Bukkit.getOnlinePlayers().stream()
                                            .filter(p -> p.isOp())
                                            .forEach(p -> p.sendMessage(messagesConfig.getString("error-op-disconnect")))
                                );
                            }
                        }, 0L, 1200L);

                        // Global alert every 5 minutes
                        Bukkit.getScheduler().runTaskTimer(EnderLink.this, () -> {
                            if (!wsConnected) {
                                Bukkit.broadcastMessage(messagesConfig.getString("error-disconnect"));
                            }
                        }, 0L, 6000L);

                        WebSocket.Listener.super.onOpen(webSocket);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        String msg = data.toString();

                        Bukkit.getScheduler().runTask(EnderLink.this, () -> {
                            try {
                                JSONObject json = new JSONObject(msg);
                                String type = json.optString("type", "");
                                lastPongTime = System.currentTimeMillis();
                                wsConnected = true;

                                if ("registered".equalsIgnoreCase(type)) {
                                    getLogger().info("WebSocket registration successful for server: " + serverId);
                                } else if ("pong".equalsIgnoreCase(type)) {
                                } else if ("chat".equalsIgnoreCase(type) || "message".equalsIgnoreCase(type)) {
                                    sendChat(json);
                                } else if ("chat_reply".equalsIgnoreCase(type) || "message".equalsIgnoreCase(type)) {
                                    sendChatReply(json);
                                }
                            } catch (Exception e) {
                                getLogger().severe("Failed to process WebSocket message: " + e.getMessage());
                            }
                        });

                        return WebSocket.Listener.super.onText(webSocket, data, last);
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        getLogger().severe("WebSocket error: " + error.getMessage());
                        WebSocket.Listener.super.onError(webSocket, error);
                    }
                }).exceptionally(e -> {
                    getLogger().severe("Failed to connect to WebSocket relay: " + e.getMessage());
                    return null;
                });
    }

    @Override
    public void onDisable() {
        if (ws != null) ws.sendClose(WebSocket.NORMAL_CLOSURE, "Shutting down");
    }






    // EVENT HANDLING
    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (ws != null) {
            String json = "{ \"serverId\": \"" + serverId + "\", \"type\": \"chat\", \"sender\": \"minecraft\", \"player\": \"" + event.getPlayer().getName() + "\", \"message\": \"" + event.getMessage() + "\" }";
            ws.sendText(json, true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        getLogger().info("Join fired for " + event.getPlayer().getName());
        if (ws != null) {
            String json = "{ \"serverId\": \"" + serverId + "\", \"type\": \"mc_join\", \"player\": \"" + event.getPlayer().getName() + "\" }";
            ws.sendText(json, true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        getLogger().info("Quit fired for " + event.getPlayer().getName());
        if (ws != null) {
            String json = "{ \"serverId\": \"" + serverId + "\", \"type\": \"mc_quit\", \"player\": \"" + event.getPlayer().getName() + "\" }";
            ws.sendText(json, true);
        }
    }






    // COMMAND HANDLING
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("enderlink")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reconnect")) {
                if (ws != null) {
                    try {
                        ws.sendClose(WebSocket.NORMAL_CLOSURE, "Reconnecting");
                    } catch (Exception ignored) {}
                }
                connectWebSocket();
                sender.sendMessage("§a[EnderLink] Attempting to reconnect to WebSocket...");
                return true;
            } else if (args.length > 0 && args[0].equalsIgnoreCase("id")) {
                sender.sendMessage(messagesConfig.getString("settings-server-id").replace("{server_id}", serverId));
                sender.sendMessage(messagesConfig.getString("settings-room-id").replace("{room_id}", roomId));
                return true;
            } else if (args.length > 0 && args[0].equalsIgnoreCase("status")) {
                sender.sendMessage(messagesConfig.getString("settings-status").replace("{status}", (wsConnected ? "§aConnected" : "§cDisconnected")));
                return true;
            } else if (args.length == 0) {
                sender.sendMessage(messagesConfig.getString("settings-server-id").replace("{server_id}", serverId));
                sender.sendMessage(messagesConfig.getString("settings-room-id").replace("{room_id}", roomId));
                sender.sendMessage(messagesConfig.getString("settings-status").replace("{status}", (wsConnected ? "§aConnected" : "§cDisconnected")));
                sender.sendMessage(messagesConfig.getString("settings-reconnect"));
                return true;
            }
        }
        return false;
    }





    
    // CONFIG HANDLING
    public void saveDefaultMessagesConfig() {
        if (!new File(getDataFolder(), "messages.yml").exists()) {
            this.saveResource("messages.yml", false);
        }
    }






    // CHAT HANDLING
    private void sendChat(JSONObject json) {
        String sender = json.optString("sender", "");
        if (!"minecraft".equalsIgnoreCase(sender)) {
            String player = json.optString("player", "Unknown");
            String message = json.optString("message", "");
            
            message = messagesConfig.getString("mc-chat", "[{sender}] {player} > {message}")
                .replace("{sender}", sender)
                .replace("{player}", player)
                .replace("{message}", message);
            Bukkit.broadcastMessage(message);   
        }
    }

    private void sendChatReply(JSONObject json) {
        String sender = json.optString("sender", "");
        if (!"minecraft".equalsIgnoreCase(sender)) {
            String player = json.optString("player", "Unknown");
            String original = json.optString("original", "");
            String message = json.optString("message", "");
            
            message = messagesConfig.getString("mc-reply", "[{sender}] {player} > {message} (in reply to {reply_to})")
                .replace("{sender}", sender)
                .replace("{player}", player)
                .replace("{message}", message)
                .replace("{reply_to}", original);
            Bukkit.broadcastMessage(message);
        }
    }
}
