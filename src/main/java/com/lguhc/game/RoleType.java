package com.lguhc.game;

/**
 * Liste des rôles disponibles, avec leur camp de départ, leur Aura (ce que
 * "voient" les pouvoirs de détection passifs) et une courte description
 * affichée au joueur. Le comportement de chaque rôle est implémenté dans
 * une classe à part (voir package com.lguhc.roles.*), fabriquée par
 * RoleFactory.
 *
 * L'Aura est fixée explicitement par rôle (indépendamment du camp de
 * départ) :
 *  - Lumineuse : Voyante, Renard, Montreur d'Ours, Idiot du Village,
 *    Loup-Garou Mystique, Cupidon
 *  - Neutre    : Petite Fille, Chasseur, Ancien, Loup-Garou Perfide,
 *    Enfant Sauvage
 *  - Obscure   : Sorcière, Druide, Loup-Garou, Infect Père des Loups,
 *    Loup-Garou Blanc, Assassin
 */
public enum RoleType {

    // --- Village ---
    VOYANTE("Voyante", Camp.VILLAGE, Aura.LUMINEUSE, "§71x/épisode, §f/lg voir <joueur> §7révèle 2 rôles possibles pour lui (dont son vrai rôle)."),
    MONTREUR_OURS("Montreur d'Ours", Camp.VILLAGE, Aura.LUMINEUSE, "§7Au début de chaque épisode, votre ours grogne une fois par Loup-Garou dans un rayon de 50 blocs."),
    RENARD("Renard", Camp.VILLAGE, Aura.LUMINEUSE, "§7La nuit, §f/lg flairer <joueur> §7(3 fois) : restez à moins de 15 blocs de lui quelques secondes. Après vos 3 flairages, apprenez combien étaient des Loups-Garous."),
    DRUIDE("Druide", Camp.VILLAGE, Aura.OBSCURE, "§7A partir de l'épisode 3, une fois par épisode : accroupissez-vous et mangez une carotte dorée pour sentir le nombre de Loups-Garous dans 50 blocs."),
    PETITE_FILLE("Petite Fille", Camp.VILLAGE, Aura.NEUTRE, "§7La nuit, §f/lg espionner §7retire votre armure et vous rend invisible (mais affaiblie) 5 minutes pour risquer d'espionner le chat des Loups — rompu si vous rééquipez une armure."),
    SORCIERE("Sorcière", Camp.VILLAGE, Aura.OBSCURE, "§7Prévenue à chaque mort non infectée. §f/lg ressusciter <joueur> §7ramène 1 seule fois dans la partie n'importe quel joueur mort."),
    CHASSEUR("Chasseur", Camp.VILLAGE, Aura.NEUTRE, "§7Force progressive contre les Loups. A votre mort, /lg tirer inflige 6 coeurs à un joueur (pas votre tueur)."),
    IDIOT_VILLAGE("Idiot du Village", Camp.VILLAGE, Aura.LUMINEUSE, "§7Si un non-Loup vous tue, vous réapparaissez instantanément à pleine vie ailleurs sur la carte (rôle révélé). Un Loup vous tue pour de bon."),
    ANCIEN("Ancien", Camp.VILLAGE, Aura.NEUTRE, "§7Resistance quasi permanente. Ressuscite 1x si tué par un Loup ; si tué par un non-Loup, le tueur perd tout."),
    SOEURS("Sœurs", Camp.VILLAGE, Aura.LUMINEUSE, "§7Vous connaissez l'identité de l'autre Sœur : §f/lg soeur <message> §7lui envoie un message privé (2 fois par épisode)."),

    // --- Loups-Garous ---
    LOUP_GAROU("Loup-Garou", Camp.LOUPS, Aura.OBSCURE, "§7Aucune commande pour tuer : éliminez vos victimes en combat direct. Tous les Loups ont aussi §f/lg hurler §7(1x/partie, révèle les Loups proches) et §f/lg meute <message> §7pour se parler."),
    INFECT_PERE_LOUPS("Infect Père des Loups", Camp.LOUPS, Aura.OBSCURE, "§7Corrompt les joueurs proches de vous au fil du temps. Quand une victime corrompue meurt, §f/lg infecter §7la relève en Loup-Garou (fenêtre limitée) — peut aussi ressusciter un allié Loup fraîchement tué."),
    LOUP_GAROU_BLANC("Loup-Garou Blanc", Camp.LOUPS, Aura.OBSCURE, "§7Loup-Garou en apparence, mais qui doit gagner seul : une nuit paire sur deux, §f/lg loupblanc <joueur> §7dévore en secret un autre Loup-Garou."),
    LOUP_PERFIDE("Loup-Garou Perfide", Camp.LOUPS, Aura.NEUTRE, "§7Une fois par nuit, §f/lg perfide §7retire votre armure et vous rend invisible 5 minutes — rompu si vous rééquipez une armure."),
    LOUP_MYSTIQUE("Loup-Garou Mystique", Camp.LOUPS, Aura.LUMINEUSE, "§7Quand un Loup-Garou meurt, vous recevez le nom et le rôle d'un joueur d'un autre camp."),

