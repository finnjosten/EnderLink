package com.example.chatbridge;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
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

public class ChatBridge extends JavaPlugin implements Listener {
    private WebSocket ws;
    private String serverId;
    private String websocketUrl;
    private String apiUrl;
    private String roomId;
    private boolean registered = false;
    private boolean wsConnected = false;
    private long lastPongTime = 0;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();

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

        final String initialId = serverId;

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
                    getConfig().set("room-id", serverId);
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

                        // Ping every minute
                        Bukkit.getScheduler().runTaskTimerAsynchronously(ChatBridge.this, () -> {
                            if (ws != null) ws.sendText("{\"type\":\"ping\"}", true);

                            // Check pong timeout
                            long now = System.currentTimeMillis();
                            if (lastPongTime == 0 || now - lastPongTime > 10000) {
                                wsConnected = false;

                                // Alert OPs every 10s
                                Bukkit.getScheduler().runTask(ChatBridge.this, () ->
                                        Bukkit.getOnlinePlayers().stream()
                                                .filter(p -> p.isOp())
                                                .forEach(p -> p.sendMessage("§c[ChatBridge] WebSocket disconnected! Run /chatbridge reconnect"))
                                );
                            }
                        }, 0L, 1200L);

                        // Global alert every 5 minutes
                        Bukkit.getScheduler().runTaskTimer(ChatBridge.this, () -> {
                            if (!wsConnected) {
                                Bukkit.broadcastMessage("§c[ChatBridge] WebSocket is disconnected! Please report this to an admin.");
                            }
                        }, 0L, 6000L);

                        WebSocket.Listener.super.onOpen(webSocket);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        String msg = data.toString();

                        Bukkit.getScheduler().runTask(ChatBridge.this, () -> {
                            try {
                                JSONObject json = new JSONObject(msg);
                                String type = json.optString("type", "");

                                if ("registered".equalsIgnoreCase(type)) {
                                    registeredWs = true;
                                    wsConnected = true;
                                    lastPongTime = System.currentTimeMillis();
                                    getLogger().info("WebSocket registration successful for server: " + serverId);
                                } else if ("pong".equalsIgnoreCase(type)) {
                                    wsConnected = true;
                                    lastPongTime = System.currentTimeMillis();
                                } else if ("chat".equalsIgnoreCase(type) || "message".equalsIgnoreCase(type)) {
                                    String sender = json.optString("sender", "");
                                    if ("discord".equalsIgnoreCase(sender)) {
                                        String player = json.optString("player", "Unknown");
                                        String message = json.optString("message", "");
                                        Bukkit.broadcastMessage("§9[Discord] §f" + player + " > " + message);
                                    }
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

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (ws != null) {
            String json = "{ \"serverId\": \"" + serverId + "\", \"type\": \"chat\", \"sender\": \"minecraft\", \"player\": \"" +
                    event.getPlayer().getName() + "\", \"message\": \"" + event.getMessage() + "\" }";
            ws.sendText(json, true);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("chatbridge")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reconnect")) {
                if (ws != null) {
                    try {
                        ws.sendClose(WebSocket.NORMAL_CLOSURE, "Reconnecting");
                    } catch (Exception ignored) {}
                }
                connectWebSocket();
                sender.sendMessage("§a[ChatBridge] Attempting to reconnect to WebSocket...");
                return true;
            }
        }
        return false;
    }
}
