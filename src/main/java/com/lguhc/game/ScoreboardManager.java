package com.lguhc.game;

import com.lguhc.LGUHCPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.NameTagVisibility;

import java.util.Map;
import java.util.UUID;

/**
 * Tableau de bord latéral, mis en forme pour coller à la référence UHC World
 * ("» Informations", lignes courtes, séparateurs en tirets entre chaque
 * catégorie).
 *
 * Deux contraintes du protocole 1.8 à respecter partout dans cette classe :
 *
 * 1) Chaque ligne repose sur un "faux nom de joueur" limité à 16 caractères
 *    BRUTS, codes couleur § inclus (chaque code couleur/format compte pour 2
 *    caractères bruts). Toutes les lignes ci-dessous sont calibrées pour
 *    rester sous cette limite avec les valeurs réalistes du jeu (18-22
 *    joueurs, bordure à 4 chiffres, etc).
 *
 * 2) Le client 1.8 n'affiche jamais plus de 15 lignes sur un même sidebar :
 *    au-delà, seules les 15 lignes au score le plus élevé restent visibles,
 *    les autres disparaissent silencieusement (aucune erreur, aucun log).
 *    C'est pour ça que la ligne de rôle est maintenant ajoutée en tout
 *    premier (juste après le titre, donc au score le plus haut) plutôt
 *    qu'en dernier comme avant : avec une composition restante un peu
 *    longue, l'ancien ordre poussait cette ligne hors des 15 lignes
 *    visibles et elle disparaissait purement et simplement côté client,
 *    même si le code qui l'ajoutait était correct. Si jamais ça déborde
 *    encore aujourd'hui, c'est la section "» Composition" (en tout dernier,
 *    donc au score le plus bas) qui sera coupée en premier, jamais le rôle.
 *
 * Autre piège du même protocole : deux lignes au texte strictement identique
 * fusionnent en une seule entrée de score (Bukkit les indexe par leur
 * texte). Les séparateurs utilisent donc chacun un préfixe couleur
 * invisible différent (voir MARQUEURS) pour rester uniques tout en
 * affichant tous la même ligne de tirets.
 */
public class ScoreboardManager {

    /** Préfixes invisibles (recouverts par la couleur affichée juste derrière) qui servent uniquement à rendre chaque séparateur unique pour Bukkit. */
    private static final String[] MARQUEURS = {"§0", "§1", "§2", "§3", "§4", "§5"};

    /**
     * Entrées invisibles (uniquement des codes couleur/format, aucun glyphe) utilisées comme
     * faux nom de joueur par {@link #ligneLongue}, l'équivalent de MARQUEURS pour cette
     * technique-là. Codes délibérément distincts de MARQUEURS (qui utilise §0-§5) : les deux
     * techniques cohabitent dans le même mettreAJour(), et deux entrées identiques
     * fusionneraient en une seule ligne côté client (même piège que documenté en tête de
     * classe pour les séparateurs).
     */
    private static final String[] MARQUEURS_LIGNE = {
            "§6§r", "§7§r", "§8§r", "§9§r", "§a§r", "§b§r", "§c§r", "§d§r",
            "§e§r", "§f§r", "§k§r", "§l§r", "§m§r", "§n§r", "§o§r"
    };

    public void mettreAJour(LGUHCPlugin plugin) {
        GameManager gm = plugin.getGameManager();
        for (GamePlayer gp : gm.getTousLesJoueurs()) {
            Player joueur = gp.getPlayer();
            if (joueur == null) {
                continue;
            }
            Scoreboard board = joueur.getServer().getScoreboardManager().getNewScoreboard();
            Objective obj = board.registerNewObjective("lguhc", "dummy");
            obj.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "LG-UHC");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);

            int score = 30;
            int marqueur = 0;

            // » Rôle — en premier / au score le plus haut : la ligne qui doit survivre
            // en priorité si tout ne rentre pas dans les 15 lignes visibles.
            score = ligne(obj, separateur(marqueur++), score);
            score = ligne(obj, ChatColor.AQUA + "» Rôle", score);
            if (gp.getRole() != null) {
                RoleType type = gp.getRole();
                // Nom complet, sans troncature (voir ligneLongue : la limite de 16 caractères
                // bruts d'un nom de joueur classique est contournée via une Team prefix+suffix).
                // Indispensable pour les rôles à rallonge comme "Infecté Père des Loups".
                score = ligneLongue(board, obj, type.getCouleur() + type.getNomAffiche(), score, 0);
            } else {
                score = ligne(obj, ChatColor.WHITE + "bientôt...", score);
            }

            score = ligne(obj, separateur(marqueur++), score);
            score = ligne(obj, ChatColor.AQUA + "» Informations", score);

