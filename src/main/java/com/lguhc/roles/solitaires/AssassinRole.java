package com.lguhc.roles.solitaires;

import com.lguhc.LGUHCPlugin;
import com.lguhc.game.GamePlayer;
import com.lguhc.game.RoleType;
import com.lguhc.roles.Role;
import com.lguhc.util.InventaireUtil;
import com.lguhc.util.ItemBuilder;
import com.lguhc.util.Msg;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;

/**
 * Assassin : doit être l'unique survivant, en éliminant aussi bien les
 * Villageois que les Loups-Garous. Reçoit uniquement 3 livres enchantés
 * (Tranchant III, Protection III, Puissance III) - pas d'équipement de
 * base fourni : à lui de trouver/crafter épée, armure et arc pour y
 * appliquer les enchants. Bénéficie de Force le JOUR (à l'inverse des
 * Loups qui l'ont la nuit), appliqué de façon centralisée par
 * GameManager#appliquerEffetsPeriodiques.
 */
public class AssassinRole implements Role {

    @Override
    public RoleType getType() {
        return RoleType.ASSASSIN;
    }

    @Override
    public void onAssign(LGUHCPlugin plugin, GamePlayer gp) {
        Player joueur = gp.getPlayer();
        Msg.envoyer(joueur, "&6Vous êtes l'&eAssassin&6 ! Vous devez être l'unique survivant, Village et Loups-Garous confondus. Vous bénéficiez de Force le JOUR (quand les Loups, eux, en sont privés). Faites profil bas au début, frappez ensuite.");
        if (joueur != null) {
            InventaireUtil.donner(joueur,
                    ItemBuilder.livreEnchante(Enchantment.DAMAGE_ALL, 3),
                    ItemBuilder.livreEnchante(Enchantment.PROTECTION_ENVIRONMENTAL, 3),
                    ItemBuilder.livreEnchante(Enchantment.ARROW_DAMAGE, 3));
        }
    }
}
