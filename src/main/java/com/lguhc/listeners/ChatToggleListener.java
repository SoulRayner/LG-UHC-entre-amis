package com.lguhc.listeners;

import com.lguhc.LGUHCPlugin;
import com.lguhc.util.Msg;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Coupe le chat général du serveur tant qu'il est désactivé (voir GameManager#isChatDesactive,
 * désactivé PAR DÉFAUT au démarrage du plugin ; un hôte le réactive avec /lg admin chat, qui
 * permet aussi de le recouper ensuite). Priorité LOWEST : on veut intercepter AVANT tout autre listener
 * de chat (ex : RumeursListener, qui annule aussi le chat pendant sa fenêtre de collecte), pour
 * qu'un chat coupé le reste même si un événement de jeu tente de s'en servir au même moment.
 * Les joueurs ayant la permission lguhc.host (les hôtes/admins) continuent de pouvoir parler,
 * pour pouvoir donner des consignes pendant que le chat est coupé pour tout le monde.
 */
public class ChatToggleListener implements Listener {

    private final LGUHCPlugin plugin;

    public ChatToggleListener(LGUHCPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void surChat(AsyncPlayerChatEvent event) {
        if (!plugin.getGameManager().isChatDesactive()) {
            return;
        }
        if (event.getPlayer().hasPermission("lguhc.host")) {
            return;
        }
        event.setCancelled(true);
        Msg.envoyer(event.getPlayer(), "&cLe chat est désactivé.");
    }
}
