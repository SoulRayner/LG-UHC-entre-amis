package com.lguhc.roles.villageois;

import com.lguhc.LGUHCPlugin;
import com.lguhc.game.GamePlayer;
import com.lguhc.game.RoleType;
import com.lguhc.roles.Role;
import com.lguhc.util.InventaireUtil;
import com.lguhc.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Voyante : une fois par épisode, /lg voir <joueur> révèle deux rôles
 * possibles pour ce joueur (son vrai rôle + un leurre pioché dans la
 * composition, avec au moins un rôle du Village parmi les deux affichés).
 */
public class VoyanteRole implements Role {

    @Override
    public RoleType getType() {
        return RoleType.VOYANTE;
    }

    @Override
    public void onAssign(LGUHCPlugin plugin, GamePlayer gp) {
        Player joueur = gp.getPlayer();
        Msg.envoyer(joueur, "&5Vous êtes la &dVoyante&5 ! Une fois par épisode, utilisez &d/lg voir <joueur> &5pour découvrir 2 rôles possibles pour lui (dont son vrai rôle, mêlé à un leurre).");
        if (joueur != null) {
            InventaireUtil.donner(joueur, new ItemStack(Material.OBSIDIAN, 4), new ItemStack(Material.BOOKSHELF, 4));
        }
    }
}