            int vivants = gm.getJoueursVivants().size();
            score = ligne(obj, ChatColor.GRAY + "Joueurs: " + vivants + "/" + gm.getTousLesJoueurs().size(), score);
            score = ligne(obj, ChatColor.GRAY + "Durée: " + formaterTemps(gm.getTempsTotalEcouleSecondes()), score);
            score = ligne(obj, ChatColor.GRAY + "Cycle: " + libellePhase(gm.getPhase()), score);
            score = ligne(obj, ChatColor.GRAY + "Episode: " + gm.getEpisodeActuel(), score);
            int limiteGroupe = gm.getLimiteGroupeActuelle();
            if (limiteGroupe > 0) {
                score = ligne(obj, ChatColor.GRAY + "Groupe: " + limiteGroupe, score);
            }

            score = ligne(obj, ChatColor.GREEN + "Village: " + gm.compterVivants(Camp.VILLAGE), score);
            score = ligne(obj, ChatColor.RED + "Loups: " + gm.compterVivants(Camp.LOUPS), score);
            score = ligne(obj, ChatColor.GOLD + "Solo: " + gm.compterVivants(Camp.SOLO), score);

            score = ligne(obj, separateur(marqueur++), score);
            score = ligne(obj, ChatColor.AQUA + "Bordure: ±" + Math.round(tailleBordure(joueur)), score);
            score = ligne(obj, ChatColor.AQUA + "0,0: " + Math.round(distanceOrigine(joueur.getLocation())) + "m", score);

            Map<RoleType, Integer> compoRestante = gm.getCompositionRestante();
            if (!compoRestante.isEmpty()) {
                score = ligne(obj, separateur(marqueur++), score);
                score = ligne(obj, ChatColor.AQUA + "» Composition", score);
                // indexLigneLongue démarre à 1 : l'index 0 est déjà pris par la ligne de rôle
                // ci-dessus (chaque appel à ligneLongue() dans ce même mettreAJour() a besoin
                // d'un index distinct, voir MARQUEURS_LIGNE).
                int indexLigneLongue = 1;
                for (Map.Entry<RoleType, Integer> entree : compoRestante.entrySet()) {
                    String texte = ChatColor.DARK_AQUA + entree.getKey().getNomAffiche() + ": " + entree.getValue();
                    score = ligneLongue(board, obj, texte, score, indexLigneLongue++);
                }
            }

