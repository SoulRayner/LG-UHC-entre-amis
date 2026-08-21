package com.lguhc.roles.villageois;

import com.lguhc.LGUHCPlugin;
import com.lguhc.game.GamePlayer;
import com.lguhc.game.RoleType;
import com.lguhc.roles.Role;
import com.lguhc.util.InventaireUtil;
import com.lguhc.util.Msg;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.Potion;
import org.bukkit.potion.PotionType;

/**
 * Sorcière : reçoit à l'attribution des rôles 2 potions de soin instantané I
 * et 2 potions de dégâts instantané I (en splash, pour pouvoir les jeter).
 * A la mort de chaque joueur qui n'a pas été infecté par l'Infect Père des
 * Loups, elle est prévenue par un message cliquable (8 secondes pour
 * décider) et peut, une seule fois dans la partie, ressusciter ce joueur
 * (sauf elle-même) via /lg ressusciter <joueur>.
 */
public class SorciereRole implements Role {

    public static final int NB_POTIONS_SOIN = 2;
    public static final int NB_POTIONS_DEGATS = 2;

    @Override
    public RoleType getType() {
        return RoleType.SORCIERE;
    }

    @Override
    public void onAssign(LGUHCPlugin plugin, GamePlayer gp) {
        gp.setEtat("sorciere_resurrection_dispo", true);
        Msg.envoyer(gp.getPlayer(), "&5Vous êtes la &dSorcière&5 ! A chaque mort (non infectée par les Loups), vous recevrez un message cliquable pour ressusciter ce joueur - vous avez 8 secondes pour décider, et une seule résurrection pour toute la partie (impossible sur vous-même). Vous recevez aussi 2 potions de soin et 2 potions de dégâts, à jeter sur qui vous voulez.");
        Player joueur = gp.getPlayer();
        if (joueur != null) {
            ItemStack potionsSoin = new Potion(PotionType.INSTANT_HEAL, 1).splash().toItemStack(NB_POTIONS_SOIN);
            ItemStack potionsDegats = new Potion(PotionType.INSTANT_DAMAGE, 1).splash().toItemStack(NB_POTIONS_DEGATS);
            InventaireUtil.donner(joueur, potionsSoin, potionsDegats);
        }
    }
}
