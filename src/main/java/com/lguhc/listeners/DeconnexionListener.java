package com.lguhc.listeners;

import com.lguhc.LGUHCPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Gère le délai de grâce de 2 minutes accordé à un joueur qui se déconnecte en pleine partie
 * (crash ou déconnexion volontaire, impossible à distinguer côté serveur) avant qu'il ne soit
 * éliminé de la partie. La logique elle-même (échéance, vérification périodique, élimination)
 * vit dans GameManager#surDeconnexion / #surReconnexion / #tickDeconnexions ; ce listener ne
 * fait que relayer les deux événements Bukkit.
 */
public class DeconnexionListener implements Listener {

    private final LGUHCPlugin plugin;

    public DeconnexionListener(LGUHCPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.getGameManager().surDeconnexion(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.getGameManager().surReconnexion(event.getPlayer());
    }
}
