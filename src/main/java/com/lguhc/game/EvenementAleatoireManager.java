package com.lguhc.game;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Random;

/**
 * Réglage des "événements aléatoires" de partie (Exposed / Exposed Inversé / Rumeurs), chacun
 * activable INDÉPENDAMMENT des deux autres via /lg config > Événement aléatoire (voir ConfigMenu -
 * un hôte peut par exemple n'activer que Rumeurs sans se retrouver avec Exposed en plus). Comme
 * BorderManager/VoteManager, cette classe ne porte QUE la configuration (actif ou non par
 * événement, fenêtres de déclenchement en minutes) : le tirage des horaires exacts et l'exécution
 * des événements eux-mêmes restent orchestrés côté GameManager (mêmes accès aux joueurs vivants et
 * à diffuser() que tout le reste du moteur de jeu).
 *
 * Exposed et Exposé Inversé partagent 2 fenêtres de déclenchement ("1er"/"2e" événement) : à
 * chacune des 2, GameManager tire lequel des deux survient (50/50 si les deux sont actifs, sinon
 * celui qui est actif). Rumeurs, lui, est indépendant et n'a qu'UNE seule fenêtre (pas de "1er"/
 * "2e" : un seul tirage de messages par partie).
 */
public class EvenementAleatoireManager {

    public static final boolean EXPOSE_ACTIF_DEFAUT = false;
    public static final boolean EXPOSE_INVERSE_ACTIF_DEFAUT = false;
    public static final boolean RUMEURS_ACTIF_DEFAUT = false;

    public static final int PREMIER_MIN_DEFAUT = 60;
    public static final int PREMIER_MAX_DEFAUT = 80;
    public static final int SECOND_MIN_DEFAUT = 100;
    public static final int SECOND_MAX_DEFAUT = 120;

    public static final int RUMEURS_MIN_DEFAUT = 80;
    public static final int RUMEURS_MAX_DEFAUT = 120;
    /** Durée (en secondes réelles) de la fenêtre de collecte des messages de l'événement Rumeurs. Fixe, pas exposée dans le menu (non demandé). */
    public static final int RUMEURS_DUREE_COLLECTE_SECONDES = 20;

    /**
     * Nombre de joueurs vivants tirés au sort pour Exposé Inversé - c'est aussi, mécaniquement, le
     * nombre MINIMUM de joueurs vivants requis pour que l'événement soit jouable (voir
     * GameManager#declencherEvenementAleatoire). Réglable dans /lg config > Événement aléatoire
     * (au lieu d'un 5 figé en dur) : un host testant en petit comité peut le baisser pour pouvoir
     * observer l'événement sans réunir 5 joueurs vivants.
     */
    public static final int EXPOSE_INVERSE_JOUEURS_MIN_DEFAUT = 5;

    private boolean exposeActif = EXPOSE_ACTIF_DEFAUT;
    private boolean exposeInverseActif = EXPOSE_INVERSE_ACTIF_DEFAUT;
    private boolean rumeursActif = RUMEURS_ACTIF_DEFAUT;

    private int premierMinMinutes = PREMIER_MIN_DEFAUT;
    private int premierMaxMinutes = PREMIER_MAX_DEFAUT;
    private int secondMinMinutes = SECOND_MIN_DEFAUT;
    private int secondMaxMinutes = SECOND_MAX_DEFAUT;

    private int rumeursMinMinutes = RUMEURS_MIN_DEFAUT;
    private int rumeursMaxMinutes = RUMEURS_MAX_DEFAUT;

    private int exposeInverseJoueursMinimum = EXPOSE_INVERSE_JOUEURS_MIN_DEFAUT;

