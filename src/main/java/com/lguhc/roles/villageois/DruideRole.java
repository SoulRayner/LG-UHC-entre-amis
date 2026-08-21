package com.lguhc.roles.villageois;

import com.lguhc.LGUHCPlugin;
import com.lguhc.game.GamePlayer;
import com.lguhc.game.RoleType;
import com.lguhc.roles.Role;
import com.lguhc.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Druide : à partir de l'épisode 3, une fois par épisode, en étant accroupi
 * et en mangeant une pomme en or, sent le nombre d'auras Obscures dans un
 * rayon de 50 blocs. La consommation est interceptée dans AbilityListener
 * (PlayerItemConsumeEvent).
 */
public class DruideRole implements Role {

    public static final double RAYON = 50.0;
    public static final int EPISODE_MIN = 3;

    @Override
    public RoleType getType() {
        return RoleType.DRUIDE;
    }

    @Override
    public void onAssign(LGUHCPlugin plugin, GamePlayer gp) {
        Player joueur = gp.getPlayer();
        Msg.envoyer(joueur, "&5Vous êtes le &dDruide&5 ! A partir de l'épisode 3, accroupissez-vous et mangez une &6pomme en or&5 (une fois par épisode) pour sentir le nombre d'auras Obscures dans un rayon de 50 blocs.");
        if (joueur != null) {
            joueur.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, 2));
        }
    }

    @Override
    public void onEpisodeStart(LGUHCPlugin plugin, GamePlayer gp, int episode) {
        gp.setEtat("druide_utilise_ce_episode", false);
        if (episode == EPISODE_MIN && gp.getPlayer() != null) {
            Msg.envoyer(gp.getPlayer(), "&5Votre pouvoir de Druide est maintenant actif !");
        }
    }
}
