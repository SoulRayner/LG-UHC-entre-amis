package com.lguhc.roles.loups;

import com.lguhc.LGUHCPlugin;
import com.lguhc.game.GamePlayer;
import com.lguhc.game.RoleType;
import com.lguhc.roles.Role;
import com.lguhc.util.Msg;

/**
 * Loup-Garou : chaque nuit, vote avec les autres membres du camp des Loups
 * (/lg loup <joueur>) pour désigner la victime de la nuit. Le dépouillement
 * du vote et l'élimination sont gérés de façon centralisée dans GameManager
 * (le pack entier vote, quel que soit le Loup "spécial" de chacun).
 * Force I + Vision Nocturne la nuit + bonus au kill sont appliqués de façon
 * centralisée à tout le camp des Loups par GameManager#appliquerEffetsPeriodiques.
 */
public class LoupGarouRole implements Role {

    @Override
    public RoleType getType() {
        return RoleType.LOUP_GAROU;
    }

    @Override
    public void onAssign(LGUHCPlugin plugin, GamePlayer gp) {
        Msg.envoyer(gp.getPlayer(), "&cVous êtes un &4Loup-Garou&c ! Vous tuez en combat direct, la nuit venue (ou dès que le PvP est possible). Discutez avec vos alliés via &4/lg meute <message>&c, et hurlez une fois par partie avec &4/lg hurler&c.");
    }
}
