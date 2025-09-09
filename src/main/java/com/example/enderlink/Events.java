package com.example.enderlink;

import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.event.Listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import java.net.http.WebSocket;

public class Events implements Listener {
    private final EnderLink plugin;
    private final String serverId;

    public Events(EnderLink plugin, String serverId) {
        this.plugin = plugin;
        this.serverId = serverId;
    }


    // EVENT HANDLING
    @EventHandler
    public void onChat(AsyncChatEvent  event) {
        String playerName = event.getPlayer().getName();
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());

        wsSend("{ \"serverId\": \"" + serverId + "\", \"type\": \"chat\", \"sender\": \"minecraft\", \"player\": \"" + playerName + "\", \"message\": \"" + message + "\" }");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        wsSend("{ \"serverId\": \"" + serverId + "\", \"type\": \"mc_join\", \"player\": \"" + event.getPlayer().getName() + "\" }");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        wsSend("{ \"serverId\": \"" + serverId + "\", \"type\": \"mc_quit\", \"player\": \"" + event.getPlayer().getName() + "\" }");
    }

    @EventHandler
    public void onDead(PlayerDeathEvent event) {
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
        if (event.getType() == ServerLoadEvent.LoadType.STARTUP && this.ws != null) {
            wsSend("{ \"serverId\": \"" + serverId + "\", \"type\": \"mc_power\", \"event\": \"up\" }");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                plugin.sendInfo("Shutdown event triggered");
                wsSend("{ \"serverId\": \"" + serverId + "\", \"type\": \"mc_power\", \"event\": \"down\" }");
            }));
        }
    }


    private void wsSend(String msg) {
        if (plugin.getWs() != null) {
            plugin.getWs().sendText(msg, true);
        }
    }
}
