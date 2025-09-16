package nl.frjosten.enderlink;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.UUID;

public class EnderLink extends JavaPlugin implements Listener {
    private WebSocket ws;
    public String serverId;
    public String websocketUrl;
    public String apiUrl;
    public String roomId;
    private boolean registered;

    private FileConfiguration messagesConfig;

    private Events eventsClass;
    private Commands commandsClass;
    private WS webSocketClass;
    private Logger logger = new Logger();

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
        webSocketClass = new WS(this);
        webSocketClass.connect();

        setupEvents();
        setupCommands();
    }

    @Override
    public void onDisable() {
        if (Bukkit.isStopping()) {
            new Thread(() -> {
                webSocketClass.msg("{ \\\"serverId\\\": \\\"\" + serverId + \"\\\", \\\"type\\\": \\\"mc_power\\\", \\\"event\\\": \\\"down\\\" }");
                webSocketClass.disconnect("Shutting down");
            }).start();
        }
    }





    
    // Setup functions
    private void setupEvents() {
        eventsClass = new Events(this, serverId);
        Bukkit.getPluginManager().registerEvents(eventsClass, this);
    }

    private void setupCommands() {
        // Register the /enderlink command using Bukkit's system
        if (getCommand("enderlink") != null) {
            commandsClass = new Commands(this, serverId, ws);
            getCommand("enderlink").setExecutor(commandsClass);
            getCommand("enderlink").setTabCompleter(commandsClass);
        } else {
            logger.warning("/enderlink command not found in plugin.yml");
        }
    }





    
    // Helper functions
    public WS getWebsocketClass() {
        return webSocketClass;
    }
    public Events getEventsClass() {
        return eventsClass;
    }
    public Commands getCommandsClass() {
        return commandsClass;
    }
    public Logger getLoggerClass() {
        return logger;
    }


    public FileConfiguration getMessagesConfig() {
        return messagesConfig;
    }


    public void scheduleTask(long delay, long period, Runnable task) {
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, task, delay, period);
    }
    public void runTask(Runnable task) {
        Bukkit.getScheduler().runTask(this, task);
    }


    public net.kyori.adventure.text.Component component(String text) {
        return net.kyori.adventure.text.Component.text(text);
    }





    
    // UUID Handling
    private void ensureServerIdAsync() {
        if (serverId == null || serverId.equals("UUID") || serverId.isEmpty()) {
            serverId = UUID.randomUUID().toString();
        } else {
            this.registered = true;
            getConfig().set("server-id", this.serverId);
            getConfig().set("room-id", this.roomId);
            logger.info("Using existing server UUID: " + serverId);
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
                            logger.info("Registered server UUID: " + serverId);
                            break;
                        } else {
                            logger.error("Failed to register server UUID: " + serverId + " Response: " + registerResponse.body());
                        }
                    } else {
                        logger.warning("Server UUID is already registered: " + serverId);
                    }

                    if (!this.registered) {
                        serverId = UUID.randomUUID().toString();
                        count++;
                        logger.info("Checking new server UUID: " + serverId);
                    }

                    if (count >= 5) {
                        logger.error("Failed to register a unique server UUID after 5 attempts. Stopping.");
                        break;
                    }
                } catch (Exception e) {
                    logger.error("Failed to check/register UUID: " + e.getMessage());
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
                        logger.error("Failed to save config.yml with server-id!");
                    }
                }
            });
        });
    }





    
    // Config handling
    public void saveDefaultMessagesConfig() {
        if (!new File(getDataFolder(), "messages.yml").exists()) {
            this.saveResource("messages.yml", false);
        }
    }
}
