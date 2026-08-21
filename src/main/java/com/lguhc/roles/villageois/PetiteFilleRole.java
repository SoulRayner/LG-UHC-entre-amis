package com.lguhc.roles.villageois;

import com.lguhc.LGUHCPlugin;
import com.lguhc.game.GamePlayer;
import com.lguhc.game.RoleType;
import com.lguhc.roles.Role;
import com.lguhc.util.Msg;

/**
 * Petite Fille : peut retirer toute son armure pour obtenir Invisibilité +
 * Faiblesse I pendant 5 minutes, 1 fois par nuit (rompu si elle rééquipe
 * une pièce d'armure), via /lg espionner - gérée dans LGCommand, même
 * mécanique que le Loup-Garou Perfide. Voit aussi la direction d'un
 * hurlement de Loup-Garou pendant les 5 premières secondes.
 */
public class PetiteFilleRole implements Role {

    public static final int DUREE_INVISIBILITE_SECONDES = 300;

    @Override
    public RoleType getType() {
        return RoleType.PETITE_FILLE;
    }

    @Override
    public void onAssign(LGUHCPlugin plugin, GamePlayer gp) {
        Msg.envoyer(gp.getPlayer(), "&5Vous êtes la &dPetite Fille&5 ! Une fois par nuit, utilisez &d/lg espionner &5pour retirer votre armure et devenir invisible (mais affaiblie) 5 minutes. Vous sentirez aussi la direction des hurlements de Loups-Garous pendant leurs 5 premières secondes.");
    }
}
