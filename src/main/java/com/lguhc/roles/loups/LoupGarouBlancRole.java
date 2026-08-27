package com.lguhc.roles.loups;

import com.lguhc.LGUHCPlugin;
import com.lguhc.game.GamePlayer;
import com.lguhc.game.RoleType;
import com.lguhc.roles.Role;
import com.lguhc.util.Msg;
import org.bukkit.entity.Player;

/**
 * Loup-Garou Blanc : 15 coeurs de vie max en permanence (30 HP). Membre à part
 * entière du camp des Loups pour TOUTES les mécaniques de jeu (chat de meute,
 * vision de nuit, liste des alliés, bonus de Force, éliminations...) - il a
 * EXACTEMENT les mêmes pouvoirs qu'un Loup-Garou normal, aucune commande
 * spéciale ni capacité en plus. Mais il ne partage PAS leur victoire
 * collective : il ne gagne que s'il finit unique survivant de la partie (voir
 * GameManager#verifierVictoire, qui l'exclut explicitement du calcul de
 * victoire "classique" des Loups pour ça). Concrètement, le moment venu, il
 * devra éliminer lui-même ses anciens alliés Loups au corps-à-corps - comme
 * n'importe quel autre affrontement en UHC, sans mécanique dédiée - pour
 * rester seul en vie.
 */
public class LoupGarouBlancRole implements Role {

    public static final double VIE_MAX = 30.0; // 15 coeurs

    @Override
    public RoleType getType() {
        return RoleType.LOUP_GAROU_BLANC;
    }

    @Override
    public void onAssign(LGUHCPlugin plugin, GamePlayer gp) {
        Player joueur = gp.getPlayer();
        Msg.envoyer(joueur, "&cVous êtes le &4Loup-Garou Blanc&c ! Vous avez 15 coeurs de vie.");
        Msg.envoyer(joueur, "&cAux yeux de tous (y compris de la meute), vous êtes un Loup-Garou normal : chat de meute, vision de nuit, mêmes pouvoirs, tout y est. Aucune commande à part.");
        Msg.envoyer(joueur, "&cMais vous ne gagnez PAS avec eux : vous devez être l'unique survivant de la partie pour l'emporter, seul.");
        Msg.envoyer(joueur, "&4&lLe moment venu, &cvous devrez éliminer vous-même vos anciens alliés Loups pour rester seul en vie.");
        if (joueur != null) {
            joueur.setMaxHealth(VIE_MAX);
            joueur.setHealth(VIE_MAX);
        }
    }
}
