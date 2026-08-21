package com.lguhc.roles;

import com.lguhc.LGUHCPlugin;
import com.lguhc.game.GamePlayer;
import com.lguhc.game.RoleType;

/**
 * Comportement "passif" d'un rôle : attribution (kit, message d'annonce),
 * et éventuels crochets appelés par le moteur de jeu (début d'épisode, mort).
 *
 * Les pouvoirs "actifs" (commandes /lg ...) sont gérés directement dans
 * LGCommand + GameManager plutôt que par polymorphisme ici : les signatures
 * sont trop différentes d'un rôle à l'autre (0, 1 ou 2 cibles, etc.) pour
 * qu'une seule méthode générique reste lisible.
 */
public interface Role {

    RoleType getType();

    /** Appelé une fois, au moment où le rôle est distribué au joueur. */
    void onAssign(LGUHCPlugin plugin, GamePlayer gp);

    /** Appelé au début de chaque épisode (à partir de l'épisode d'attribution des rôles). */
    default void onEpisodeStart(LGUHCPlugin plugin, GamePlayer gp, int episode) {
    }

    /** Appelé quand ce joueur meurt, juste avant la diffusion du message de mort. */
    default void onDeath(LGUHCPlugin plugin, GamePlayer gp) {
    }

    /** Raccourci pratique vers le nom du rôle coloré (délègue à RoleType, qui porte les données d'affichage). */
    default String getNomFormate() {
        return getType().getNomFormate();
    }
}
