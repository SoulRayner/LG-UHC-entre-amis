package com.lguhc.roles.villageois;

import com.lguhc.LGUHCPlugin;
import com.lguhc.game.GamePlayer;
import com.lguhc.game.RoleType;
import com.lguhc.roles.Role;
import com.lguhc.util.Msg;

/**
 * Ancien : Resistance 0.5 en permanence (approximée par 50% de chance de
 * Resistance I à chaque rafraîchissement d'effets). S'il est tué par un
 * Loup-Garou, il ressuscite instantanément (1 seule fois) à pleine vie,
 * garde son inventaire, est téléporté aléatoirement dans la bordure
 * actuelle, et perd sa Resistance. S'il est tué par un non-Loup, il ne
 * ressuscite pas et son tueur perd tous ses effets + la moitié de sa vie
 * (géré dans DeathManager#debuterFenetreMort).
 */
public class AncienRole implements Role {

    @Override
    public RoleType getType() {
        return RoleType.ANCIEN;
    }

    @Override
    public void onAssign(LGUHCPlugin plugin, GamePlayer gp) {
        gp.setEtat("ancien_peut_ressusciter", true);
        Msg.envoyer(gp.getPlayer(), "&5Vous êtes l'&dAncien&5 ! Vous bénéficiez d'une Résistance quasi permanente. Si un Loup-Garou vous tue, vous ressusciterez instantanément une première fois, à pleine vie et avec votre inventaire, ailleurs sur la carte (en perdant cette résistance). Si c'est un non-Loup qui vous tue, vous ne reviendrez pas... mais votre tueur perdra tous ses effets et la moitié de sa vie.");
    }
}
