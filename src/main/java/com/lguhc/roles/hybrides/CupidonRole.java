package com.lguhc.roles.hybrides;

import com.lguhc.LGUHCPlugin;
import com.lguhc.game.GamePlayer;
import com.lguhc.game.RoleType;
import com.lguhc.roles.Role;
import com.lguhc.util.InventaireUtil;
import com.lguhc.util.ItemBuilder;
import com.lguhc.util.Msg;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Cupidon : au début de l'épisode 2, forme un couple entre deux joueurs de
 * son choix (pas lui-même) via /lg couple <joueur1> <joueur2>. S'il n'a
 * rien fait à 45 minutes de jeu, un couple aléatoire est formé et lui est
 * révélé. Doit gagner en priorité avec le couple ; si celui-ci meurt, il
 * doit gagner avec le Village et perd son enchant Punch I s'il l'a posé.
 * Reçoit uniquement un LIVRE Punch I (pas d'équipement de base fourni : à lui
 * de trouver/crafter son propre arc pour y appliquer l'enchant).
 */
public class CupidonRole implements Role {

    public static final int MINUTES_AVANT_COUPLE_ALEATOIRE = 45;

    @Override
    public RoleType getType() {
        return RoleType.CUPIDON;
    }

    @Override
    public void onAssign(LGUHCPlugin plugin, GamePlayer gp) {
        gp.setEtat("cupidon_pouvoir_utilise", false);
        Player joueur = gp.getPlayer();
        Msg.envoyer(joueur, "&5Vous êtes &dCupidon&5 ! Dès le début de cet épisode, utilisez &d/lg couple <joueur1> <joueur2> &5pour former un couple (sans vous inclure). Si vous n'avez rien fait à 45 minutes de jeu, un couple aléatoire sera formé et vous sera révélé. Si l'un meurt, l'autre meurt de chagrin ; s'ils sont les 2 derniers vivants, ils gagnent ensemble.");
        if (joueur != null) {
            InventaireUtil.donner(joueur,
                    ItemBuilder.livreEnchante(Enchantment.ARROW_KNOCKBACK, 1),
                    new ItemStack(Material.ARROW, 64));
        }
    }
}
