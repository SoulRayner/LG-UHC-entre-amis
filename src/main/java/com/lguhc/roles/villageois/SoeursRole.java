package com.lguhc.roles.villageois;

import com.lguhc.LGUHCPlugin;
import com.lguhc.game.GamePlayer;
import com.lguhc.game.RoleType;
import com.lguhc.roles.Role;
import com.lguhc.util.Msg;

/**
 * Sœurs (rôle à 2 joueurs) : les deux Sœurs connaissent l'identité l'une de
 * l'autre et peuvent s'envoyer un message privé 2 fois par épisode.
 * L'appariement des deux joueuses (UUID "soeur_partenaire") est fait par
 * GameManager juste après la distribution des rôles.
 * Ability active : /lg soeur <message>, gérée dans LGCommand.
 */
public class SoeursRole implements Role {

    public static final int MESSAGES_MAX_PAR_EPISODE = 2;

    @Override
    public RoleType getType() {
        return RoleType.SOEURS;
    }

    @Override
    public void onAssign(LGUHCPlugin plugin, GamePlayer gp) {
        Msg.envoyer(gp.getPlayer(), "&5Vous êtes une des &dSœurs&5 ! Vous connaîtrez l'identité de l'autre Sœur sous peu. Utilisez &d/lg soeur <message> &5pour lui parler (2 fois par épisode).");
    }

    @Override
    public void onEpisodeStart(LGUHCPlugin plugin, GamePlayer gp, int episode) {
        gp.setEtat("soeur_messages_utilises", 0);
    }
}
