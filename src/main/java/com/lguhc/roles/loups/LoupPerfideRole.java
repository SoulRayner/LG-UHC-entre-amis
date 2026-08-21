package com.lguhc.roles.loups;

import com.lguhc.LGUHCPlugin;
import com.lguhc.game.GamePlayer;
import com.lguhc.game.RoleType;
import com.lguhc.roles.Role;
import com.lguhc.util.Msg;

/**
 * Loup-Garou Perfide : au lieu de la Force la nuit, peut retirer son armure
 * pour devenir invisible 5 minutes (une fois par nuit), avec un bonus de
 * vitesse tant qu'il est invisible. Ability active : /lg perfide, gérée
 * dans LGCommand (stocke armure + réapplique à la fin / si le joueur attaque).
 */
public class LoupPerfideRole implements Role {

    public static final int DUREE_INVISIBILITE_SECONDES = 300;

    @Override
    public RoleType getType() {
        return RoleType.LOUP_PERFIDE;
    }

    @Override
    public void onAssign(LGUHCPlugin plugin, GamePlayer gp) {
        gp.setEtat("perfide_utilise_cette_nuit", false);
        Msg.envoyer(gp.getPlayer(), "&cVous êtes le &4Loup-Garou Perfide&c ! Utilisez &4/lg perfide &cune fois par nuit pour retirer votre armure et devenir invisible (l'effet s'arrête si vous rééquipez une pièce d'armure ou si vous attaquez).");
    }
}
