package nl.frjosten.enderlink;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import net.kyori.adventure.text.Component;

public class WS {

    private WS instance = this;
    private final EnderLink plugin;
    private Logger logger;

    private FileConfiguration messagesConfig;

    private String serverId;
    private String roomId;
    private String websocketUrl;

    private WebSocket ws;
    private boolean connected = false;

    private long lastPongTime = -1;


    public WS(EnderLink plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLoggerClass();
    }





    
    // Connection handling
    public void connect() {
        websocketUrl    = plugin.websocketUrl;
        roomId          = plugin.roomId;
        serverId        = plugin.serverId;

        messagesConfig  = plugin.getMessagesConfig();

        HttpClient client = HttpClient.newHttpClient();
        client.newWebSocketBuilder().buildAsync(URI.create(websocketUrl), new WebSocket.Listener() {
            @Override
            public void onOpen(WebSocket webSocket) {
                logger.info("Connected to WebSocket relay as " + roomId);
                ws = webSocket;

                // Send register message
                msg("{\"type\":\"register\",\"roomId\":\"" + roomId + "\"}");
                connected = true;

                // Ping every minute
                plugin.scheduleTask(0L, 1200L, minutePingTask());
                plugin.scheduleTask(0L, 6000L, disconnectedTask());

                WebSocket.Listener.super.onOpen(webSocket);
            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                String msg = data.toString();
                connected = true;
                lastPongTime = System.currentTimeMillis();

                plugin.getEventsClass().wsOnMessage(msg);

                return WebSocket.Listener.super.onText(webSocket, data, last);
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                logger.error("WebSocket error: " + error.getMessage());
                WebSocket.Listener.super.onError(webSocket, error);
            }
        }).exceptionally(e -> {
            logger.error("Failed to connect to WebSocket relay: " + e.getMessage());
            return null;
        });
    }

    public void disconnect(String reason) {
        if (ws != null) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, reason);
            } catch (Exception e) {
                logger.error("Error during WebSocket disconnect: " + e.getMessage());
            } finally {
                ws = null;
                connected = false;
            }
        }
    }

    public void reconnect() {
        disconnect("Reconnecting");
        connect();
    }





    
    // Helper functions
    public void msg(String msg) {
        if (ws != null) {
            ws.sendText(msg, true);
        }
    }

    public boolean connected() {
        return connected;
    }





    
    // Task functions
    public Runnable minutePingTask() {
        return () -> {
            if (ws != null) msg("{\"type\":\"ping\"}");

            // Reconnect if no pong in 2 minutes
            if (lastPongTime == 0 || (System.currentTimeMillis() - lastPongTime) > 120000) {
                connected = false;
                logger.warning("WebSocket pong timeout, attempting to reconnect...");
                try {
                    disconnect("Pong timeout");
                } catch (Exception ignored) {}
                connect();


                plugin.runTask(() -> {
                    Bukkit.getOnlinePlayers().stream()
                            .filter(p -> p.isOp())
                            .forEach(p -> p.sendMessage(messagesConfig.getString("error-op-disconnect")));
                });
            }
        };
    }

    public Runnable disconnectedTask() {
        return () -> {
            if (!connected()) {
                String errorMsg = messagesConfig.getString("error-disconnect");
                Component componentMsg = Component.text(errorMsg);
                Bukkit.getServer().broadcast(componentMsg);
            }
        };
    }
}
