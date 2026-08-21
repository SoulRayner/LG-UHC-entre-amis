package com.lguhc.roles.loups;

import com.lguhc.LGUHCPlugin;
import com.lguhc.game.GamePlayer;
import com.lguhc.game.RoleType;
import com.lguhc.roles.Role;
import com.lguhc.util.Msg;
import org.bukkit.entity.Player;

/**
 * Loup-Garou Blanc : 15 coeurs de vie max en permanence (30 HP), doit en
 * réalité gagner seul même s'il apparaît comme un Loup normal.
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
        Msg.envoyer(joueur, "&cVous êtes le &4Loup-Garou Blanc&c ! Vous avez 15 coeurs de vie. En apparence un Loup comme les autres, mais vous devez gagner SEUL.");
        if (joueur != null) {
            joueur.setMaxHealth(VIE_MAX);
            joueur.setHealth(VIE_MAX);
        }
    }
}
