package com.lguhc.game;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Calcule une composition de rôles adaptée au nombre de joueurs réels.
 *
 * Deux sources possibles, dans cet ordre de priorité :
 *
 *  0) COMPOSITION PERSONNALISEE : si config.yml définit une liste de rôles
 *     exacte pour ce nombre précis de joueurs (section "compositions"),
 *     elle est utilisée telle quelle (rôles mélangés aléatoirement entre
 *     les joueurs, mais l'ensemble des rôles est figé). Voir {@link #charger}.
 *
 *  1) MEUTE (calcul automatique, si aucune composition personnalisée ne
 *     correspond) : le nombre de Loups-Garous est calculé au prorata du
 *     nombre de joueurs (1 Loup pour {@link #joueursParLoup} joueurs,
 *     arrondi au-dessus, toujours au moins 1). Les rôles spéciaux de
 *     meute (Infect Père des Loups, Loup-Garou Perfide, Loup-Garou
 *     Mystique, Loup-Garou Blanc) sont ajoutés en priorité dans cet ordre
 *     dès qu'il y a assez de Loups pour les accueillir ; le reste de la
 *     meute est du Loup-Garou de base.
 *
 *  2) VILLAGE / HYBRIDES / SOLITAIRE (calcul automatique) : les places
 *     restantes sont distribuées selon un ordre de priorité fixe (rôles
 *     utiles même en petit comité d'abord, rôles qui ont besoin d'une
 *     grande tablée pour être intéressants en dernier). Les Sœurs
 *     comptent pour 2 places d'un coup (le duo n'a de sens que si les
 *     deux sont en jeu) : s'il ne reste qu'une place quand vient leur
 *     tour, elles sont sautées au profit du rôle suivant qui, lui, tient
 *     dans la place restante.
 *
 * S'il ne reste plus aucun rôle défini à distribuer mais qu'il reste des
 * joueurs (lobby très large), les places en trop sont comblées par du
 * Loup-Garou de base, faute d'un rôle "Villageois" générique dans le jeu -
 * même filet de sécurité que l'ancien système.
 */
public class CompositionManager {

    private final Logger logger;

    /** 1 Loup-Garou pour ce nombre de joueurs (arrondi au-dessus), toujours au moins 1 Loup. Réglable via config.yml. */
    private int joueursParLoup = 3;

    /** Compositions personnalisées chargées depuis config.yml (section "compositions"), indexées par nombre de joueurs. */
    private final Map<Integer, List<RoleType>> compositionsPersonnalisees = new HashMap<>();

    /**
     * Rôles exclus du calcul AUTOMATIQUE (voir construireListeRolesAutomatique), réglés via
     * /lg config > Compo (menu ConfigMenu) et persistés à part dans config.yml
     * (compo-manuelle.roles-desactives), indépendamment de la section "compositions" ci-dessus
     * (qui définit des listes exactes par nombre de joueurs et n'est pas affectée par ce filtre).
     * LOUP_GAROU ne peut jamais y figurer : c'est le rôle de secours qui comble les places
     * restantes, le retirer casserait le filet de sécurité déjà documenté plus bas.
     */
    private final Set<RoleType> rolesDesactives = EnumSet.noneOf(RoleType.class);

    /** Rôles spéciaux de meute, dans l'ordre où ils apparaissent à mesure que la meute grandit. */
    private static final List<RoleType> ORDRE_LOUPS_SPECIAUX = Arrays.asList(
            RoleType.INFECT_PERE_LOUPS,
            RoleType.LOUP_PERFIDE,
            RoleType.LOUP_MYSTIQUE,
            RoleType.LOUP_GAROU_BLANC
    );