    // --- Hybrides ---
    // Camp de départ = VILLAGE (logique de victoire inchangée), mais couleur
    // d'affichage violette via le flag "hybride" ci-dessous.
    CUPIDON("Cupidon", Camp.VILLAGE, Aura.LUMINEUSE, "§7Au début de l'épisode 2, §f/lg couple <joueur1> <joueur2> §7forme un couple entre deux autres joueurs (1 seule fois). Liés pour le meilleur et pour le pire.", true),
    ENFANT_SAUVAGE("Enfant Sauvage", Camp.VILLAGE, Aura.NEUTRE, "§7Au début de l'épisode 2, §f/lg choisir <joueur> §7désigne votre modèle (1 seule fois). S'il meurt, vous devenez Loup-Garou.", true),

    // --- Solitaire ---
    ASSASSIN("Assassin", Camp.SOLO, Aura.OBSCURE, "§7Doit éliminer tout le monde et être l'unique survivant. Force le jour, quand les Loups sont vulnérables.");

    /** Couleur d'affichage dédiée aux rôles Hybrides (le camp de jeu, lui, reste Village). */
    private static final String COULEUR_HYBRIDE = "§5";
    private static final String LIBELLE_HYBRIDE = "Hybride";

    private final String nomAffiche;
    private final Camp campDepart;
    private final Aura aura;
    private final String description;
    private final boolean hybride;

    RoleType(String nomAffiche, Camp campDepart, Aura aura, String description) {
        this(nomAffiche, campDepart, aura, description, false);
    }

    RoleType(String nomAffiche, Camp campDepart, Aura aura, String description, boolean hybride) {
        this.nomAffiche = nomAffiche;
        this.campDepart = campDepart;
        this.aura = aura;
        this.description = description;
        this.hybride = hybride;
    }

    public String getNomAffiche() {
        return nomAffiche;
    }

    public Camp getCampDepart() {
        return campDepart;
    }

    /** Aura fixe de ce rôle (indépendante du camp de départ), attribuée au joueur à l'assignation du rôle. */
    public Aura getAura() {
        return aura;
    }

    public String getDescription() {
        return description;
    }

    public boolean estLoup() {
        return campDepart == Camp.LOUPS;
    }

    /** Vrai uniquement pour les rôles Hybrides (Cupidon, Enfant Sauvage) — affichage seulement, sans effet sur le camp de jeu. */
    public boolean estHybride() {
        return hybride;
    }

    /** Couleur d'affichage du rôle : violet pour les Hybrides, sinon la couleur de son camp de départ. */
    public String getCouleur() {
        return hybride ? COULEUR_HYBRIDE : campDepart.getCouleur();
    }

    /** Libellé de catégorie affiché : "Hybride" pour Cupidon/Enfant Sauvage, sinon le nom du camp de départ. */
    public String getLibelleCategorie() {
        return hybride ? LIBELLE_HYBRIDE : campDepart.getNomAffiche();
    }

    /** Nom du rôle coloré, prêt à afficher (ex : "§5Cupidon", "§cLoup-Garou"). */
    public String getNomFormate() {
        return getCouleur() + nomAffiche;
    }

    /** Préfixe coloré type "§5[Hybride] " ou "§c[Loups-Garous] ", utilisable devant un pseudo ou une description. */
    public String getPrefixe() {
        return getCouleur() + "[" + getLibelleCategorie() + "] ";
    }

    /**
     * Carte d'annonce du rôle (même disposition que la référence UHC World) : en-tête avec
     * numéro d'épisode, bandeau "Rôle", puis rôle / objectif / aura / description. Tableau de
     * lignes déjà colorées, prêtes à envoyer une par une (ex: via Msg.envoyer, une ligne = un
     * sendMessage) — à utiliser aussi bien pour l'attribution initiale que pour un rappel en
     * début d'épisode.
     */
    public String[] getCarteAnnonce(int episode) {
        String separateur = "§8§m                                                  ";
        return new String[] {
                separateur,
                "      §7Début de l'épisode §f" + episode,
                separateur,
                "",
                "                §f| §cRôle §f|",
                "",
                "§7• Vous êtes " + getNomFormate(),
                "§7• Objectif : " + getPhraseObjectif(),
                "§7• Aura : " + aura.getNomFormate(),
                "",
                description
        };
    }

    /** Phrase d'objectif affichée dans la carte, dérivée du camp de départ (pas du camp actuel). */
    private String getPhraseObjectif() {
        switch (campDepart) {
            case LOUPS:
                return "Vous devez gagner avec les Loups-Garous.";
            case SOLO:
                return "Vous devez éliminer tout le monde et être l'unique survivant.";
            case VILLAGE:
            default:
                return "Vous devez gagner avec le Village.";
        }
    }
}
