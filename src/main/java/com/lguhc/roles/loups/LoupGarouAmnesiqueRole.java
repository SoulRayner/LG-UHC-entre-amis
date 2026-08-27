package com.lguhc.roles.loups;

import com.lguhc.LGUHCPlugin;
import com.lguhc.game.GamePlayer;
import com.lguhc.game.RoleType;
import com.lguhc.roles.Role;
import com.lguhc.util.Msg;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Loup-Garou Amnésique : un Loup-Garou normal en tout point pour les mécaniques de jeu (camp,
 * combat direct, chat de meute §f/lg meute§7, §f/lg hurler§7, vision de nuit) mais qui a "oublié"
 * sa propre nature :
 * <ul>
 *   <li>Il ne figure pas dans la liste des alliés Loups (côté meute, ni la révélation
 *       automatique à 45 min par défaut, ni un §f/lg role§7) tant que son réveil individuel
 *       n'a pas sonné - un instant tiré au sort une seule fois à l'attribution, entre
 *       {@value #REVEIL_MIN_MINUTES} et {@value #REVEIL_MAX_MINUTES} minutes de jeu réel.</li>
 *   <li>De son côté, sa PROPRE liste ne contient au départ que son nom : elle se complète
 *       automatiquement, Loup par Loup, à chaque fois qu'il s'approche à moins de 10 blocs
 *       d'un autre Loup-Garou vivant.</li>
 * </ul>
 * L'essentiel de la mécanique (minuteur de réveil, découverte par proximité, filtrage de la
 * liste envoyée aux autres Loups) vit dans GameManager (voir tickLoupGarouAmnesique(),
 * envoyerListeAlliesLoup(), estAmnesiqueCache()) car elle doit tourner en boucle sur tous les
 * joueurs vivants — cette classe se contente de l'attribution initiale (tirage du réveil).
 *
 * Etats stockés sur le GamePlayer (voir GamePlayer#getEtat/#setEtat) :
 *  - "amnesique_instant_reveil_secondes" (long) : instant (temps de jeu écoulé, en secondes)
 *    auquel ce joueur se "souvient" et rejoint la liste visible de la meute. Tiré une fois ici.
 *  - "amnesique_revele" (boolean) : bascule à true par GameManager#tickLoupGarouAmnesique()
 *    une fois cet instant atteint.
 *  - "amnesique_connus" (Set&lt;UUID&gt;) : alliés Loups reconnus par proximité tant que
 *    "amnesique_revele" est encore false, alimenté par GameManager#tickLoupGarouAmnesique().
 *
 * Le bonus Vitesse + 2♥ d'Absorption sur mise à mort est géré à part, par
 * com.lguhc.listeners.AmnesiqueListener (écoute PlayerDeathEvent#getKiller()), et l'effet Force
 * de nuit par GameManager#appliquerEffetsPeriodiques() (comme la Vision Nocturne des autres Loups).
 */
public class LoupGarouAmnesiqueRole implements Role {

    private static final int REVEIL_MIN_MINUTES = 70;
    private static final int REVEIL_MAX_MINUTES = 90;

    @Override
    public RoleType getType() {
        return RoleType.LOUP_GAROU_AMNESIQUE;
    }

    @Override
    public void onAssign(LGUHCPlugin plugin, GamePlayer gp) {
        // Borne haute inclusive : nextLong(origin, bound) est exclusive côté haut, d'où le +1.
        long instantReveilSecondes = ThreadLocalRandom.current()
                .nextLong(REVEIL_MIN_MINUTES * 60L, REVEIL_MAX_MINUTES * 60L + 1L);
        gp.setEtat("amnesique_instant_reveil_secondes", instantReveilSecondes);
        gp.setEtat("amnesique_revele", false);

        if (gp.getPlayer() != null) {
            Msg.envoyer(gp.getPlayer(), "&8&oVous sentez une force sauvage en vous, mais votre mémoire est floue... "
                    + "vous ne reconnaissez actuellement personne comme l'un des vôtres.");
        }
    }
}
