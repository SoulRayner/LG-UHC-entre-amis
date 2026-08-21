package com.lguhc.roles.villageois;

import com.lguhc.LGUHCPlugin;
import com.lguhc.game.GamePlayer;
import com.lguhc.game.RoleType;
import com.lguhc.roles.Role;
import com.lguhc.util.Msg;

/**
 * Idiot du Village : s'il est tué par un joueur qui n'appartient PAS au
 * camp des Loups-Garous, il réapparaît instantanément à pleine vie,
 * garde son inventaire, est téléporté aléatoirement dans la bordure
 * actuelle, et son rôle est révélé à tous (géré dans
 * DeathManager#debuterFenetreMort). Un Loup-Garou, lui, le tue "pour de bon".
 */
public class IdiotDuVillageRole implements Role {

    @Override
    public RoleType getType() {
        return RoleType.IDIOT_VILLAGE;
    }

    @Override
    public void onAssign(LGUHCPlugin plugin, GamePlayer gp) {
        Msg.envoyer(gp.getPlayer(), "&5Vous êtes l'&dIdiot du Village&5 ! Si un joueur qui n'est PAS un Loup-Garou vous tue, vous réapparaîtrez instantanément à pleine vie, avec votre inventaire, ailleurs sur la carte (et tout le monde saura que vous êtes l'Idiot). Seul un Loup-Garou peut vraiment vous tuer.");
    }
}