    /**
     * (Re)charge les réglages depuis la section "evenements-aleatoires" de config.yml. Comme les
     * autres nouveaux réglages introduits par le menu (voir REGLAGES_REGLES dans ConfigMenu), ces
     * clés n'ont pas besoin d'exister déjà dans config.yml : les valeurs par défaut ci-dessus sont
     * utilisées tant que le host n'a pas touché à l'onglet correspondant.
     */
    public void charger(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        // Migration : "actif" était auparavant un unique toggle partagé par Exposed ET Exposé
        // Inversé (avant l'ajout de Rumeurs et des toggles indépendants). S'il est présent dans un
        // config.yml existant sans les nouvelles clés dédiées, il sert de valeur par défaut aux
        // deux - pour qu'une config qui avait déjà les événements activés ne les perde pas
        // silencieusement au premier redémarrage après cette mise à jour.
        boolean ancienActifPartage = section.getBoolean("actif", EXPOSE_ACTIF_DEFAUT);
        this.exposeActif = section.getBoolean("expose.actif", ancienActifPartage);
        this.exposeInverseActif = section.getBoolean("expose-inverse.actif", ancienActifPartage);
        this.rumeursActif = section.getBoolean("rumeurs.actif", RUMEURS_ACTIF_DEFAUT);

        this.exposeInverseJoueursMinimum = section.getInt("expose-inverse.joueurs-minimum", EXPOSE_INVERSE_JOUEURS_MIN_DEFAUT);

        this.premierMinMinutes = section.getInt("premier-min-minutes", PREMIER_MIN_DEFAUT);
        this.premierMaxMinutes = section.getInt("premier-max-minutes", PREMIER_MAX_DEFAUT);
        this.secondMinMinutes = section.getInt("second-min-minutes", SECOND_MIN_DEFAUT);
        this.secondMaxMinutes = section.getInt("second-max-minutes", SECOND_MAX_DEFAUT);

        this.rumeursMinMinutes = section.getInt("rumeurs-min-minutes", RUMEURS_MIN_DEFAUT);
        this.rumeursMaxMinutes = section.getInt("rumeurs-max-minutes", RUMEURS_MAX_DEFAUT);
    }

    public boolean isExposeActif() {
        return exposeActif;
    }

    public boolean isExposeInverseActif() {
        return exposeInverseActif;
    }

    public boolean isRumeursActif() {
        return rumeursActif;
    }

    /** Nombre de joueurs tirés au sort pour Exposé Inversé, aussi utilisé comme seuil minimum de joueurs vivants requis (voir doc du champ). */
    public int getExposeInverseJoueursMinimum() {
        return exposeInverseJoueursMinimum;
    }

    /** Vrai si au moins un des deux (Exposed / Exposé Inversé) est actif - utilisé pour savoir si les 2 fenêtres de tirage partagées doivent être programmées (voir GameManager#veritableDebutPartie). */
    public boolean isExposeOuInverseActif() {
        return exposeActif || exposeInverseActif;
    }

    /** Tire une durée aléatoire (secondes de jeu réel depuis /lg start) pour le 1er événement Exposed/Exposé Inversé, dans la fenêtre premier-min/max-minutes (défaut 60-80 min). */
    public long tirerDelaiPremierEvenementSecondes(Random random) {
        return tirerDansFenetreSecondes(random, premierMinMinutes, premierMaxMinutes);
    }

    /** Idem pour le 2e événement Exposed/Exposé Inversé (défaut 100-120 min). */
    public long tirerDelaiSecondEvenementSecondes(Random random) {
        return tirerDansFenetreSecondes(random, secondMinMinutes, secondMaxMinutes);
    }

    /** Tire une durée aléatoire pour l'unique fenêtre de l'événement Rumeurs (défaut 80-120 min, indépendante des 2 fenêtres Exposed/Exposé Inversé ci-dessus). */
    public long tirerDelaiRumeursSecondes(Random random) {
        return tirerDansFenetreSecondes(random, rumeursMinMinutes, rumeursMaxMinutes);
    }

    /** Bornes défensives : si min > max (mauvais réglage host), on retombe sur min plutôt que de planter. */
    private long tirerDansFenetreSecondes(Random random, int minMinutes, int maxMinutes) {
        int min = Math.max(0, minMinutes);
        int max = Math.max(min, maxMinutes);
        int minutesTirees = (max == min) ? min : min + random.nextInt(max - min + 1);
        return minutesTirees * 60L;
    }

    public int getPremierMinMinutes() {
        return premierMinMinutes;
    }

    public int getPremierMaxMinutes() {
        return premierMaxMinutes;
    }

    public int getSecondMinMinutes() {
        return secondMinMinutes;
    }

    public int getSecondMaxMinutes() {
        return secondMaxMinutes;
    }

    public int getRumeursMinMinutes() {
        return rumeursMinMinutes;
    }

    public int getRumeursMaxMinutes() {
        return rumeursMaxMinutes;
    }
}