    /**
     * Rôles Village / Hybrides / Solitaire, dans l'ordre où ils apparaissent
     * à mesure que le nombre de places restantes grandit. SOEURS coûte 2
     * places (voir logique d'allocation dans construireListeRoles).
     */
    private static final List<RoleType> ORDRE_VILLAGE = Arrays.asList(
            RoleType.VOYANTE,
            RoleType.SORCIERE,
            RoleType.CHASSEUR,
            RoleType.ANCIEN,
            RoleType.PETITE_FILLE,
            RoleType.CUPIDON,
            RoleType.SOEURS,
            RoleType.MONTREUR_OURS,
            RoleType.ASSASSIN,
            RoleType.ENFANT_SAUVAGE,
            RoleType.IDIOT_VILLAGE,
            RoleType.RENARD,
            RoleType.ANALYSTE,
            RoleType.DRUIDE
    );

    public CompositionManager(Logger logger) {
        this.logger = logger;
    }

    /**
     * Règle le ratio joueurs/Loup (lu depuis config.yml, clé "joueurs-par-loup") et charge les
     * compositions personnalisées éventuellement définies dans config.yml (section "compositions").
     * Une composition personnalisée pour un nombre de joueurs donné prend le pas sur le calcul
     * automatique dès qu'une partie démarre avec exactement ce nombre de joueurs vivants.
     */
    public void charger(int joueursParLoup, ConfigurationSection racineCompositions) {
        this.joueursParLoup = Math.max(1, joueursParLoup);
        compositionsPersonnalisees.clear();

        if (racineCompositions != null) {
            for (String cle : racineCompositions.getKeys(false)) {
                int nbJoueurs;
                try {
                    nbJoueurs = Integer.parseInt(cle.trim());
                } catch (NumberFormatException e) {
                    logger.warning("[LGUHC] compositions: clé \"" + cle + "\" invalide dans config.yml (doit être "
                            + "un nombre de joueurs, ex: \"14:\"). Entrée ignorée.");
                    continue;
                }

                List<String> nomsRoles = racineCompositions.getStringList(cle);
                List<RoleType> roles = new ArrayList<>();
                boolean valide = true;
                for (String nomRole : nomsRoles) {
                    try {
                        roles.add(RoleType.valueOf(nomRole.trim().toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException e) {
                        logger.warning("[LGUHC] compositions." + nbJoueurs + " : rôle inconnu \"" + nomRole
                                + "\" dans config.yml (identifiants valides : voir /lg admin roles en jeu). "
                                + "Composition ignorée pour " + nbJoueurs + " joueurs, calcul automatique utilisé à la place.");
                        valide = false;
                        break;
                    }
                }
                if (!valide) {
                    continue;
                }
                if (roles.size() != nbJoueurs) {
                    logger.warning("[LGUHC] compositions." + nbJoueurs + " : " + roles.size() + " rôle(s) listé(s) "
                            + "dans config.yml au lieu de " + nbJoueurs + ". Composition ignorée pour " + nbJoueurs
                            + " joueurs, calcul automatique utilisé à la place.");
                    continue;
                }

                compositionsPersonnalisees.put(nbJoueurs, roles);
            }
            if (!compositionsPersonnalisees.isEmpty()) {
                logger.info("[LGUHC] Composition(s) personnalisée(s) chargée(s) pour : " + compositionsPersonnalisees.keySet());
            }
        }

        logger.info("[LGUHC] Composition dynamique activée (1 Loup-Garou pour " + this.joueursParLoup
                + " joueurs) pour tout effectif sans composition personnalisée.");
    }

    /** Toujours calculable, quel que soit le nombre de joueurs : plus de config à vérifier ici. */
    public boolean estVide() {
        return false;
    }

    /**
     * (Re)charge la liste des rôles désactivés depuis config.yml (compo-manuelle.roles-desactives,
     * une liste d'identifiants de RoleType). Un identifiant inconnu ou invalide est ignoré
     * silencieusement (config modifiée à la main avec une typo) plutôt que de bloquer le
     * chargement du plugin. LOUP_GAROU est toujours filtré de cette liste au chargement : voir le
     * commentaire sur le champ rolesDesactives.
     */
    public void chargerRolesDesactives(List<String> nomsRolesDesactives) {
        rolesDesactives.clear();
        if (nomsRolesDesactives == null) {
            return;
        }
        for (String nom : nomsRolesDesactives) {
            try {
                RoleType type = RoleType.valueOf(nom.trim().toUpperCase(Locale.ROOT));
                if (type != RoleType.LOUP_GAROU) {
                    rolesDesactives.add(type);
                }
            } catch (IllegalArgumentException e) {
                logger.warning("[LGUHC] compo-manuelle.roles-desactives : rôle inconnu \"" + nom + "\" dans config.yml, entrée ignorée.");
            }
        }
    }

    /** Vrai si ce rôle peut être pioché par le calcul automatique (LOUP_GAROU l'est toujours, voir rolesDesactives). */
    public boolean estActif(RoleType type) {
        return type == RoleType.LOUP_GAROU || !rolesDesactives.contains(type);
    }

    /** Active/désactive un rôle pour le calcul automatique. Ignoré pour LOUP_GAROU (toujours actif). Ne persiste rien lui-même : à écrire dans config.yml par l'appelant (voir ConfigMenu). */
    public void setActif(RoleType type, boolean actif) {
        if (type == RoleType.LOUP_GAROU) {
            return;
        }
        if (actif) {
            rolesDesactives.remove(type);
        } else {
            rolesDesactives.add(type);
        }
    }

    /** Rôles actuellement désactivés du calcul automatique, pour affichage (ex: ConfigMenu) et sauvegarde config.yml. */
    public Set<RoleType> getRolesDesactives() {
        return Collections.unmodifiableSet(rolesDesactives);
    }

    /**
     * Construit la liste "à plat" des rôles à distribuer pour ce nombre de
     * joueurs (mélangée aléatoirement à la fin, l'ordre de construction ne
     * sert qu'à choisir QUELS rôles entrent en jeu). Utilise la composition
     * personnalisée de config.yml si elle existe pour ce nombre exact de
     * joueurs, sinon le calcul automatique.
     */
    public List<RoleType> construireListeRoles(int nbJoueursReels) {
        List<RoleType> personnalisee = compositionsPersonnalisees.get(nbJoueursReels);
        List<RoleType> liste = personnalisee != null
                ? new ArrayList<>(personnalisee)
                : construireListeRolesAutomatique(nbJoueursReels);

        Collections.shuffle(liste);
        return liste;
    }

    /** Calcul automatique (ancien comportement, toujours utilisé en l'absence de composition personnalisée). */
    private List<RoleType> construireListeRolesAutomatique(int nbJoueursReels) {
        List<RoleType> liste = new ArrayList<>();

        // --- 1) Meute ---
        int nbLoups = Math.max(1, (int) Math.ceil(nbJoueursReels / (double) joueursParLoup));
        nbLoups = Math.min(nbLoups, nbJoueursReels);

        for (RoleType special : ORDRE_LOUPS_SPECIAUX) {
            if (liste.size() >= nbLoups) break;
            if (!estActif(special)) continue; // désactivé via /lg config > Compo
            liste.add(special);
        }
        while (liste.size() < nbLoups) {
            liste.add(RoleType.LOUP_GAROU);
        }

        // --- 2) Village / Hybrides / Solitaire ---
        int placesRestantes = nbJoueursReels - liste.size();
        for (RoleType role : ORDRE_VILLAGE) {
            if (!estActif(role)) continue; // désactivé via /lg config > Compo
            int cout = (role == RoleType.SOEURS) ? 2 : 1;
            if (cout > placesRestantes) {
                continue; // pas assez de place pour ce rôle précis, on tente le suivant de la liste
            }
            liste.add(role);
            if (role == RoleType.SOEURS) {
                liste.add(RoleType.SOEURS);
            }
            placesRestantes -= cout;
        }

        // Lobby très large : plus de rôle défini à distribuer, on comble avec des Loups-Garous de base.
        while (placesRestantes > 0) {
            liste.add(RoleType.LOUP_GAROU);
            placesRestantes--;
        }

        return liste;
    }
}
