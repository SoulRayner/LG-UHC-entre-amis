package com.lguhc.listeners;

import com.lguhc.LGUHCPlugin;
import com.lguhc.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.UUID;

/**
 * Capture le "prochain message" d'un membre du staff après un clic sur [Répondre] dans un
 * /helpop (ou après un "/helpop reply <joueur>" tapé à la main) et le transforme en message
 * privé vers le joueur concerné, au lieu de l'envoyer dans le chat public.
 *
 * Priorité LOWEST : on veut intercepter avant que d'autres plugins de chat (formatteurs, filtres
 * anti-spam...) ne touchent à l'événement, pour être sûr de capter le message tel quel et de ne
 * jamais le laisser fuiter dans le chat public même si un autre plugin l'annule entre-temps.
 * AsyncPlayerChatEvent est déclenché hors thread principal (comme dans toute version Bukkit) :
 * on se contente ici d'envoyer des messages, ce qui est sûr même hors du thread principal.
 */
public class HelpOpListener implements Listener {

    private final LGUHCPlugin plugin;

    public HelpOpListener(LGUHCPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player staff = event.getPlayer();
        UUID cibleId = plugin.getHelpOpManager().getCibleReponse(staff);
        if (cibleId == null) {
            return;
        }

        // On intercepte quoi qu'il arrive dès qu'un staff en attente de réponse écrit quelque
        // chose : ce message ne doit jamais partir dans le chat public par erreur, même si le
        // joueur ciblé s'est déconnecté entre-temps (cas géré juste en dessous).
        event.setCancelled(true);
        plugin.getHelpOpManager().retirerCibleReponse(staff);

        Player cible = Bukkit.getPlayer(cibleId);
        if (cible == null || !cible.isOnline()) {
            Msg.envoyer(staff, "&c[HelpOp] Ce joueur n'est plus en ligne, message annulé.");
            return;
        }

        String message = event.getMessage();
        Msg.envoyer(cible, "&d&l[HELP-OP] &fHOST &7: &f" + message);
        Msg.envoyer(staff, "&7[HelpOp] &fVous → " + cible.getName() + " &7: &f" + message);
    }
}