            appliquerCouleursPersonnalisees(board, gp, gm);
            joueur.setScoreboard(board);
        }
    }

    /** Crée une équipe par couleur assignée par ce joueur, et y ajoute les cibles concernées (visible pour lui seul). */
    private void appliquerCouleursPersonnalisees(Scoreboard board, GamePlayer gp, GameManager gm) {
        int compteur = 0;
        for (Map.Entry<UUID, ChatColor> entree : gp.getCouleursPersonnalisees().entrySet()) {
            GamePlayer cible = gm.getGamePlayer(entree.getKey());
            if (cible == null || cible.getPlayer() == null) {
                continue;
            }
            String nomEquipe = "lgc" + (compteur++);
            Team equipe = board.getTeam(nomEquipe) != null ? board.getTeam(nomEquipe) : board.registerNewTeam(nomEquipe);
            equipe.setPrefix(entree.getValue().toString());
            // Une Team en NameTagVisibility.ALWAYS (réglage par défaut) force l'affichage du
            // pseudo flottant au-dessus de la tête même pour une entité normalement invisible
            // (c'est d'ailleurs la technique utilisée par les plugins d'ESP/glow pour ça) : ça
            // "grillait" donc la Petite Fille ou le Loup Perfide pendant qu'ils sont invisibles.
            // NEVER masque uniquement ce tag flottant - la couleur reste appliquée dans la
            // tab-list, qui n'est pas concernée par ce réglage - et redevient ALWAYS dès que la
            // cible n'est plus sous invisibilité (ce bloc tourne à chaque rafraichissement du
            // scoreboard, donc la transition suit l'effet de potion en temps réel).
            boolean cibleInvisible = cible.getPlayer().hasPotionEffect(PotionEffectType.INVISIBILITY);
            equipe.setNameTagVisibility(cibleInvisible ? NameTagVisibility.NEVER : NameTagVisibility.ALWAYS);
            try {
                equipe.addPlayer(cible.getPlayer());
            } catch (Exception ignored) {
                // Si l'API diffère légèrement, on ignore plutôt que de bloquer tout le scoreboard.
            }
        }
    }

    /**
     * Ligne de séparation en tirets : visuellement identique à chaque appel (gris foncé),
     * mais textuellement unique grâce au préfixe invisible pioché dans MARQUEURS (recouvert
     * aussitôt par le code couleur gris qui suit, donc invisible à l'écran). 12 tirets +
     * 2 codes couleur (préfixe + gris) = 16 caractères bruts pile.
     */
    private String separateur(int index) {
        return MARQUEURS[index % MARQUEURS.length] + ChatColor.DARK_GRAY + "------------";
    }

    /**
     * Distance depuis le centre jusqu'au mur de la bordure, dans une direction donnée (ce que le
     * "±" affiché au scoreboard est censé représenter). WorldBorder#getSize() renvoie le CÔTÉ
     * TOTAL du carré (le diamètre) : une bordure réglée à 1000 s'étend de -500 à +500 depuis son
     * centre. Sans cette division par 2, le scoreboard annonçait "±1000" pour une bordure dont
     * le mur réel n'est qu'à 500 du centre - c'était un bug d'affichage pur, la WorldBorder
     * elle-même était toujours correctement dimensionnée.
     */
    private double tailleBordure(Player joueur) {
        World monde = joueur.getWorld();
        return monde.getWorldBorder() != null ? monde.getWorldBorder().getSize() / 2.0 : 0;
    }

    /** Distance horizontale jusqu'à (0,0). L'ancienne direction cardinale (N/NE/E...) a été retirée : son calcul ne donnait pas un résultat fiable. */
    private double distanceOrigine(Location depuis) {
        double dx = 0 - depuis.getX();
        double dz = 0 - depuis.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private String formaterTemps(long secondes) {
        long h = secondes / 3600;
        long m = (secondes % 3600) / 60;
        long s = secondes % 60;
        if (h > 0) {
            return String.format("%d:%02d:%02d", h, m, s);
        }
        return String.format("%02d:%02d", m, s);
    }

    private int ligne(Objective obj, String texte, int score) {
        obj.getScore(texte).setScore(score);
        return score - 1;
    }

    /**
     * Variante de {@link #ligne} pour un texte qui dépasse la limite de 16 caractères bruts
     * d'un nom de joueur classique (ex : "§4Infecté Père des Loups" ≈ 24 caractères bruts,
     * couleur comprise). Technique standard sur protocole 1.8 : une Team dont le préfixe et
     * le suffixe (16 caractères bruts chacun, donc 32 au total) encadrent une "entrée" à part
     * qui porte le score - elle-même invisible (uniquement des codes couleur/format, aucun
     * glyphe) piochée dans MARQUEURS_LIGNE, donc rien de visible ne change pour le joueur par
     * rapport à une ligne normale.
     *
     * @param indexUnique index (0, 1, 2...) distinct à chaque appel de cette méthode dans un
     *                     même mettreAJour() : sert à choisir une entrée invisible différente
     *                     et un nom de Team différent à chaque fois, sans quoi deux lignes
     *                     fusionneraient en une seule (même piège que documenté en tête de
     *                     classe pour les séparateurs - un doublon de faux nom de joueur).
     */
    private int ligneLongue(Scoreboard board, Objective obj, String texte, int score, int indexUnique) {
        String entree = MARQUEURS_LIGNE[indexUnique % MARQUEURS_LIGNE.length];
        String nomEquipe = "lgl" + indexUnique;
        Team equipe = board.getTeam(nomEquipe) != null ? board.getTeam(nomEquipe) : board.registerNewTeam(nomEquipe);
        String[] parts = decouperEnPrefixeSuffixe(texte, 16);
        equipe.setPrefix(parts[0]);
        equipe.setSuffix(parts[1]);
        if (!equipe.hasEntry(entree)) {
            equipe.addEntry(entree);
        }
        return ligne(obj, entree, score);
    }

    /**
     * Découpe un texte (avec codes couleur §) en deux morceaux d'au plus {@code maxParPartie}
     * caractères bruts chacun, pour {@link #ligneLongue}. Ne coupe jamais un code couleur en
     * deux : un "§" ne se retrouve jamais seul en toute dernière position du premier morceau
     * (sinon il s'afficherait tel quel au lieu d'agir comme code couleur).
     */
    private String[] decouperEnPrefixeSuffixe(String texte, int maxParPartie) {
        if (texte.length() <= maxParPartie) {
            return new String[]{texte, ""};
        }
        int coupure = maxParPartie;
        if (texte.charAt(coupure - 1) == '§') {
            coupure--;
        }
        String suffixe = texte.substring(coupure);
        if (suffixe.length() > maxParPartie) {
            // Filet de sécurité : ne devrait pas arriver avec des noms de rôle réalistes (le
            // plus long, "Infecté Père des Loups" + couleur, tient largement dans les 32
            // caractères bruts que cette technique peut porter), mais évite une
            // IllegalArgumentException côté Bukkit si un texte dépasse un jour cette taille.
            suffixe = suffixe.substring(0, maxParPartie);
        }
        return new String[]{texte.substring(0, coupure), suffixe};
    }

    private String libellePhase(GamePhase phase) {
        switch (phase) {
            case LOBBY: return "Attente";
            case EPISODE_1: return "Prepa";
            case JOUR: return "Jour";
            case NUIT: return "Nuit";
            case TERMINEE: return "Fin";
            default: return "";
        }
    }
}
