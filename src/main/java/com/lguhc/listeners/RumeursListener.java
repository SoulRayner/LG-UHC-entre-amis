package com.lguhc.listeners;

import com.lguhc.LGUHCPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Intercepte le chat général pendant la fenêtre de collecte de l'événement aléatoire "Rumeurs"
 * (voir GameManager#declencherRumeurs / #cloturerRumeurs). Tant que
 * GameManager#isCollecteRumeursActive() est vrai, le message d'un joueur INSCRIT À LA PARTIE en
 * cours n'est jamais diffusé tel quel dans le chat (event annulé, pseudo jamais associé au
 * message) : il est simplement mis de côté pour l'annonce anonyme et mélangée qui suit la fenêtre
 * de EvenementAleatoireManager.RUMEURS_DUREE_COLLECTE_SECONDES secondes.
 *
 * AsyncPlayerChatEvent s'exécute HORS du thread principal (comportement standard Bukkit, pour ne
 * pas bloquer le thread serveur le temps que tous les plugins traitent le message) : contrairement
 * au reste de ce listener qui ne fait que lire l'état courant, l'ENREGISTREMENT du message est
 * renvoyé sur le thread principal via Bukkit.getScheduler().runTask() avant de toucher à
 * GameManager, qui n'est pas thread-safe et ne doit être modifié que depuis ce thread (comme tout
 * le reste de l'état de partie dans ce plugin).
 */
public class RumeursListener implements Listener {

    private final LGUHCPlugin plugin;

    public RumeursListener(LGUHCPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void surChat(AsyncPlayerChatEvent event) {
        if (!plugin.getGameManager().isCollecteRumeursActive()) {
            return;
        }
        Player joueur = event.getPlayer();
        if (plugin.getGameManager().getGamePlayer(joueur) == null) {
            // Pas inscrit à la partie en cours (staff/spectateur externe) : laissé de côté, la
            // collecte ne concerne que les participants.
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage();
        Bukkit.getScheduler().runTask(plugin, () ->
                plugin.getGameManager().enregistrerMessageRumeur(joueur.getUniqueId(), message));
    }
}
