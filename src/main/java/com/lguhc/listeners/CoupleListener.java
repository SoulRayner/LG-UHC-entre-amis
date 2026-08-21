package com.lguhc.listeners;

import com.lguhc.LGUHCPlugin;
import com.lguhc.game.GamePlayer;
import com.lguhc.util.Msg;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * Intercepte la commande dynamique /don[Nombre] (ex : /don40 pour transférer 4 cœurs),
 * réservée aux 2 membres du Couple. Le nombre étant variable, cette commande ne peut pas
 * être déclarée telle quelle dans plugin.yml : on la détecte donc ici, avant que le serveur
 * ne la traite comme une commande inconnue, exactement comme /don<chiffres> quel que soit
 * le nombre de chiffres.
 */
public class CoupleListener implements Listener {

    private final LGUHCPlugin plugin;

    public CoupleListener(LGUHCPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void surCommandeDon(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message.length() < 2) {
            return;
        }
        // On isole le premier mot après le "/" (ignore d'éventuels arguments superflus).
        String premierMot = message.substring(1).split(" ", 2)[0];
        if (!premierMot.toLowerCase().matches("don\\d+")) {
            return;
        }
        event.setCancelled(true);

        Player joueur = event.getPlayer();
        GamePlayer gp = plugin.getGameManager().getGamePlayer(joueur);
        if (gp == null || !gp.estEnCouple()) {
            Msg.envoyer(joueur, "&cCette commande est réservée aux membres du Couple.");
            return;
        }

        int nombre;
        try {
            nombre = Integer.parseInt(premierMot.substring(3));
        } catch (NumberFormatException e) {
            return;
        }
        plugin.getCoupleManager().donnerVie(plugin.getGameManager(), gp, nombre);
    }
}
