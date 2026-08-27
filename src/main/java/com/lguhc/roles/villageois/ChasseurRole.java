package com.lguhc.roles.villageois;

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
 * Chasseur : reçoit un LIVRE Power (niveau réglable via /lg config > Règle,
 * "Puissance (bonus Chasseur)" — III par défaut) ainsi que 64 flèches. Pas
 * d'arc fourni : à lui de trouver/crafter le sien. A sa mort, /lg tirer
 * <joueur> (pas son propre tueur) inflige 6 coeurs de dégâts.
 */
public class ChasseurRole implements Role {

    public static final double DEGATS_TIR_COEURS = 6.0;
    public static final int FLECHES_DEPART = 64;
    /** Valeur utilisée si "survie-uhc.chasseur-bonus-puissance" est absent du config.yml. */
    public static final int NIVEAU_PUISSANCE_DEFAUT = 3;

    @Override
    public RoleType getType() {
        return RoleType.CHASSEUR;
    }

    @Override
    public void onAssign(LGUHCPlugin plugin, GamePlayer gp) {
        Player joueur = gp.getPlayer();
        int niveauPuissance = plugin.getConfig().getInt("survie-uhc.chasseur-bonus-puissance", NIVEAU_PUISSANCE_DEFAUT);
        Msg.envoyer(joueur, "&5Vous êtes le &dChasseur&5 ! A votre mort, vous pourrez tirer sur un joueur (pas votre tueur) pour lui infliger 6 cœurs avec &d/lg tirer <joueur>&5.");
        if (joueur != null) {
            InventaireUtil.donner(joueur,
                    ItemBuilder.livreEnchante(Enchantment.ARROW_DAMAGE, niveauPuissance),
                    new ItemStack(Material.ARROW, FLECHES_DEPART));
        }
    }

    @Override
    public void onDeath(LGUHCPlugin plugin, GamePlayer gp) {
        plugin.getDeathManager().proposerTirChasseur(gp);
    }
}
