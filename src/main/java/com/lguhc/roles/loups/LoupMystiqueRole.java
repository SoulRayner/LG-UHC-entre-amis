package com.lguhc.roles.loups;

import com.lguhc.LGUHCPlugin;
import com.lguhc.game.GamePlayer;
import com.lguhc.game.RoleType;
import com.lguhc.roles.Role;
import com.lguhc.util.Msg;

/**
 * Loup-Garou Mystique : quand un membre du camp des Loups meurt, reçoit le
 * nom et le rôle d'un joueur tiré au sort parmi les vivants d'un autre camp.
 * Déclenché depuis DeathManager après chaque mort.
 */
public class LoupMystiqueRole implements Role {

    @Override
    public RoleType getType() {
        return RoleType.LOUP_MYSTIQUE;
    }

    @Override
    public void onAssign(LGUHCPlugin plugin, GamePlayer gp) {
        Msg.envoyer(gp.getPlayer(), "&cVous êtes le &4Loup-Garou Mystique&c ! A chaque fois qu'un membre de votre camp meurt, vous recevrez le nom et le rôle d'un joueur d'un autre camp.");
    }
}
