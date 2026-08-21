package com.lguhc.roles.loups;

import com.lguhc.LGUHCPlugin;
import com.lguhc.game.GamePlayer;
import com.lguhc.game.RoleType;
import com.lguhc.roles.Role;
import com.lguhc.util.InventaireUtil;
import com.lguhc.util.Msg;

/**
 * Infect Père des Loups : fait partie du vote de meute comme les autres
 * Loups. Il dispose d'un unique pouvoir de résurrection pour toute la
 * partie (message cliquable, 8 secondes), déclenché dès qu'une victime
 * meurt d'un membre du camp des Loups-Garous - aucune proximité ni
 * corruption préalable n'est nécessaire. 3 cas possibles (gérés dans
 * DeathManager + LGCommand) :
 *  - un joueur qui n'est pas déjà Loup meurt sous les crocs d'un membre du
 *    camp des Loups -> il devient Loup-Garou en gardant ses pouvoirs ;
 *  - un membre des Loups-Garous meurt sous les crocs d'un autre Loup -> il
 *    est simplement ressuscité, sans changement de camp ;
 *  - l'Infect Père des Loups lui-même meurt sous les crocs d'un autre Loup
 *    -> il peut se ressusciter lui-même.
 * Ce message arrive toujours AVANT celui de la Sorcière : s'il ne clique
 * pas dans les 8 secondes, la Sorcière est alors prévenue à son tour.
 */
public class InfectPereDesLoupsRole implements Role {

    public static final double GAIN_PROCHE_PAR_TICK = 0.2; // ≈1%/5s (tick toutes les secondes)
    public static final double GAIN_MEUTE_PAR_TICK = 0.05; // ≈1%/20s
    public static final double RAYON_CORRUPTION = 15.0;

    @Override
    public RoleType getType() {
        return RoleType.INFECT_PERE_LOUPS;
    }

    @Override
    public void onAssign(LGUHCPlugin plugin, GamePlayer gp) {
        gp.setEtat("infect_pouvoir_utilise", false);
        Msg.envoyer(gp.getPlayer(), "&cVous êtes l'&4Infect Père des Loups&c ! Si un joueur meurt sous les crocs d'un membre du camp des Loups-Garous, vous pourrez le ressusciter en Loup-Garou.");

        // Potions de départ : soin instantané I (se soigner ou soigner un allié) et dégâts
        // instantané I (offensif). En versions à projeter (splash) : boire directement une
        // potion de Dégâts se ferait mal à soi-même, elle doit donc être jetée.
        org.bukkit.potion.Potion soin = new org.bukkit.potion.Potion(org.bukkit.potion.PotionType.INSTANT_HEAL, 1);
        soin.setSplash(true);
        org.bukkit.potion.Potion degats = new org.bukkit.potion.Potion(org.bukkit.potion.PotionType.INSTANT_DAMAGE, 1);
        degats.setSplash(true);
        InventaireUtil.donner(gp.getPlayer(), soin.toItemStack(2), degats.toItemStack(2));
    }
}
