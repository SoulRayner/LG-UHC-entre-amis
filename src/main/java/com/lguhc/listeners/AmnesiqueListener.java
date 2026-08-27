package com.lguhc.listeners;

import com.lguhc.LGUHCPlugin;
import com.lguhc.game.GamePlayer;
import com.lguhc.game.RoleType;
import com.lguhc.util.Msg;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Bonus du Loup-Garou Amnésique : Vitesse + 2♥ d'Absorption pendant 1 minute à chaque fois qu'il
 * tue un joueur. Détecté via PlayerDeathEvent#getKiller() (dernier coup direct porté par un
 * joueur), le même événement réel que celui utilisé par le reste du plugin pour déclencher
 * GameManager#surMortReelle() (voir son javadoc) : ce listener tourne donc en parallèle de celui
 * qui gère la fenêtre de résurrection, sans interférer avec elle.
 *
 * Ne se déclenche que sur une élimination directe (combat/tir) : une victime qui meurt de chute,
 * de faim ou par une commande admin (getKiller() == null) ne donne pas le bonus, même si
 * l'Amnésique en est indirectement responsable.
 */
public class AmnesiqueListener implements Listener {

    /** 1 minute, comme demandé. */
    private static final int DUREE_BONUS_TICKS = 20 * 60;
    /** Amplificateur 0 = 4 PV d'Absorption = 2 coeurs. */
    private static final int NIVEAU_ABSORPTION_2_COEURS = 0;

    private final LGUHCPlugin plugin;

    public AmnesiqueListener(LGUHCPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void surMortJoueur(PlayerDeathEvent event) {
        if (!plugin.getGameManager().estEnCours()) {
            return;
        }
        Player victime = event.getEntity();
        Player tueur = victime.getKiller();
        if (tueur == null || tueur.equals(victime)) {
            return;
        }
        GamePlayer gpTueur = plugin.getGameManager().getGamePlayer(tueur);
        if (gpTueur == null || gpTueur.getRole() != RoleType.LOUP_GAROU_AMNESIQUE) {
            return;
        }
        tueur.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, DUREE_BONUS_TICKS, 0, false, false));
        tueur.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, DUREE_BONUS_TICKS, NIVEAU_ABSORPTION_2_COEURS, false, false));
        Msg.envoyer(tueur, "&4🐺 Un frisson de puissance vous parcourt après cette mise à mort !");
    }
}
