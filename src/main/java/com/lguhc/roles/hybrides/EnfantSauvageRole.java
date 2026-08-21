package com.lguhc.roles.hybrides;

import com.lguhc.LGUHCPlugin;
import com.lguhc.game.GamePlayer;
import com.lguhc.game.RoleType;
import com.lguhc.roles.Role;
import com.lguhc.util.Msg;

/**
 * Enfant Sauvage : au début de l'épisode 2, choisit un modèle via
 * /lg choisir <joueur>. Commence dans le camp Village ; si son modèle
 * meurt, se transforme aussitôt en Loup-Garou (mais reste affiché comme
 * Village au scoreboard). Après la transformation, reçoit la distance
 * jusqu'à chaque hurlement de Loup-Garou.
 */
public class EnfantSauvageRole implements Role {

    @Override
    public RoleType getType() {
        return RoleType.ENFANT_SAUVAGE;
    }

    @Override
    public void onAssign(LGUHCPlugin plugin, GamePlayer gp) {
        gp.setEtat("sauvage_modele_choisi", false);
        gp.setEtat("sauvage_transforme", false);
        Msg.envoyer(gp.getPlayer(), "&5Vous êtes l'&dEnfant Sauvage&5 ! Dès le début de cet épisode, choisissez un modèle avec &d/lg choisir <joueur>&5. Vous êtes du Village pour l'instant... mais si votre modèle meurt, vous deviendrez Loup-Garou et sentirez la distance de chaque hurlement !");
    }
}
