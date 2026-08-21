package com.lguhc.roles.villageois;

import com.lguhc.LGUHCPlugin;
import com.lguhc.game.GamePlayer;
import com.lguhc.game.RoleType;
import com.lguhc.roles.Role;
import com.lguhc.util.Msg;

/**
 * Renard : la nuit uniquement, 1 fois par nuit, 3 fois max dans la partie,
 * peut flairer un joueur avec /lg flairer <joueur>. Il doit rester à moins
 * de 15 blocs de sa cible quelques secondes pour que ça fonctionne. Une
 * fois les 3 flairages faits, il apprend combien étaient des Loups-Garous.
 * Bénéficie aussi d'un bonus de vitesse de déplacement constant (pas
 * aléatoire) pendant toutes les phases de nuit, équivalent à la moitié
 * du bonus donné par l'effet Vitesse I (+10% au lieu de +20%), appliqué
 * directement via Player#setWalkSpeed (un potion effect ne permet pas de
 * demi-niveau).
 */
public class RenardRole implements Role {

    public static final int UTILISATIONS_MAX = 3;
    public static final double RAYON = 15.0;
    public static final int SECONDES_A_RESTER_PROCHE = 5;

    /** Vitesse de marche par défaut d'un joueur (référence Bukkit). */
    public static final float VITESSE_MARCHE_NORMALE = 0.2f;
    /** +10% par rapport à la normale, soit la moitié du bonus de Vitesse I (+20%). */
    public static final float VITESSE_MARCHE_RENARD_NUIT = 0.22f;

    @Override
    public RoleType getType() {
        return RoleType.RENARD;
    }

    @Override
    public void onAssign(LGUHCPlugin plugin, GamePlayer gp) {
        gp.setEtat("renard_utilisations", 0);
        gp.setEtat("renard_loups_trouves", 0);
        Msg.envoyer(gp.getPlayer(), "&5Vous êtes le &dRenard&5 ! Vous courez légèrement plus vite (+10%) pendant toutes les phases de nuit. La nuit, une fois par nuit (3 fois max dans la partie), utilisez &d/lg flairer <joueur>&5 et restez à moins de 15 blocs quelques secondes. Une fois vos 3 flairages faits, vous saurez combien étaient des Loups-Garous.");
    }
}
