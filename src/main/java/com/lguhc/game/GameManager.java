package com.lguhc.game;

import com.lguhc.LGUHCPlugin;
import com.lguhc.roles.Role;
import com.lguhc.roles.villageois.RenardRole;
import com.lguhc.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orchestre la partie de A à Z : lobby, épisode 1 (préparation), attribution
 * des rôles, alternance jour/nuit, vote de meute des Loups, effets
 * périodiques, corruption, conditions de victoire.
 */
public class GameManager {

    private final LGUHCPlugin plugin;
    private final Map<UUID, GamePlayer> joueurs = new LinkedHashMap<>();
    private final Random random = new Random();

    private GamePhase phase = GamePhase.LOBBY;
    private int episodeActuel = 0;
    private int numeroNuit = 0;
    private int blocagePouvoirsVillageJusquaEpisode = -1;
    private Location centreMonde;

    private int periodeDansEpisode = 0; // 0=Jour1, 1=Nuit1, 2=Jour2, 3=Nuit2 (4 périodes par épisode)
    private final TreeMap<Integer, Integer> paliersGroupes = new TreeMap<>();
    private List<RoleType> compositionUtilisee = new ArrayList<>();

    private String raisonTransitoire = null;
    private Camp campResponsableTransitoire = null;
    private long finPhaseTimestamp = 0L;
    private long debutPartieTimestamp = 0L;
    /**
     * Ticks écoulés depuis le début de la phase JOUR/NUIT actuelle, remis à 0 à chaque
     * démarrerJour()/démarrerNuit() et incrémenté de 20 à chaque appel de tickCycleJourNuit()
     * (lui-même déclenché 1x/seconde par le minuteur de LGUHCPlugin). Utilisé uniquement pour
     * synchroniser l'heure visuelle — voir tickCycleJourNuit().
     * On NE PEUT PAS utiliser World#getFullTime() pour ça (comme avant) : ce compteur n'avance
     * tout seul que si le gamerule doDaylightCycle est actif, or il est volontairement désactivé
     * (voir demarrer()) pour empêcher l'horloge vanilla de faire concurrence à setTime() ici.
     * Résultat avec l'ancienne méthode : getFullTime() ne bougeait plus QUE via nos propres
     * appels à setTime(), qui eux-mêmes ne recalculaient une nouvelle cible qu'en fonction de
     * ce même compteur immobile - une boucle fermée bloquée à 0 pour toujours, d'où un soleil
     * et une lune qui ne bougeaient jamais après le saut initial à l'aube/la tombée de la nuit.
     */
    private long ticksEcoulesDansPhase = 0L;

    private boolean listeLoupsRevelee = false;

    /** Chat général désactivé PAR DÉFAUT (un hôte le réactive avec /lg admin chat), voir ChatToggleListener. Etat volatile (non persisté dans config.yml) : redevient désactivé à chaque redémarrage du plugin. */
    private boolean chatDesactive = true;

    /**
     * Délai (en secondes de jeu réel, depuis le lancement de la partie) avant la révélation de la
     * liste des alliés Loups. Réglable via /lg config > Règle (survie-uhc.minutes-avant-liste-loups,
     * 45 min par défaut) — anciennement une constante figée en dur (2700L), désormais lue à chaque
     * appel dans tickMinuteriesAutomatiques() (1x/s, coût négligeable).
     */
    public long getDelaiRevelationListeLoupsSecondes() {
        return plugin.getConfig().getLong("survie-uhc.minutes-avant-liste-loups", 45) * 60L;
    }

    /** Délai de grâce accordé à un joueur qui se déconnecte en pleine partie avant d'être éliminé définitivement (un simple crash ne doit pas coûter la partie). */
    private static final long DELAI_GRACE_DECONNEXION_SECONDES = 2 * 60L;
    /**
     * UUID des joueurs actuellement déconnectés en pleine partie, associé à l'horodatage réel
     * (System.currentTimeMillis()) auquel ils seront éliminés faute d'être revenus à temps.
     * Voir surDeconnexion() / surReconnexion() / tickDeconnexions().
     */
    private final Map<UUID, Long> echeancesDeconnexion = new LinkedHashMap<>();

    /** Durée du gel de préparation (immobilité + Blindness + compte à rebours en Title) juste après /lg start, avant le vrai début de partie. */
    private static final int DUREE_GEL_DEBUT_SECONDES = 10;
    /** Durée de l'invincibilité générale accordée à tous les joueurs au vrai début de partie (voir veritableDebutPartie() / tickInvincibiliteDebut()). */
    private static final long DUREE_INVINCIBILITE_DEBUT_SECONDES = 5 * 60L;
    /** Horodatage réel (System.currentTimeMillis()) du début de l'invincibilité de début de partie, 0 si aucune partie n'est en cours. */
    private long debutInvincibiliteTimestamp = 0L;
    /** Dernière minute écoulée pour laquelle un rappel a déjà été envoyé dans le chat (0 à 5), voir tickInvincibiliteDebut(). */
    private int dernierRappelInvincibiliteMinute = 0;

    /**
     * Horodatages (secondes de jeu réel écoulées depuis /lg start, voir getTempsTotalEcouleSecondes())
     * auxquels le 1er/2e événement aléatoire (Exposed / Exposé Inversé) doit se déclencher, tirés
     * au sort une seule fois au vrai début de partie (voir veritableDebutPartie()) dans les fenêtres
     * réglées par EvenementAleatoireManager. -1 si Exposed ET Exposé Inversé sont tous deux
     * désactivés (ou si aucune partie n'est en cours) : voir tickMinuteriesAutomatiques() pour le
     * déclenchement.
     */
    private long secondesPremierEvenementAleatoire = -1L;
    private long secondesSecondEvenementAleatoire = -1L;
    private boolean premierEvenementAleatoireDeclenche = false;
    private boolean secondEvenementAleatoireDeclenche = false;

    /**
     * Horodatage de la fenêtre (unique, contrairement à Exposed/Exposé Inversé ci-dessus) de
     * l'événement Rumeurs, tiré une seule fois au vrai début de partie si
     * EvenementAleatoireManager#isRumeursActif(). -1 si désactivé (ou si aucune partie en cours).
     */
    private long secondesRumeursAleatoire = -1L;
    private boolean rumeursAleatoireDeclenche = false;
    /** Vrai pendant les EvenementAleatoireManager.RUMEURS_DUREE_COLLECTE_SECONDES de collecte des messages (voir declencherRumeurs()/cloturerRumeurs()) : RumeursListener n'intercepte le chat que pendant cette fenêtre. */
    private boolean collecteRumeursActive = false;
    /** UUID des joueurs ayant déjà envoyé leur message pendant la fenêtre de collecte en cours - un seul message pris en compte par joueur (voir enregistrerMessageRumeur()). */
    private final Set<UUID> joueursAyantEnvoyeRumeur = new HashSet<>();
    private final List<String> messagesRumeursCollectes = new ArrayList<>();

    public GameManager(LGUHCPlugin plugin) {
        this.plugin = plugin;
    }

    // ================= Lobby =================

    public boolean rejoindre(Player p) {
        if (phase != GamePhase.LOBBY) {
            Msg.envoyer(p, "&cUne partie est déjà en cours, impossible de la rejoindre.");
            return false;
        }
        if (joueurs.containsKey(p.getUniqueId())) {
            Msg.envoyer(p, "&cVous êtes déjà inscrit.");
            return false;
        }
        joueurs.put(p.getUniqueId(), new GamePlayer(p.getUniqueId(), p.getName()));
        diffuser("&a+ &f" + p.getName() + " &arejoint la partie ! (&e" + joueurs.size() + "&a inscrits)");
        return true;
    }

    public boolean quitter(Player p) {
        if (phase != GamePhase.LOBBY) {
            Msg.envoyer(p, "&cLa partie a déjà commencé, vous ne pouvez plus la quitter proprement.");
            return false;
        }
        if (joueurs.remove(p.getUniqueId()) != null) {
            diffuser("&c- &f" + p.getName() + " &cquitte la partie. (&e" + joueurs.size() + "&c inscrits)");
            return true;
        }
        return false;
    }

    public void demarrer(CommandSender hote) {
        demarrer(hote, false);
    }

    public void demarrer(CommandSender hote, boolean ignorerMinimum) {
        if (phase != GamePhase.LOBBY) {
            hote.sendMessage(Msg.c("&cUne partie est déjà en cours."));
            return;
        }
        if (plugin.getWorldResetManager().isRegenEnCours()) {
            hote.sendMessage(Msg.c("&cLe monde de jeu est en cours de régénération, réessayez dans quelques secondes."));
            return;
        }
        int minimum = ignorerMinimum ? 1 : 4;
        if (joueurs.size() < minimum) {
            hote.sendMessage(Msg.c("&cIl faut au moins " + minimum + " joueur(s) inscrit(s) pour lancer une partie (actuellement " + joueurs.size() + ")."));
            return;
        }
        if (plugin.getCompositionManager().estVide()) {
            hote.sendMessage(Msg.c("&cAucune composition de rôles n'est configurée dans config.yml !"));
            return;
        }

        World monde = Bukkit.getWorld(plugin.getConfig().getString("monde.nom", "world"));
        if (monde == null) {
            hote.sendMessage(Msg.c("&cLe monde configuré est introuvable."));
            return;
        }
        this.centreMonde = trouverBonSpawn(monde);

        diffuser("&a&lLa partie démarre avec " + joueurs.size() + " joueurs !");

        double rayonConfig = plugin.getConfig().getDouble("monde.rayon-teleportation-depart", 400);
        double demiTailleBordure = plugin.getBorderManager().getTailleInitiale() / 2.0;
        double rayon = Math.min(rayonConfig, demiTailleBordure * 0.8);
        plugin.getBorderManager().initialiser(monde, centreMonde);

        // Spawn "en couronne" : si monde.distance-spawn-min > 0 (réglable via /lg config > Map,
        // par pas de 100), les joueurs apparaissent à une distance tirée entre cette valeur et
        // +100 blocs, au lieu d'être répartis sur tout le disque [0, rayon] comme d'habitude
        // (ex : distance-spawn-min = 700 => joueurs entre 700 et 800 blocs du centre).
        // 0 (défaut) = comportement classique inchangé, voir emplacementAleatoireDansRayon.
        // La couronne est bornée à 90% du rayon de bordure de départ pour ne jamais poser un
        // joueur hors bordure (ou juste avant son premier resserrement) sur une petite bordure.
        double distanceSpawnMin = plugin.getConfig().getDouble("monde.distance-spawn-min", 0);
        double distanceSpawnMax = 0;
        if (distanceSpawnMin > 0) {
            distanceSpawnMax = Math.min(distanceSpawnMin + 100.0, demiTailleBordure * 0.9);
            distanceSpawnMin = Math.min(distanceSpawnMin, Math.max(0.0, distanceSpawnMax - 10.0));
        }

        monde.setGameRuleValue("doMobSpawning", "false");
        // Indispensable : sans ça, l'horloge vanilla avance toute seule en parallèle du setTime()
        // de tickCycleJourNuit() et lui fait concurrence, ce qui rend le cycle jour/nuit erratique.
        monde.setGameRuleValue("doDaylightCycle", "false");
        monde.setStorm(false);
        monde.setThundering(false);
        monde.setWeatherDuration(Integer.MAX_VALUE);

        for (GamePlayer gp : joueurs.values()) {
            Player p = gp.getPlayer();
            if (p == null) {
                continue;
            }
            p.setGameMode(GameMode.SURVIVAL);
            p.getInventory().clear();
            // PlayerInventory#clear() ne vide QUE les 36 emplacements normaux : les 4 emplacements
            // d'armure sont un tableau séparé côté Bukkit et ne sont jamais touchés par clear().
            // Sans cette ligne, un joueur qui portait déjà une armure (lobby, ancienne partie...)
            // la gardait telle quelle au lancement d'une nouvelle partie.
            p.getInventory().setArmorContents(new org.bukkit.inventory.ItemStack[4]);
            p.setMaxHealth(20.0);
            p.setHealth(20.0);
            p.setFoodLevel(20);
            for (PotionEffect eff : new ArrayList<>(p.getActivePotionEffects())) {
                p.removePotionEffect(eff.getType());
            }
            p.getInventory().addItem(new org.bukkit.inventory.ItemStack(org.bukkit.Material.BOOK, 7));
            p.getInventory().addItem(new org.bukkit.inventory.ItemStack(org.bukkit.Material.FEATHER, 16));
            p.getInventory().addItem(new org.bukkit.inventory.ItemStack(org.bukkit.Material.STRING, 6));
            p.getInventory().addItem(new org.bukkit.inventory.ItemStack(org.bukkit.Material.COOKED_BEEF, 64));
            Location depart = distanceSpawnMin > 0
                    ? emplacementAleatoireEnCouronne(monde, centreMonde, distanceSpawnMin, distanceSpawnMax)
                    : emplacementAleatoireDansRayon(monde, centreMonde, rayon);
            p.teleport(depart);
            gp.setVivant(true);
            gp.setDroitDeVote(true);
        }

        // Avant que la partie ne débute réellement (épisode 1, minuteries, invincibilité...), on
        // gèle tout le monde sur place, aveuglé, pendant quelques secondes (voir
        // demarrerCompteAReboursDebut ci-dessous). episodeActuel/phase/debutPartieTimestamp ne
        // sont positionnés qu'à l'issue de ce délai, dans veritableDebutPartie().
        for (GamePlayer gp : joueurs.values()) {
            gp.setEtat("gel_debut_actif", true);
        }
        demarrerCompteAReboursDebut(DUREE_GEL_DEBUT_SECONDES);
    }

    /**
     * Compte à rebours de préparation (10 secondes par défaut, voir DUREE_GEL_DEBUT_SECONDES)
     * affiché en Title au centre de l'écran. Pendant ce délai les joueurs sont figés sur place
     * (immobilité gérée par UHCRulesListener#surMouvementGelDebut, piloté par le flag d'état
     * "gel_debut_actif") et aveuglés (Blindness). Utilise un compteur de TICKS classique - et non
     * l'horloge réelle comme ailleurs dans cette classe (voir demarrer()) - car sur une durée
     * aussi courte la dérive liée au lag est négligeable, contrairement aux minuteries de
     * plusieurs minutes/dizaines de minutes qui, elles, doivent absolument s'appuyer sur
     * System.currentTimeMillis().
     */
    private void demarrerCompteAReboursDebut(int secondesRestantes) {
        if (secondesRestantes <= 0) {
            terminerCompteAReboursDebut();
            return;
        }
        for (GamePlayer gp : joueurs.values()) {
            Player p = gp.getPlayer();
            if (p == null) {
                continue;
            }
            // Durée de Blindness volontairement plus longue que l'intervalle (25s pour un tick
            // toutes les 1s) : simple garde-fou pour ne jamais laisser un "trou" sans cécité entre
            // deux appels si le serveur a un léger à-coup.
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 25 * 20, 0, false, false));
            p.sendTitle(Msg.c("&e&l" + secondesRestantes), Msg.c("&7Préparez-vous..."));
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> demarrerCompteAReboursDebut(secondesRestantes - 1), 20L);
    }

    /** Fin du gel de préparation : dégèle et rend la vue à tout le monde, puis lance vraiment la partie. */
    private void terminerCompteAReboursDebut() {
        for (GamePlayer gp : joueurs.values()) {
            gp.setEtat("gel_debut_actif", false);
            Player p = gp.getPlayer();
            if (p == null) {
                continue;
            }
            p.removePotionEffect(PotionEffectType.BLINDNESS);
            p.sendTitle(Msg.c("&a&lGO !"), Msg.c("&fLa partie commence !"));
        }
        veritableDebutPartie();
    }

    /**
     * Vrai lancement de la partie (temps de jeu réel, épisode 1, minuteries, invincibilité de
     * début...). Appelé uniquement à l'issue du gel de préparation ci-dessus - c'est ici, et non
     * dans demarrer(), que la partie "commence" au sens des règles (PvP, rôles, minuteries).
     */
    private void veritableDebutPartie() {
        episodeActuel = 1;
        debutPartieTimestamp = System.currentTimeMillis();
        phase = GamePhase.EPISODE_1;
        diffuser("&e&lEpisode 1 &e: récoltez des ressources et explorez, aucun rôle n'est encore actif. Le PvP sera activé à l'épisode 2.");
        planifierMiseAJourScoreboard();

        // Rôles et démarrage du resserrement de bordure : voir tickMinuteriesAutomatiques(),
        // appelée 1x/seconde. On ne programme plus ces deux actions via runTaskLater(ticks) :
        // ce délai est compté en TICKS SERVEUR, qui prennent plus de 50ms chacun dès que le
        // serveur a du retard (TPS < 20). Sur un vrai test avec 18-22 joueurs qui minent/explorent,
        // ce retard s'accumule sur toute la durée du délai et le fait déclencher plusieurs
        // dizaines de secondes (voire minutes) après les 20 minutes réelles annoncées. La vérification
        // dans tickMinuteriesAutomatiques() compare le temps RÉELLEMENT écoulé
        // (System.currentTimeMillis(), voir getTempsTotalEcouleSecondes()) à la durée configurée :
        // elle ne dérive donc jamais, quel que soit le TPS du serveur.
        long dureeEpisode1Secondes = plugin.getConfig().getLong("episodes.duree-minutes", 20) * 60L;
        this.finPhaseTimestamp = debutPartieTimestamp + (dureeEpisode1Secondes * 1000L);

        long ticksAvantCoupleAleatoire = com.lguhc.roles.hybrides.CupidonRole.MINUTES_AVANT_COUPLE_ALEATOIRE * 60L * 20L;
        Bukkit.getScheduler().runTaskLater(plugin, this::formerCoupleAleatoireSiBesoin, ticksAvantCoupleAleatoire);
        // NOTE : la révélation de la liste des alliés Loups (45 min) n'est plus programmée ici
        // via runTaskLater(ticks) - voir tickMinuteriesAutomatiques(), qui la déclenche sur la
        // même base de temps réel que le reste (même piège de dérive sous lag que celui déjà
        // documenté juste au-dessus pour les rôles et la bordure).

        // Invincibilité générale de début de partie (5 minutes par défaut, voir
        // DUREE_INVINCIBILITE_DEBUT_SECONDES) : couvre TOUS les types de dégâts (PvP, chute,
        // lave, mobs...) via estProtege(), vérifié dans UHCRulesListener#surDegatsGeneraux. Les
        // rappels 1x/minute dans le chat sont gérés par tickInvincibiliteDebut().
        this.debutInvincibiliteTimestamp = System.currentTimeMillis();
        this.dernierRappelInvincibiliteMinute = 0;
        long finInvincibiliteDebut = debutInvincibiliteTimestamp + (DUREE_INVINCIBILITE_DEBUT_SECONDES * 1000L);
        for (GamePlayer gp : joueurs.values()) {
            gp.setEtat("invincible_jusqua", finInvincibiliteDebut);
        }
        diffuser("&a&l✦ Invincibilité activée pour les " + (DUREE_INVINCIBILITE_DEBUT_SECONDES / 60L) + " premières minutes de jeu !");

        // Événements aléatoires (Exposed / Exposé Inversé) : les 2 horaires sont tirés une seule
        // fois ici (comme le couple aléatoire de Cupidon ci-dessus), puis comparés au temps réel
        // écoulé dans tickMinuteriesAutomatiques() - voir EvenementAleatoireManager pour les
        // fenêtres par défaut (60-80 min / 100-120 min). Programmés dès que l'un des deux est
        // actif (isExposeOuInverseActif()) : declencherEvenementAleatoire() se chargera de ne
        // faire jouer que celui (ou ceux) réellement activé(s) à chaque horaire.
        if (plugin.getEvenementAleatoireManager().isExposeOuInverseActif()) {
            this.secondesPremierEvenementAleatoire = plugin.getEvenementAleatoireManager().tirerDelaiPremierEvenementSecondes(random);
            this.secondesSecondEvenementAleatoire = plugin.getEvenementAleatoireManager().tirerDelaiSecondEvenementSecondes(random);
        } else {
            this.secondesPremierEvenementAleatoire = -1L;
            this.secondesSecondEvenementAleatoire = -1L;
        }
        this.premierEvenementAleatoireDeclenche = false;
        this.secondEvenementAleatoireDeclenche = false;

        // Rumeurs : événement indépendant d'Exposed/Exposé Inversé, avec son propre toggle et sa
        // propre fenêtre unique (défaut 80-120 min) - voir EvenementAleatoireManager.
        if (plugin.getEvenementAleatoireManager().isRumeursActif()) {
            this.secondesRumeursAleatoire = plugin.getEvenementAleatoireManager().tirerDelaiRumeursSecondes(random);
        } else {
            this.secondesRumeursAleatoire = -1L;
        }
        this.rumeursAleatoireDeclenche = false;
        this.collecteRumeursActive = false;
        this.joueursAyantEnvoyeRumeur.clear();
        this.messagesRumeursCollectes.clear();
    }

    /**
     * Vérifie, une fois par seconde réelle (appelée depuis le minuteur 1x/s de LGUHCPlugin), les
     * déclenchements automatiques qui doivent arriver à un instant précis du temps de jeu réel :
     * attribution des rôles (fin de l'épisode 1) et démarrage du resserrement de bordure. Voir le
     * commentaire dans demarrer() : ceci remplace les anciens runTaskLater(ticks), sensibles au lag.
     * Les deux déclenchements sont idempotents (assignerRoleA change la phase, demarrerResserrement
     * a son propre garde-fou interne), donc un appel répété sans effet une fois déclenché ne pose
     * pas de problème.
     */
    public void tickMinuteriesAutomatiques() {
        if (!estEnCours()) {
            return;
        }
        long ecouleSecondes = getTempsTotalEcouleSecondes();

        if (phase == GamePhase.EPISODE_1) {
            long dureeEpisode1Secondes = plugin.getConfig().getLong("episodes.duree-minutes", 20) * 60L;
            if (ecouleSecondes >= dureeEpisode1Secondes) {
                finDePremierEpisode();
            }
        }

        World monde = getMondeJeu();
        if (monde != null) {
            long secondesAvantResserrement = plugin.getBorderManager().getMinutesAvantMouvement() * 60L;
            if (ecouleSecondes >= secondesAvantResserrement && plugin.getBorderManager().demarrerResserrement(monde)) {
                diffuser("&6&lLa bordure commence à se resserrer...");
            }
        }

        // Liste des alliés Loups à 45 min de jeu réel : même logique que ci-dessus, comparée au
        // temps réellement écoulé plutôt que programmée en ticks (voir demarrer()). revelerListeLoups()
        // est idempotente (gardée par listeLoupsRevelee), donc rester dans cette condition un peu
        // trop longtemps après le déclenchement ne pose pas de problème.
        if (!listeLoupsRevelee && ecouleSecondes >= getDelaiRevelationListeLoupsSecondes()) {
            revelerListeLoups();
        }

        // Événements aléatoires (Exposed / Exposé Inversé) : mêmes raisons que ci-dessus (temps
        // réel plutôt que ticks programmés). Chaque déclenchement est gardé par son propre drapeau
        // *Declenche, donc rester dans la condition un peu trop longtemps après coup ne pose pas
        // de problème (idempotent).
        if (secondesPremierEvenementAleatoire >= 0 && !premierEvenementAleatoireDeclenche
                && ecouleSecondes >= secondesPremierEvenementAleatoire) {
            premierEvenementAleatoireDeclenche = true;
            declencherEvenementAleatoire();
        }
        if (secondesSecondEvenementAleatoire >= 0 && !secondEvenementAleatoireDeclenche
                && ecouleSecondes >= secondesSecondEvenementAleatoire) {
            secondEvenementAleatoireDeclenche = true;
            declencherEvenementAleatoire();
        }

        // Rumeurs : même principe, fenêtre unique et indépendante des 2 ci-dessus.
        if (secondesRumeursAleatoire >= 0 && !rumeursAleatoireDeclenche
                && ecouleSecondes >= secondesRumeursAleatoire) {
            rumeursAleatoireDeclenche = true;
            declencherRumeurs();
        }

        // Transitions Jour -> Nuit -> Jour : mêmes raisons que ci-dessus (voir le commentaire dans
        // demarrer()), comparées à finPhaseTimestamp (System.currentTimeMillis()) plutôt que
        // programmées via runTaskLater(ticks), qui dérivait sous lag. finDeJour()/finDeNuit() sont
        // idempotentes (gardées par une vérification stricte de la phase courante), donc rester
        // dans cette condition un tick de trop après le déclenchement ne pose pas de problème.
        if ((phase == GamePhase.JOUR || phase == GamePhase.NUIT)
                && finPhaseTimestamp > 0 && System.currentTimeMillis() >= finPhaseTimestamp) {
            if (phase == GamePhase.JOUR) {
                finDeJour();
            } else {
                finDeNuit();
            }
        }
    }

    private void formerCoupleAleatoireSiBesoin() {
        if (phase == GamePhase.TERMINEE) {
            return;
        }
        GamePlayer cupidon = getJoueursVivants().stream().filter(g -> g.getRole() == RoleType.CUPIDON).findFirst().orElse(null);
        if (cupidon == null || cupidon.getEtat("cupidon_pouvoir_utilise", false)) {
            return;
        }
        List<GamePlayer> candidats = new ArrayList<>(getJoueursVivants());
        candidats.remove(cupidon);
        if (candidats.size() < 2) {
            return;
        }
        java.util.Collections.shuffle(candidats, random);
        GamePlayer a = candidats.get(0);
        GamePlayer b = candidats.get(1);
        cupidon.setEtat("cupidon_pouvoir_utilise", true);
        plugin.getCoupleManager().formerCouple(this, a, b);
        Msg.envoyer(cupidon.getPlayer(), "&d✧ Vous n'avez formé aucun couple à temps : le destin en a choisi un pour vous, et vous en êtes informé : &f" + a.getNom() + " &d& &f" + b.getNom());
    }

    // ================= Événements aléatoires (Exposed / Exposé Inversé) =================

    /**
     * Appelée aux 2 horaires tirés dans veritableDebutPartie() (voir tickMinuteriesAutomatiques()).
     * Détermine lequel des 2 événements survient en fonction des toggles indépendants
     * d'EvenementAleatoireManager (voir la demande d'origine : activer l'un sans forcément activer
     * l'autre) :
     *  - Si les deux sont actifs, tirage 50/50 - sauf si Exposé Inversé n'est pas jouable (il lui
     *    faut un nombre minimum de joueurs vivants distincts, réglable via
     *    EvenementAleatoireManager#getExposeInverseJoueursMinimum(), 5 par défaut - voir
     *    declencherExposeInverse()), auquel cas on retombe sur Exposed, qui ne nécessite qu'un
     *    seul joueur vivant.
     *  - Si un seul des deux est actif, c'est toujours lui qui joue à cet horaire (sous réserve,
     *    pour Exposé Inversé seul, d'avoir ce nombre minimum de joueurs vivants - sinon rien ne se
     *    passe cette fois).
     * Ne devrait pas être appelée si aucun des deux n'est actif : les 2 horaires ne sont programmés
     * dans veritableDebutPartie() que si isExposeOuInverseActif() est vrai.
     */
    private void declencherEvenementAleatoire() {
        if (!estEnCours()) {
            return;
        }
        List<GamePlayer> vivants = getJoueursVivants();
        if (vivants.isEmpty()) {
            return;
        }
        EvenementAleatoireManager mgr = plugin.getEvenementAleatoireManager();
        boolean exposeOk = mgr.isExposeActif();
        boolean inverseOk = mgr.isExposeInverseActif() && vivants.size() >= mgr.getExposeInverseJoueursMinimum();

        if (exposeOk && inverseOk) {
            if (random.nextBoolean()) {
                declencherExposeInverse(vivants);
            } else {
                declencherExpose(vivants);
            }
        } else if (inverseOk) {
            declencherExposeInverse(vivants);
        } else if (exposeOk) {
            declencherExpose(vivants);
        }
        // Ni l'un ni l'autre jouable à cet horaire (ex : seul Exposé Inversé actif mais moins de 5
        // vivants) : rien ne se passe, tant pis pour cette fenêtre.
    }

    /**
     * Événement "Exposed" : un joueur vivant tiré au sort voit son pseudo annoncé dans le chat
     * général à côté de 4 rôles - le sien (toujours inclus), un rôle d'un camp différent du sien,
     * et deux rôles supplémentaires tirés au hasard. Les 4 rôles sont distincts, et au moins 2
     * d'entre eux sont des rôles du camp Village (Cupidon/Enfant Sauvage inclus, comme partout
     * ailleurs dans le code - voir compterVivants(Camp.VILLAGE)). Voir tirerRolesExpose() pour le
     * détail de la pioche.
     */
    private void declencherExpose(List<GamePlayer> vivants) {
        GamePlayer cible = vivants.get(random.nextInt(vivants.size()));
        List<RoleType> roles = tirerRolesExpose(cible, vivants);
        diffuser("&d&l✦ EXPOSED &d: &f" + cible.getNom() + " &7pourrait être... " + formaterRoles(roles));
    }

    /**
     * Construit la liste des 4 rôles affichés par declencherExpose(). Piochés en priorité parmi
     * les rôles actuellement détenus par des joueurs vivants (même principe que l'annonce de
     * sanction du vote : "4 rôles tirés au sort parmi les vivants"), pour rester cohérent avec ce
     * que les autres joueurs peuvent réellement observer en jeu. Si ce pool est trop restreint
     * pour garantir les contraintes ci-dessous (très peu de joueurs restants en fin de partie), il
     * est élargi à toute la composition utilisée cette partie (compositionUtilisee).
     */
    private List<RoleType> tirerRolesExpose(GamePlayer cible, List<GamePlayer> vivants) {
        List<RoleType> pool = vivants.stream().map(GamePlayer::getRole).distinct().collect(Collectors.toList());
        if (pool.size() < 4) {
            for (RoleType type : compositionUtilisee) {
                if (!pool.contains(type)) {
                    pool.add(type);
                }
            }
        }

        List<RoleType> selection = new ArrayList<>();
        RoleType roleCible = cible.getRole();
        selection.add(roleCible);

        // Un rôle d'un camp différent du sien.
        List<RoleType> autreCamp = pool.stream()
                .filter(t -> t.getCampDepart() != roleCible.getCampDepart() && !selection.contains(t))
                .collect(Collectors.toList());
        java.util.Collections.shuffle(autreCamp, random);
        if (!autreCamp.isEmpty()) {
            selection.add(autreCamp.get(0));
        }

        // Complète jusqu'à 4 rôles, en garantissant au moins 2 Villageois au total : d'abord les
        // slots manquants avec des rôles Village (si dispo), puis le reste au hasard.
        long villageoisActuels = selection.stream().filter(t -> t.getCampDepart() == Camp.VILLAGE).count();
        int villageoisManquants = (int) Math.max(0, 2 - villageoisActuels);

        List<RoleType> restant = new ArrayList<>(pool);
        restant.removeAll(selection);
        java.util.Collections.shuffle(restant, random);

        for (RoleType type : restant) {
            if (selection.size() >= 4) {
                break;
            }
            if (villageoisManquants > 0 && type.getCampDepart() == Camp.VILLAGE) {
                selection.add(type);
                villageoisManquants--;
            }
        }
        for (RoleType type : restant) {
            if (selection.size() >= 4) {
                break;
            }
            if (!selection.contains(type)) {
                selection.add(type);
            }
        }
        return selection;
    }

    private String formaterRoles(List<RoleType> roles) {
        return roles.stream().map(RoleType::getNomFormate).collect(Collectors.joining("&7, "));
    }

    /**
     * Événement "Exposed Inversé" : un nombre réglable de joueurs vivants tirés au sort (sans
     * doublon, voir EvenementAleatoireManager#getExposeInverseJoueursMinimum(), 5 par défaut) sont
     * tous affichés dans le chat général à côté du MÊME rôle - un rôle réellement détenu par l'un
     * d'entre eux (l'unique "vrai", les autres servent de leurre). Contrairement à Exposed, le
     * rôle affiché n'est donc jamais un pur mensonge : il correspond toujours à au moins un des
     * joueurs montrés, ce qui laisse une vraie piste à exploiter (juste noyée dans le bruit).
     * Nécessite au moins ce même nombre de joueurs vivants (vérifié par l'appelant,
     * declencherEvenementAleatoire()) : c'est ce qui garantit qu'on peut toujours en tirer autant
     * sans doublon ci-dessous.
     */
    private void declencherExposeInverse(List<GamePlayer> vivants) {
        int nombreJoueurs = plugin.getEvenementAleatoireManager().getExposeInverseJoueursMinimum();
        List<GamePlayer> tires = new ArrayList<>(vivants);
        java.util.Collections.shuffle(tires, random);
        List<GamePlayer> cibles = new ArrayList<>(tires.subList(0, Math.min(nombreJoueurs, tires.size())));

        GamePlayer porteur = cibles.get(random.nextInt(cibles.size()));
        RoleType roleAffiche = porteur.getRole();

        String noms = cibles.stream().map(GamePlayer::getNom).collect(Collectors.joining("&7, &f"));
        diffuser("&d&l✦ EXPOSED INVERSÉ &d: &fl'un de ces joueurs est " + roleAffiche.getNomFormate()
                + "&7 : &f" + noms);
    }

    // ================= Événement aléatoire : Rumeurs =================

    /**
     * Événement "Rumeurs" (déclenché à l'horaire tiré dans veritableDebutPartie(), voir
     * tickMinuteriesAutomatiques()) : contrairement à Exposed/Exposé Inversé (annonce immédiate),
     * celui-ci ouvre une fenêtre de EvenementAleatoireManager.RUMEURS_DUREE_COLLECTE_SECONDES
     * secondes pendant laquelle chaque message envoyé dans le chat général par un joueur de la
     * partie est intercepté par RumeursListener (jamais diffusé tel quel, jamais attribué - voir
     * enregistrerMessageRumeur()) puis, la fenêtre passée, réaffiché par cloturerRumeurs()
     * anonymement et dans un ordre mélangé.
     */
    private void declencherRumeurs() {
        if (!estEnCours()) {
            return;
        }
        joueursAyantEnvoyeRumeur.clear();
        messagesRumeursCollectes.clear();
        collecteRumeursActive = true;
        int dureeSecondes = EvenementAleatoireManager.RUMEURS_DUREE_COLLECTE_SECONDES;
        diffuser("&d&l✦ RUMEURS &d: &fVous avez " + dureeSecondes + " secondes pour envoyer un message dans le chat...");
        Bukkit.getScheduler().runTaskLater(plugin, this::cloturerRumeurs, dureeSecondes * 20L);
    }

    /**
     * Fin de la fenêtre de collecte de Rumeurs : coupe l'interception (collecteRumeursActive passe
     * à false, donc RumeursListener laisse à nouveau passer le chat normalement), puis diffuse les
     * messages collectés anonymement et dans un ordre mélangé (Collections.shuffle) - jamais dans
     * l'ordre d'envoi, pour ne pas laisser deviner qui a parlé en premier/dernier.
     */
    private void cloturerRumeurs() {
        collecteRumeursActive = false;
        List<String> messages = new ArrayList<>(messagesRumeursCollectes);
        messagesRumeursCollectes.clear();
        joueursAyantEnvoyeRumeur.clear();
        if (!estEnCours()) {
            return;
        }
        if (messages.isEmpty()) {
            diffuser("&d&l✦ RUMEURS &d: &7Personne n'a envoyé de message...");
            return;
        }
        java.util.Collections.shuffle(messages, random);
        diffuser("&d&l✦ RUMEURS &d: &7Voici les messages reçus, anonymement et dans le désordre :");
        for (String message : messages) {
            diffuser("&d  » &f" + message);
        }
    }

    /** Vrai pendant la fenêtre de collecte de l'événement Rumeurs - consulté par RumeursListener pour savoir s'il doit intercepter le chat général. */
    public boolean isCollecteRumeursActive() {
        return collecteRumeursActive;
    }

    /**
     * Enregistre le message d'un joueur pour l'annonce anonyme de fin de fenêtre Rumeurs. Appelée
     * par RumeursListener (sur le thread principal, voir sa doc - AsyncPlayerChatEvent est
     * asynchrone). Ignoré si la fenêtre de collecte n'est plus active (message arrivé trop tard) ou
     * si ce joueur a déjà un message enregistré cette fenêtre-ci (un seul message pris en compte
     * par joueur, comme annoncé : "vous avez 20 secondes pour envoyer UN message").
     */
    public void enregistrerMessageRumeur(UUID uuid, String message) {
        if (!collecteRumeursActive) {
            return;
        }
        if (!joueursAyantEnvoyeRumeur.add(uuid)) {
            return;
        }
        messagesRumeursCollectes.add(assainirMessageRumeur(message));
    }

    /** Retire les codes couleur ('&'/'§') du message d'un joueur avant de le remettre dans le chat via diffuser() : sans ça, un joueur pourrait injecter n'importe quel formatage (voire se faire deviner via une couleur trop reconnaissable) dans l'annonce censée être anonyme. */
    private String assainirMessageRumeur(String message) {
        return message.replace('§', ' ').replace('&', ' ').trim();
    }

    /**
     * Détermine le centre de la partie (téléportation de départ + centre de
     * la bordure). Si `monde.centre-x` / `monde.centre-z` sont définis dans
     * config.yml, ils sont prioritaires (pratique pour caler précisément le
     * centre sur une carte custom). Sinon, on retombe sur le spawn du monde.
     */
    private Location trouverBonSpawn(World monde) {
        if (plugin.getConfig().contains("monde.centre-x") && plugin.getConfig().contains("monde.centre-z")) {
            int x = plugin.getConfig().getInt("monde.centre-x");
            int z = plugin.getConfig().getInt("monde.centre-z");
            int y = monde.getHighestBlockYAt(x, z) + 1;
            return new Location(monde, x + 0.5, y, z + 0.5);
        }
        return monde.getSpawnLocation();
    }

    private Location emplacementAleatoireDansRayon(World monde, Location centre, double rayon) {
        double angle = random.nextDouble() * Math.PI * 2;
        double distance = random.nextDouble() * rayon;
        double x = centre.getX() + Math.cos(angle) * distance;
        double z = centre.getZ() + Math.sin(angle) * distance;
        // (int) tronque vers zéro, pas vers le bas : pour x/z négatifs (la moitié des cas,
        // le rayon de téléportation s'étendant dans toutes les directions autour du centre),
        // ça interrogeait la colonne de bloc VOISINE au lieu de la bonne, avec une hauteur de
        // terrain potentiellement différente (trou, ravin...) => joueur posé en l'air, sans
        // sol sous les pieds, qui tombe pendant que le gel de préparation le fige en boucle.
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int y = monde.getHighestBlockYAt(bx, bz) + 1;
        return new Location(monde, x, y, z);
    }

    /**
     * Comme emplacementAleatoireDansRayon ci-dessus, mais la distance est tirée dans
     * [distanceMin, distanceMax] (couronne) au lieu de [0, rayon] (disque plein). Utilisé
     * quand monde.distance-spawn-min > 0 (voir demarrer()) pour forcer un espacement minimum
     * entre le centre de la carte et les joueurs au lancement de la partie.
     */
    private Location emplacementAleatoireEnCouronne(World monde, Location centre, double distanceMin, double distanceMax) {
        double angle = random.nextDouble() * Math.PI * 2;
        double distance = distanceMin + random.nextDouble() * (distanceMax - distanceMin);
        double x = centre.getX() + Math.cos(angle) * distance;
        double z = centre.getZ() + Math.sin(angle) * distance;
        // Même précaution que emplacementAleatoireDansRayon : Math.floor() et non (int), pour ne
        // pas interroger la mauvaise colonne de bloc sur des coordonnées négatives.
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int y = monde.getHighestBlockYAt(bx, bz) + 1;
        return new Location(monde, x, y, z);
    }

    /**
     * Position aléatoire à l'intérieur de la WorldBorder ACTUELLE du monde
     * (taille réelle au moment de l'appel, y compris en plein resserrement).
     * Utilisé pour la réapparition de l'Ancien / de l'Idiot du Village.
     */
    public Location emplacementAleatoireDansBordure(World monde) {
        org.bukkit.WorldBorder bordure = monde.getWorldBorder();
        Location centre = bordure.getCenter();
        double demiTaille = bordure.getSize() / 2.0;
        double x = centre.getX() + (random.nextDouble() * 2.0 - 1.0) * demiTaille;
        double z = centre.getZ() + (random.nextDouble() * 2.0 - 1.0) * demiTaille;
        // Même bug de troncature que emplacementAleatoireDansRayon() ci-dessus, voir son
        // commentaire : (int) au lieu de Math.floor() sur des coordonnées négatives interroge
        // la mauvaise colonne de bloc et peut poser le joueur au-dessus du vide.
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int y = monde.getHighestBlockYAt(bx, bz) + 1;
        return new Location(monde, x, y, z);
    }

    /** Public : aussi utilisé par LGCommand pour réanimer un joueur DANS le monde de jeu (voir
     * emplacementAleatoireAutourDuZero ci-dessous), alors que ce joueur patiente au lobby depuis
     * sa mort apparente et que son Player#getWorld() actuel pointe donc sur le monde lobby. */
    public World getMondeJeu() {
        return Bukkit.getWorld(plugin.getConfig().getString("monde.nom", "world"));
    }

    /**
     * Position aléatoire dans le monde de jeu, à 300 blocs maximum des coordonnées GLOBALES
     * (0, 0) — et non du centre de la bordure ni de centreMonde (qui peuvent être décalés sur
     * une carte personnalisée, voir demarrer()). Utilisée pour faire réapparaître un joueur
     * réanimé (Sorcière / Infect Père des Loups) après son passage au lobby pendant la fenêtre
     * d'attente, plutôt que de le renvoyer n'importe où dans toute la bordure (potentiellement
     * très loin de tout le monde).
     */
    public Location emplacementAleatoireAutourDuZero(World monde) {
        return emplacementAleatoireDansRayon(monde, new Location(monde, 0, 0, 0), 300.0);
    }

    // ================= Déroulement des épisodes =================
// ici caca 1
    private void finDePremierEpisode() {
        if (phase == GamePhase.TERMINEE) {
            return;
        }
        attribuerRoles();
        episodeActuel = 2;
        diffuser("&d&l✦ Les rôles ont été attribués ! Consultez vos messages privés. Le PvP est désormais activé.");
        for (GamePlayer gp : joueurs.values()) {
            appelerOnEpisodeStart(gp, episodeActuel);
        }
        demarrerJour();
    }

    /**
     * Attribue un rôle (et l'Aura correspondante) à un joueur, et déclenche son onAssign.
     * Réutilisé par l'attribution normale et par /lg admin role. N'envoie plus de message ici :
     * l'attribution normale enchaîne juste après sur appelerOnEpisodeStart(gp, 2), qui envoie la
     * carte complète (rôle/objectif/aura/description) ; /lg admin role l'envoie lui-même via
     * envoyerCarteRole() pour couvrir le cas où l'épisode a déjà commencé.
     */
    public void assignerRoleA(GamePlayer gp, RoleType type) {
        gp.setRole(type);
        gp.setAura(type.getAura());
        Role role = plugin.getRoleRegistry().get(type);
        if (role != null) {
            role.onAssign(plugin, gp);
        }
    }

    private void attribuerRoles() {
        List<GamePlayer> vivants = getJoueursVivants();
        List<RoleType> roles = plugin.getCompositionManager().construireListeRoles(vivants.size());
        this.compositionUtilisee = new ArrayList<>(roles);

        for (int i = 0; i < vivants.size(); i++) {
            GamePlayer gp = vivants.get(i);
            RoleType type = i < roles.size() ? roles.get(i) : RoleType.LOUP_GAROU;
            assignerRoleA(gp, type);
        }

        // Appariement des Sœurs
        List<GamePlayer> soeurs = vivants.stream().filter(g -> g.getRole() == RoleType.SOEURS).collect(Collectors.toList());
        for (int i = 0; i + 1 < soeurs.size(); i += 2) {
            GamePlayer a = soeurs.get(i);
            GamePlayer b = soeurs.get(i + 1);
            a.setEtat("soeur_partenaire", b.getUuid());
            b.setEtat("soeur_partenaire", a.getUuid());
            Msg.envoyer(a.getPlayer(), "&dVotre Sœur est &f" + b.getNom() + "&d.");
            Msg.envoyer(b.getPlayer(), "&dVotre Sœur est &f" + a.getNom() + "&d.");
        }

        recalculerGroupes();
    }

    private void appelerOnEpisodeStart(GamePlayer gp, int episode) {
        if (!gp.isVivant() || gp.getRole() == null) {
            return;
        }
        // La carte (rôle/objectif/Aura/description) ne doit s'afficher dans le chat qu'une seule
        // fois, à l'épisode 2 (l'attribution initiale des rôles) : pas à chaque début d'épisode
        // suivant. En revanche role.onEpisodeStart(...) ci-dessous doit, lui, continuer de
        // s'exécuter à CHAQUE épisode (ex: le grognement du Montreur d'Ours doit avoir lieu à
        // chaque épisode, pas seulement au 2ème).
        if (episode == 2) {
            envoyerCarteRole(gp, episode);
        }
        Role role = plugin.getRoleRegistry().get(gp.getRole());
        if (role != null) {
            role.onEpisodeStart(plugin, gp, episode);
        }
    }

    /** Envoie au joueur la carte d'annonce/rappel de son rôle (rôle, objectif, Aura, description), une ligne par message. */
    private void envoyerCarteRole(GamePlayer gp, int episode) {
        Player p = gp.getPlayer();
        if (p == null || gp.getRole() == null) {
            return;
        }
        for (String ligne : gp.getRole().getCarteAnnonce(episode)) {
            Msg.envoyer(p, ligne);
        }
    }

    private long dureePhaseTicks() {
        int dureeEpisode = plugin.getConfig().getInt("episodes.duree-minutes", 20);
        int cycles = Math.max(1, plugin.getConfig().getInt("episodes.cycles-par-episode", 2));
        double minutesParPhase = (double) dureeEpisode / cycles / 2.0;
        return Math.round(minutesParPhase * 60.0 * 20.0);
    }

    private void demarrerJour() {
        if (phase == GamePhase.TERMINEE) {
            return;
        }
        phase = GamePhase.JOUR;
        ticksEcoulesDansPhase = 0L;
        World mondeCycle = getMondeJeu();
        if (mondeCycle != null) {
            // Heure posée tout de suite (aube) : on ne dépend plus du prochain passage du minuteur
            // (jusqu'à 1s de délai), ce qui évite qu'un jour démarre visuellement en pleine nuit.
            mondeCycle.setTime(0L);
        }
        String libellePeriode = (periodeDansEpisode / 2) + 1 == 1 ? "1er" : "2ème";
        diffuser("&e&l☀ Le jour (" + libellePeriode + ") se lève sur l'épisode " + episodeActuel + ".");
        appliquerEffetsPeriodiques();
        planifierMiseAJourScoreboard();

        // Le vote ne se déclenche qu'une fois par épisode, au tout premier jour de celui-ci.
        if (periodeDansEpisode == 0 && plugin.getVoteManager().isActif() && episodeActuel >= plugin.getVoteManager().getEpisodePremierVote()) {
            plugin.getVoteManager().demarrerVote(plugin);
        }

        long duree = dureePhaseTicks();
        // finPhaseTimestamp (horloge réelle) est la seule source de vérité pour la transition
        // suivante : voir tickMinuteriesAutomatiques(), appelée 1x/seconde. Plus de
        // runTaskLater(ticks) ici (dérivait sous lag, voir le commentaire dans demarrer()).
        this.finPhaseTimestamp = System.currentTimeMillis() + (duree * 50L);
    }

    /**
     * Fin d'une période de Jour : comptabilise la période dans l'épisode. Avant ce correctif,
     * seule la fin de Nuit incrémentait periodeDansEpisode, donc le seuil de 4 (2 Jours + 2
     * Nuits) n'était atteint qu'après 4 NUITS au lieu de 2 : un épisode durait deux fois plus
     * longtemps que prévu (40 min au lieu de 20 avec la config par défaut) avant de passer au
     * suivant, même si chaque Jour/Nuit individuel durait bien les 5 minutes configurées.
     */
    private void finDeJour() {
        // Garde stricte sur JOUR (et non plus seulement "pas TERMINEE") : si cette méthode est
        // appelée une seconde fois par erreur (tâche fantôme non annulée, double planification...)
        // alors qu'on est déjà en NUIT, elle ne doit RIEN faire plutôt que de re-déclencher une
        // transition et re-incrémenter periodeDansEpisode, ce qui raccourcirait la phase suivante.
        if (phase != GamePhase.JOUR) {
            return;
        }
        plugin.getLogger().info("[Cycle] finDeJour() déclenché (periodeDansEpisode=" + periodeDansEpisode + ").");
        periodeDansEpisode++;
        demarrerNuit();
    }

    private void demarrerNuit() {
        if (phase == GamePhase.TERMINEE) {
            return;
        }
        phase = GamePhase.NUIT;
        numeroNuit++;
        ticksEcoulesDansPhase = 0L;
        World mondeCycle = getMondeJeu();
        if (mondeCycle != null) {
            // Idem que demarrerJour() : heure posée tout de suite (tombée de nuit), sans attendre
            // le prochain passage du minuteur.
            mondeCycle.setTime(13000L);
        }
        diffuser("&9&l☾ La nuit tombe sur l'épisode " + episodeActuel + ". Les Loups-Garous s'éveillent...");
        appliquerEffetsPeriodiques();
        planifierMiseAJourScoreboard();

        long duree = dureePhaseTicks();
        // Voir le commentaire équivalent dans demarrerJour().
        this.finPhaseTimestamp = System.currentTimeMillis() + (duree * 50L);
    }

    private void finDeNuit() {
        // Voir le commentaire équivalent dans finDeJour().
        if (phase != GamePhase.NUIT) {
            return;
        }
        plugin.getLogger().info("[Cycle] finDeNuit() déclenché (periodeDansEpisode=" + periodeDansEpisode + ").");
        periodeDansEpisode++;
        if (periodeDansEpisode >= 4) {
            periodeDansEpisode = 0;
            episodeActuel++;
            for (GamePlayer gp : getJoueursVivants()) {
                appelerOnEpisodeStart(gp, episodeActuel);
            }
            recalculerGroupes();
            if (blocagePouvoirsVillageJusquaEpisode >= 0 && episodeActuel > blocagePouvoirsVillageJusquaEpisode) {
                blocagePouvoirsVillageJusquaEpisode = -1;
                diffuser("&7Les pouvoirs du Village sont de nouveau actifs.");
            }
        }
        demarrerJour();
    }

    // ================= Cycle jour/nuit visuel (heure Minecraft synchronisée) =================

    /**
     * Fait avancer l'heure du monde en douceur pour correspondre à la phase actuelle (pas de saut
     * brutal). Appelée 1x/seconde (20 ticks) par le minuteur de LGUHCPlugin#onEnable : on avance
     * donc notre propre compteur ticksEcoulesDansPhase de 20 à chaque appel plutôt que de lire
     * World#getFullTime(). Ce compteur reste calé sur le rythme réel du serveur (runTaskTimer
     * ralentit avec le lag, donc ticksEcoulesDansPhase aussi) sans dépendre de doDaylightCycle,
     * contrairement à getFullTime() qui n'avance QUE via nos propres appels à setTime() une fois
     * ce gamerule désactivé (voir demarrer()) - ce qui figeait le soleil et la lune pour de bon.
     */
    public void tickCycleJourNuit() {
        World monde = getMondeJeu();
        if (monde == null) {
            return;
        }
        if (phase == GamePhase.EPISODE_1) {
            // Aucun rôle actif pendant la préparation, mais le soleil ne doit pas rester figé pour
            // autant : on avance le temps du monde nous-mêmes (doDaylightCycle est désactivé, voir
            // demarrer()) au rythme normal du vanilla, 20 ticks par seconde réelle. Comme cette
            // méthode est appelée 1x/seconde, ça reconstitue exactement un cycle jour/nuit vanilla
            // classique (24000 ticks) sur la durée par défaut de l'épisode 1 (20 minutes).
            monde.setFullTime(monde.getFullTime() + 20L);
            return;
        }
        if (phase != GamePhase.JOUR && phase != GamePhase.NUIT) {
            return;
        }
        ticksEcoulesDansPhase += 20L;
        long dureeTotaleTicks = dureePhaseTicks();
        double fraction = dureeTotaleTicks > 0 ? Math.min(1.0, (double) ticksEcoulesDansPhase / (double) dureeTotaleTicks) : 0.0;

        long debut;
        long fin;
        if (phase == GamePhase.JOUR) {
            debut = 0L;
            fin = 11500L; // aube -> juste avant le coucher du soleil
        } else {
            debut = 13000L;
            fin = 23000L; // tombée de la nuit -> juste avant l'aube
        }
        long tempsCible = debut + Math.round((fin - debut) * fraction);
        monde.setTime(tempsCible);
    }

    public void diffuserAuxLoups(String message) {
        for (GamePlayer gp : getJoueursVivants()) {
            if (gp.getCamp() == Camp.LOUPS) {
                Msg.envoyer(gp.getPlayer(), message);
            }
        }
    }

    public int getNumeroNuit() {
        return numeroNuit;
    }

    // ================= Hurlements =================

    private int nombreHurlements = 0;

    /**
     * Incrémente le compteur global de hurlements de la partie (tous Loups
     * confondus, chacun ne pouvant hurler qu'une fois) et retourne le numéro
     * de CE hurlement (1 pour le tout premier, 2 pour le suivant, etc.).
     * Sert à faire grandir la fenêtre de détection de la Petite Fille.
     */
    public int incrementerHurlements() {
        return ++nombreHurlements;
    }

    // ================= Liste des alliés Loups =================

    /** Déclenchée automatiquement 45 minutes après le lancement de la partie (voir {@link #demarrer}). */
    private void revelerListeLoups() {
        if (phase == GamePhase.TERMINEE || listeLoupsRevelee) {
            return;
        }
        listeLoupsRevelee = true;
        for (GamePlayer loup : getJoueursVivants()) {
            if (loup.getCamp() == Camp.LOUPS) {
                Msg.envoyer(loup.getPlayer(), "&4&l🐺 La meute se révèle à vous...");
                envoyerListeAlliesLoup(loup);
            }
        }
    }

    /**
     * Envoie à ce joueur (doit être vivant et du camp des Loups) la liste à
     * jour de ses alliés vivants dans la meute. Utilisée par la révélation
     * automatique à 45 minutes, par /lg role, et lors de l'arrivée d'un
     * nouvel allié (si la liste a déjà été révélée).
     */
    public void envoyerListeAlliesLoup(GamePlayer gp) {
        if (gp.getPlayer() == null) {
            return;
        }
        // Loup-Garou Amnésique tant qu'il n'est pas "réveillé" (voir estAmnesiqueCache) : il ne
        // reçoit PAS la liste générique de la meute (il ne se souvient de rien), mais sa propre
        // liste construite par proximité (voir tickLoupGarouAmnesique()).
        if (estAmnesiqueCache(gp)) {
            envoyerListePersonnelleAmnesique(gp);
            return;
        }
        List<GamePlayer> allies = getJoueursVivants().stream()
                // Un Amnésique encore caché n'apparaît PAS dans la liste des AUTRES Loups non plus.
                .filter(g -> g.getCamp() == Camp.LOUPS && g != gp && !estAmnesiqueCache(g))
                .collect(Collectors.toList());
        if (allies.isEmpty()) {
            Msg.envoyer(gp.getPlayer(), "&cAucun autre allié Loup-Garou n'est actuellement en vie.");
            return;
        }
        String liste = allies.stream().map(GamePlayer::getNom).collect(Collectors.joining("&7, &f"));
        Msg.envoyer(gp.getPlayer(), "&cVos alliés Loups-Garous vivants : &f" + liste);
    }

    // ================= Loup-Garou Amnésique =================

    /**
     * Vrai si `gp` est le Loup-Garou Amnésique et que son réveil individuel (70-90 min, tiré au
     * sort une fois à l'attribution - voir LoupGarouAmnesiqueRole#onAssign) n'a pas encore sonné.
     * Tant que c'est vrai : il n'apparaît dans AUCUNE liste d'alliés Loups affichée aux autres
     * (ni la révélation automatique, ni un /lg role d'un allié), et lui-même ne reçoit que sa
     * propre liste construite par proximité plutôt que la liste complète de la meute.
     */
    private boolean estAmnesiqueCache(GamePlayer gp) {
        return gp.getRole() == RoleType.LOUP_GAROU_AMNESIQUE && !gp.getEtat("amnesique_revele", false);
    }

    /** Envoie à l'Amnésique caché la liste (limitée) des Loups qu'il a reconnus par proximité jusqu'ici, lui inclus. */
    private void envoyerListePersonnelleAmnesique(GamePlayer gp) {
        Set<UUID> connus = gp.getEtat("amnesique_connus", null);
        StringBuilder liste = new StringBuilder("&f").append(gp.getNom());
        if (connus != null) {
            for (UUID uuid : connus) {
                GamePlayer autre = getGamePlayer(uuid);
                if (autre != null) {
                    liste.append("&7, &f").append(autre.getNom());
                }
            }
        }
        Msg.envoyer(gp.getPlayer(), "&4🐺 Votre mémoire est floue... vous ne reconnaissez actuellement que : " + liste);
    }

    /**
     * A appeler 1x/seconde (voir LGUHCPlugin) pendant toute la partie. Fait avancer les deux
     * mécaniques individuelles du Loup-Garou Amnésique :
     *  1. Son réveil (une fois l'instant tiré au sort atteint) : il rejoint alors la liste
     *     visible de la meute, et le reste des Loups en est informé comme pour tout nouvel
     *     arrivant (voir annoncerNouvelAllieLoup()).
     *  2. Tant qu'il n'est pas réveillé : la découverte par proximité (moins de 10 blocs d'un
     *     autre Loup-Garou vivant) qui alimente sa propre liste, consultable via /lg role
     *     (voir LGCommand#afficherRole, qui appelle envoyerListeAlliesLoup même hors révélation
     *     générale pour ce cas précis).
     */
    public void tickLoupGarouAmnesique() {
        if (!estEnCours()) {
            return;
        }
        long ecouleSecondes = getTempsTotalEcouleSecondes();
        List<GamePlayer> vivants = getJoueursVivants();
        for (GamePlayer amnesique : vivants) {
            if (amnesique.getRole() != RoleType.LOUP_GAROU_AMNESIQUE || amnesique.getPlayer() == null) {
                continue;
            }
            if (!amnesique.getEtat("amnesique_revele", false)) {
                long instantReveil = amnesique.getEtat("amnesique_instant_reveil_secondes", Long.MAX_VALUE);
                if (ecouleSecondes >= instantReveil) {
                    amnesique.setEtat("amnesique_revele", true);
                    Msg.envoyer(amnesique.getPlayer(), "&4&l🐺 Un voile se lève dans votre esprit... vous vous souvenez : vous êtes un Loup-Garou !");
                    annoncerNouvelAllieLoup(amnesique, "se souvient soudain qu'il est des vôtres");
                } else {
                    Set<UUID> connus = amnesique.getEtat("amnesique_connus", null);
                    if (connus == null) {
                        connus = new HashSet<>();
                    }
                    boolean nouveauteTrouvee = false;
                    for (GamePlayer autreLoup : vivants) {
                        if (autreLoup == amnesique || autreLoup.getCamp() != Camp.LOUPS || autreLoup.getPlayer() == null) {
                            continue;
                        }
                        if (connus.contains(autreLoup.getUuid())) {
                            continue;
                        }
                        if (distanceSure(amnesique.getPlayer(), autreLoup.getPlayer()) <= 10.0) {
                            connus.add(autreLoup.getUuid());
                            nouveauteTrouvee = true;
                            Msg.envoyer(amnesique.getPlayer(), "&4🐺 Une vague de mémoire... vous reconnaissez &f"
                                    + autreLoup.getNom() + " &4comme l'un des vôtres !");
                        }
                    }
                    if (nouveauteTrouvee) {
                        amnesique.setEtat("amnesique_connus", connus);
                    }
                }
            }
        }
    }

    public boolean isListeLoupsRevelee() {
        return listeLoupsRevelee;
    }

    /** Pour /lg admin listelgskip : force immédiatement la révélation sans attendre les 45 minutes. */
    public boolean forcerRevelationListeLoups(CommandSender demandeur) {
        if (listeLoupsRevelee) {
            demandeur.sendMessage(Msg.c("&eLa liste des alliés Loups était déjà révélée."));
            return false;
        }
        revelerListeLoups();
        demandeur.sendMessage(Msg.c("&a[Admin] Révélation de la liste des alliés Loups forcée."));
        return true;
    }

    /**
     * À appeler chaque fois qu'un joueur rejoint le camp des Loups en cours
     * de partie (infection par l'Infect Père des Loups, transformation de
     * l'Enfant Sauvage, etc.). Prévient discrètement le reste de la meute de
     * l'arrivée du nouvel allié, et donne au nouvel arrivant la liste à jour
     * de ses alliés si elle a déjà été révélée (sinon il la recevra comme
     * tout le monde au bout de 45 minutes).
     *
     * @param nouveauLoup   le joueur qui vient de rejoindre le camp des Loups
     * @param raisonArrivee courte précision affichée entre parenthèses (ex : "Enfant Sauvage transformé"), peut être null
     */
    public void annoncerNouvelAllieLoup(GamePlayer nouveauLoup, String raisonArrivee) {
        String suffixe = (raisonArrivee != null && !raisonArrivee.isEmpty()) ? " &8(" + raisonArrivee + ")" : "";
        for (GamePlayer loup : getJoueursVivants()) {
            // Un Amnésique encore caché à lui-même (voir estAmnesiqueCache) ne sait pas qu'il fait
            // partie de la meute : il ne reçoit pas non plus les annonces d'arrivée des autres.
            if (loup.getCamp() == Camp.LOUPS && loup != nouveauLoup && loup.getPlayer() != null && !estAmnesiqueCache(loup)) {
                Msg.envoyer(loup.getPlayer(), "&4🐺 &c" + nouveauLoup.getNom() + " rejoint la meute !" + suffixe);
            }
        }
        if (listeLoupsRevelee) {
            envoyerListeAlliesLoup(nouveauLoup);
        }
    }

    // ================= Effets périodiques =================

    private void appliquerEffetsPeriodiques() {
        boolean nuit = phase == GamePhase.NUIT;
        for (GamePlayer gp : getJoueursVivants()) {
            Player p = gp.getPlayer();
            if (p == null) {
                continue;
            }
            boolean estLoup = gp.getCamp() == Camp.LOUPS;

            if (estLoup && nuit && gp.getRole() != RoleType.LOUP_PERFIDE) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, dureePhaseTicksInt(), 0, false, false));
                // Le bonus de Force des Loups est désormais géré uniquement par le calcul manuel
                // de dégâts dans UHCRulesListener#surCombatGeneral (voir beneficeForceDemiNiveau) :
                // ne plus donner ici le vrai PotionEffect Force I, sous peine de cumuler les deux.
            }
            // Loup-Garou Amnésique : lui EN PLUS reçoit un vrai PotionEffect Force I la nuit (visible
            // dans son inventaire), demandé explicitement pour ce rôle - à la place du bonus
            // "demi-niveau" générique des autres Loups (voir UHCRulesListener#beneficeForceDemiNiveau,
            // qui l'exclut désormais explicitement pour ne pas cumuler les deux).
            if (gp.getRole() == RoleType.LOUP_GAROU_AMNESIQUE && nuit) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, dureePhaseTicksInt(), 0, false, false));
            }
            // Assassin : idem, son bonus de Force le jour est géré par UHCRulesListener#surCombatGeneral.
            if (gp.getRole() == RoleType.RENARD) {
                p.setWalkSpeed(nuit ? RenardRole.VITESSE_MARCHE_RENARD_NUIT : RenardRole.VITESSE_MARCHE_NORMALE);
            }
            // Ancien : son bonus de Résistance (quart de niveau, permanent tant qu'il est vivant)
            // est géré par UHCRulesListener#surCombatGeneral. L'ancien PotionEffect ci-dessus,
            // tiré au sort une fois par phase, est resté par erreur après ce refactor : il cumulait
            // avec le calcul manuel et rendait sa résistance intermittente (visible une phase sur deux
            // en moyenne) au lieu de permanente. Retiré.
        }
    }

    private int dureePhaseTicksInt() {
        long t = dureePhaseTicks() + 100;
        return (int) Math.min(Integer.MAX_VALUE, t);
    }

    // ================= Corruption (Infect Père des Loups) =================

    public void tickCorruption() {
        if (phase != GamePhase.JOUR && phase != GamePhase.NUIT) {
            return;
        }
        List<GamePlayer> vivants = getJoueursVivants();
        GamePlayer infectPere = vivants.stream().filter(g -> g.getRole() == RoleType.INFECT_PERE_LOUPS).findFirst().orElse(null);
        if (infectPere == null || infectPere.getPlayer() == null) {
            return;
        }
        for (GamePlayer cible : vivants) {
            if (cible.getCamp() == Camp.LOUPS || cible.getPlayer() == null) {
                continue;
            }
            double distanceInfect = distanceSure(infectPere.getPlayer(), cible.getPlayer());
            if (distanceInfect <= 15.0) {
                cible.ajouterCorruption(0.2);
                continue;
            }
            for (GamePlayer loup : vivants) {
                if (loup.getCamp() != Camp.LOUPS || loup.getPlayer() == null || loup == infectPere) {
                    continue;
                }
                if (distanceSure(loup.getPlayer(), cible.getPlayer()) <= 15.0) {
                    cible.ajouterCorruption(0.05);
                    break;
                }
            }
        }
    }

    private double distanceSure(Player a, Player b) {
        if (!a.getWorld().equals(b.getWorld())) {
            return Double.MAX_VALUE;
        }
        return a.getLocation().distance(b.getLocation());
    }

    // ================= Boussole traqueuse =================

    public void tickBoussole() {
        if (!plugin.getConfig().getBoolean("survie-uhc.boussole-traqueuse", true)) {
            return;
        }
        if (phase != GamePhase.JOUR && phase != GamePhase.NUIT) {
            return;
        }
        List<GamePlayer> vivants = getJoueursVivants();
        for (GamePlayer gp : vivants) {
            Player p = gp.getPlayer();
            if (p == null) {
                continue;
            }
            GamePlayer plusProche = null;
            double meilleureDistance = Double.MAX_VALUE;
            for (GamePlayer autre : vivants) {
                if (autre == gp || autre.getCamp() == gp.getCamp() || autre.getPlayer() == null) {
                    continue;
                }
                double d = distanceSure(p, autre.getPlayer());
                if (d < meilleureDistance) {
                    meilleureDistance = d;
                    plusProche = autre;
                }
            }
            if (plusProche != null) {
                p.setCompassTarget(plusProche.getPlayer().getLocation());
            }
        }
    }

    /**
     * Boussole de l'amour du Couple : DOIT être appelée juste après tickBoussole() dans le même
     * tick (voir LGUHCPlugin#onEnable) pour que sa cible (l'amoureux) prenne le dessus sur celle
     * de la boussole traqueuse générale (l'ennemi le plus proche), les deux utilisant la même
     * cible de boussole native du joueur.
     */
    public void tickBoussoleCouple() {
        if (phase != GamePhase.JOUR && phase != GamePhase.NUIT) {
            return;
        }
        plugin.getCoupleManager().tickBoussole(this);
    }

    // ================= Equipement (armure diamant, efficacité auto) =================

    public void tickEquipement() {
        if (!estEnCours()) {
            return;
        }
        for (GamePlayer gp : getJoueursVivants()) {
            Player p = gp.getPlayer();
            if (p == null) {
                continue;
            }
            limiterArmureDiamant(p);
            appliquerEfficaciteAuto(p);
        }
    }

    private void limiterArmureDiamant(Player p) {
        org.bukkit.inventory.ItemStack[] armure = p.getInventory().getArmorContents();
        int piecesDiamant = 0;
        for (org.bukkit.inventory.ItemStack piece : armure) {
            if (piece != null && piece.getType().name().startsWith("DIAMOND_")) {
                piecesDiamant++;
            }
        }
        if (piecesDiamant <= 2) {
            return;
        }
        for (int i = 0; i < armure.length; i++) {
            org.bukkit.inventory.ItemStack piece = armure[i];
            if (piece != null && piece.getType().name().startsWith("DIAMOND_") && piecesDiamant > 2) {
                armure[i] = null;

                java.util.Map<Integer, org.bukkit.inventory.ItemStack> reste = p.getInventory().addItem(piece);
                for (org.bukkit.inventory.ItemStack drop : reste.values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), drop);
                }

                piecesDiamant--;
            }
        }
        p.getInventory().setArmorContents(armure);
        p.updateInventory();
        Msg.envoyer(p, "&cVous ne pouvez pas porter plus de 2 pièces d'armure en diamant à la fois.");
    }

    private void appliquerEfficaciteAuto(Player p) {
        for (org.bukkit.inventory.ItemStack item : p.getInventory().getContents()) {
            if (item == null) {
                continue;
            }
            String nom = item.getType().name();
            boolean estOutil = nom.endsWith("_PICKAXE") || nom.endsWith("_AXE") || nom.endsWith("_SPADE") || nom.endsWith("_SHOVEL");
            if (estOutil && item.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.DIG_SPEED) < 4) {
                item.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.DIG_SPEED, 4);
            }
        }
    }

    // ================= Détection mutuelle des invisibles (Petite Fille / Loup Perfide) =================

    /**
     * La Petite Fille et le Loup-Garou Perfide sont les deux seuls rôles capables de
     * se rendre invisibles. Quand ils le sont TOUS LES DEUX en même temps, ils se
     * perçoivent l'un l'autre grâce à une particule envoyée uniquement à eux deux
     * (Player#playEffect ne l'affiche qu'au joueur ciblé, personne d'autre ne la voit).
     */
    public void tickInvisiblesMutuels() {
        if (!estEnCours()) {
            return;
        }
        List<GamePlayer> loupsPerfidesInvisibles = new ArrayList<>();
        List<GamePlayer> petitesFillesInvisibles = new ArrayList<>();
        for (GamePlayer gp : getJoueursVivants()) {
            if (gp.getPlayer() == null) {
                continue;
            }
            if (gp.getRole() == RoleType.LOUP_PERFIDE && gp.getEtat("perfide_invisible_actif", false)) {
                loupsPerfidesInvisibles.add(gp);
            } else if (gp.getRole() == RoleType.PETITE_FILLE && gp.getEtat("pf_invisible_actif", false)) {
                petitesFillesInvisibles.add(gp);
            }
        }
        if (loupsPerfidesInvisibles.isEmpty() || petitesFillesInvisibles.isEmpty()) {
            return;
        }
        for (GamePlayer loup : loupsPerfidesInvisibles) {
            Player joueurLoup = loup.getPlayer();
            for (GamePlayer fille : petitesFillesInvisibles) {
                Player joueurFille = fille.getPlayer();
                if (!joueurLoup.getWorld().equals(joueurFille.getWorld())) {
                    continue;
                }
                envoyerParticulesDetectionMutuelle(joueurLoup, joueurFille.getLocation().add(0, 1, 0));
                envoyerParticulesDetectionMutuelle(joueurFille, joueurLoup.getLocation().add(0, 1, 0));
            }
        }
    }

    /**
     * playEffect() n'envoie qu'UNE seule particule par appel et n'est visible que par le
     * joueur ciblé (personne d'autre ne voit rien) : on en envoie plusieurs autour du point
     * cible, avec un léger décalage aléatoire, pour rendre le repérage un peu plus visible
     * qu'une unique particule isolée, sans que ce soit une gerbe trop voyante.
     */
    private void envoyerParticulesDetectionMutuelle(Player destinataire, Location centre) {
        for (int i = 0; i < 6; i++) {
            double dx = (random.nextDouble() - 0.5) * 0.6;
            double dy = (random.nextDouble() - 0.5) * 0.6;
            double dz = (random.nextDouble() - 0.5) * 0.6;
            destinataire.playEffect(centre.clone().add(dx, dy, dz), org.bukkit.Effect.WITCH_MAGIC, 0);
        }
    }

    // ================= Groupes dynamiques =================

    public void chargerGroupes(ConfigurationSection section) {
        paliersGroupes.clear();
        if (section == null) {
            return;
        }
        List<?> liste = section.getList("paliers");
        if (liste == null) {
            return;
        }
        for (Object o : liste) {
            if (!(o instanceof Map)) {
                continue;
            }
            Map<?, ?> m = (Map<?, ?>) o;
            int minVivants = m.get("min-vivants") instanceof Number ? ((Number) m.get("min-vivants")).intValue() : 0;
            int taille = m.get("taille-groupe") instanceof Number ? ((Number) m.get("taille-groupe")).intValue() : 4;
            paliersGroupes.put(minVivants, taille);
        }
    }

    /** Répartit aléatoirement tous les joueurs vivants en groupes dont la taille dépend du nombre de survivants. */
    public void recalculerGroupes() {
        if (paliersGroupes.isEmpty()) {
            return;
        }
        List<GamePlayer> vivants = new ArrayList<>(getJoueursVivants());
        int nb = vivants.size();
        Map.Entry<Integer, Integer> palier = paliersGroupes.floorEntry(nb);
        if (palier == null) {
            palier = paliersGroupes.firstEntry();
        }
        int tailleGroupe = Math.max(1, palier.getValue());

        java.util.Collections.shuffle(vivants, random);
        int numero = 1;
        int compteurDansGroupe = 0;
        for (GamePlayer gp : vivants) {
            gp.setGroupe(numero);
            compteurDansGroupe++;
            if (compteurDansGroupe >= tailleGroupe) {
                compteurDansGroupe = 0;
                numero++;
            }
        }
    }

    /** Taille de groupe "conseillée" pour le nombre de survivants actuel (info affichée au scoreboard, pas un groupe assigné). */
    public int getLimiteGroupeActuelle() {
        if (paliersGroupes.isEmpty()) {
            return 0;
        }
        int nb = getJoueursVivants().size();
        Map.Entry<Integer, Integer> palier = paliersGroupes.floorEntry(nb);
        if (palier == null) {
            palier = paliersGroupes.firstEntry();
        }
        return palier == null ? 0 : palier.getValue();
    }

    public List<GamePlayer> getMembresDuGroupe(int numeroGroupe) {
        List<GamePlayer> resultat = new ArrayList<>();
        for (GamePlayer gp : getJoueursVivants()) {
            if (gp.getGroupe() == numeroGroupe) {
                resultat.add(gp);
            }
        }
        return resultat;
    }

    // ================= Elimination / mort =================

    /** Point d'entrée pour toute élimination déclenchée par une règle du jeu (vote, poison, meute...). */
    public void eliminer(GamePlayer cible, String raisonAffichage) {
        eliminer(cible, raisonAffichage, null);
    }

    public void eliminer(GamePlayer cible, String raisonAffichage, Camp campResponsable) {
        if (!cible.isVivant()) {
            return;
        }
        Player p = cible.getPlayer();
        if (p == null) {
            // Joueur hors-ligne : pas de fenêtre de résurrection possible, on finalise direct.
            finaliserMort(cible, raisonAffichage, campResponsable);
            return;
        }
        this.raisonTransitoire = raisonAffichage;
        this.campResponsableTransitoire = campResponsable;
        p.setHealth(0.0);
    }

    /**
     * A appeler depuis un Listener sur PlayerQuitEvent. Le lobby a déjà son propre mécanisme de
     * sortie propre (/lg leave, voir quitter()) : ici on ne s'occupe que d'une déconnexion en
     * pleine partie. Impossible de distinguer un crash d'un abandon volontaire côté serveur, donc
     * on laisse toujours le bénéfice du doute : le joueur reste inscrit et vivant, avec 2 minutes
     * réelles pour revenir avant élimination (voir tickDeconnexions() pour l'échéance, et
     * surReconnexion() pour l'annulation si il revient à temps).
     */
    public void surDeconnexion(Player p) {
        if (!estEnCours()) {
            return;
        }
        GamePlayer gp = getGamePlayer(p.getUniqueId());
        if (gp == null || !gp.isVivant() || echeancesDeconnexion.containsKey(gp.getUuid())) {
            return;
        }
        echeancesDeconnexion.put(gp.getUuid(), System.currentTimeMillis() + DELAI_GRACE_DECONNEXION_SECONDES * 1000L);
        diffuser("&e" + gp.getNom() + " &7s'est déconnecté(e). &f2 minutes &7pour revenir avant d'être éliminé(e) de la partie.");
    }

    /**
     * A appeler depuis un Listener sur PlayerJoinEvent : annule le compte à rebours d'élimination
     * en cours pour ce joueur s'il en avait un (reconnexion à temps après un crash/déco).
     */
    public void surReconnexion(Player p) {
        GamePlayer gp = getGamePlayer(p.getUniqueId());
        if (gp != null && echeancesDeconnexion.remove(gp.getUuid()) != null) {
            diffuser("&a" + gp.getNom() + " &7est revenu(e) à temps !");
        }
    }

    /**
     * Vérifie, une fois par seconde réelle (voir le minuteur 1x/seconde de LGUHCPlugin), si le
     * délai de grâce d'un ou plusieurs joueurs déconnectés est écoulé. Basé sur
     * System.currentTimeMillis() plutôt que sur un délai en ticks planifié une seule fois via
     * runTaskLater : même principe que tickMinuteriesAutomatiques() dans cette classe (voir son
     * commentaire) - un long délai en ticks dérive dès que le serveur ralentit (TPS < 20), ce qui
     * repousserait l'élimination bien au-delà des 2 minutes annoncées sur un serveur chargé.
     */
    public void tickDeconnexions() {
        if (echeancesDeconnexion.isEmpty()) {
            return;
        }
        long maintenant = System.currentTimeMillis();
        echeancesDeconnexion.entrySet().removeIf(entree -> {
            if (maintenant < entree.getValue()) {
                return false;
            }
            GamePlayer gp = getGamePlayer(entree.getKey());
            if (gp != null && gp.isVivant()) {
                diffuser("&c" + gp.getNom() + " &7n'est pas revenu(e) à temps et est éliminé(e) de la partie.");
                eliminer(gp, "n'est pas revenu(e) à temps après une déconnexion");
            }
            return true;
        });
    }

    /**
     * Vérifie, une fois par seconde réelle (voir le minuteur 1x/seconde de LGUHCPlugin), le temps
     * restant de l'invincibilité générale de début de partie (voir veritableDebutPartie()) et
     * envoie un rappel dans le chat à chaque minute écoulée, puis un dernier message quand elle se
     * termine. Basé sur System.currentTimeMillis() (comme tickDeconnexions()/tickMinuteriesAutomatiques())
     * plutôt que sur un compteur de ticks, pour ne pas dériver sous lag.
     */
    public void tickInvincibiliteDebut() {
        if (debutInvincibiliteTimestamp <= 0L || dernierRappelInvincibiliteMinute >= 5) {
            return;
        }
        long ecouleSecondes = (System.currentTimeMillis() - debutInvincibiliteTimestamp) / 1000L;
        if (ecouleSecondes >= DUREE_INVINCIBILITE_DEBUT_SECONDES) {
            dernierRappelInvincibiliteMinute = 5;
            diffuser("&c&lFin de l'invincibilité de début de partie, faites attention !");
            return;
        }
        int minuteActuelle = (int) (ecouleSecondes / 60L);
        if (minuteActuelle > dernierRappelInvincibiliteMinute) {
            dernierRappelInvincibiliteMinute = minuteActuelle;
            long resteMinutes = (DUREE_INVINCIBILITE_DEBUT_SECONDES / 60L) - minuteActuelle;
            diffuser("&e&l⏱ Invincibilité de début de partie : encore &f" + resteMinutes + " &e&lminute(s).");
        }
    }

    /**
     * Vrai si ce joueur est actuellement protégé de tous les dégâts, que ce soit par
     * l'invincibilité générale de début de partie ou par une invincibilité individuelle accordée
     * à un respawn (voir UHCRulesListener#surRespawn), les deux étant stockées dans le même champ
     * d'état "invincible_jusqua" (le plus grand des deux l'emporte toujours, voir surRespawn).
     * Couvre aussi le gel de préparation avant le vrai début de partie (flag "gel_debut_actif").
     * Vérifié depuis UHCRulesListener#surDegatsGeneraux pour annuler le dégât.
     */
    public boolean estProtege(GamePlayer gp) {
        if (gp == null) {
            return false;
        }
        if (gp.getEtat("gel_debut_actif", false)) {
            return true;
        }
        long jusqua = gp.getEtat("invincible_jusqua", 0L);
        return System.currentTimeMillis() < jusqua;
    }

    /**
     * Appelé par le Listener sur un PlayerDeathEvent réel (combat, chute,
     * éliminations forcées...). Ouvre la fenêtre de résurrection (Infect
     * Père des Loups puis Sorcière) plutôt que de finaliser immédiatement.
     */
    public void surMortReelle(GamePlayer gp, Camp campResponsableDetecte, String raisonSiMortNaturelle) {
        String raison = this.raisonTransitoire != null ? this.raisonTransitoire : raisonSiMortNaturelle;
        Camp camp = this.campResponsableTransitoire != null ? this.campResponsableTransitoire : campResponsableDetecte;
        this.raisonTransitoire = null;
        this.campResponsableTransitoire = null;
        plugin.getDeathManager().debuterFenetreMort(plugin, gp, camp, raison);
    }

    /** Rend une mort définitive (appelé uniquement après l'échec de la fenêtre de résurrection). */
    public void finaliserMort(GamePlayer gp, String raisonAffichage, Camp campResponsable) {
        if (!gp.isVivant()) {
            return;
        }
        gp.setVivant(false);
        gp.setEnAttenteMort(false);
        Player joueurMort = gp.getPlayer();
        if (joueurMort != null) {
            joueurMort.setWalkSpeed(RenardRole.VITESSE_MARCHE_NORMALE);
            // Le joueur patientait au lobby depuis debuterFenetreMort() (voir DeathManager) : sa
            // mort devient définitive, direction spectateur DANS LE MONDE DE JEU (et non plus le
            // lobby) pour qu'il puisse suivre la suite de la partie. On vise l'endroit exact de
            // sa mort (mémorisé par DeathManager AVANT dropperStuff(), qui le consomme juste en
            // dessous) plutôt qu'un point aléatoire.
            Location lieuMort = plugin.getDeathManager().getDernierLieuMort(gp.getUuid());
            if (lieuMort != null && lieuMort.getWorld() != null) {
                joueurMort.teleport(lieuMort);
            } else {
                World mondeJeu = getMondeJeu();
                if (mondeJeu != null) {
                    joueurMort.teleport(emplacementAleatoireDansBordure(mondeJeu));
                }
            }
        }
        // Mort définitive : le stuff intercepté à la mort réelle tombe enfin au sol,
        // à l'endroit exact où le joueur est mort.
        plugin.getDeathManager().dropperStuff(gp);

        Role role = gp.getRole() != null ? plugin.getRoleRegistry().get(gp.getRole()) : null;
        if (role != null) {
            role.onDeath(plugin, gp);
        }

        plugin.getDeathManager().annoncerMort(plugin, gp, raisonAffichage);

        // Enfant Sauvage : transformation si son modèle vient de mourir
        for (GamePlayer autre : getJoueursVivants()) {
            if (autre.getRole() == RoleType.ENFANT_SAUVAGE
                    && !autre.getEtat("sauvage_transforme", false)
                    && gp.getUuid().equals(autre.getModele())) {
                autre.setEtat("sauvage_transforme", true);
                autre.setCampSansAffichage(Camp.LOUPS);
                // Aura inchangée (reste Neutre) : c'est désormais une propriété fixe du rôle Enfant Sauvage,
                // pas quelque chose qui suit le camp réel comme avant.
                Msg.envoyer(autre.getPlayer(), "&4&lVotre modèle est mort... vous vous transformez en Loup-Garou !");
                annoncerNouvelAllieLoup(autre, "Enfant Sauvage transformé");
            }
        }

        // Loup-Garou Mystique : intel sur la mort d'un membre du camp des Loups
        if (gp.getCamp() == Camp.LOUPS) {
            List<GamePlayer> autresCamps = getJoueursVivants().stream()
                    .filter(g -> g.getCamp() != Camp.LOUPS)
                    .collect(Collectors.toList());
            if (!autresCamps.isEmpty()) {
                GamePlayer cibleInfo = autresCamps.get(random.nextInt(autresCamps.size()));
                for (GamePlayer mystique : getJoueursVivants()) {
                    if (mystique.getRole() == RoleType.LOUP_MYSTIQUE) {
                        Msg.envoyer(mystique.getPlayer(), "&5Vision mystique : &f" + cibleInfo.getNom() + " &5est en réalité... &f" + cibleInfo.getRole().getNomAffiche());
                    }
                }
            }
        }

        boolean victoireCouple = plugin.getCoupleManager().traiterMortPourCouple(this, gp);
        if (!victoireCouple) {
            verifierVictoire();
        }

        planifierMiseAJourScoreboard();
    }

    // ================= Victoire =================

    public void verifierVictoire() {
        if (phase == GamePhase.TERMINEE) {
            return;
        }
        List<GamePlayer> vivants = getJoueursVivants();
        if (vivants.isEmpty()) {
            terminerPartie(null, "Personne (égalité totale)");
            return;
        }

        // Victoire du Couple (4ème camp, transcende les camps d'origine) : tant que le Couple
        // existe et que ses 2 membres sont vivants, NI le Village NI les Loups NI un Solo ne
        // peuvent gagner "par défaut" - ils doivent être réduits à eux seuls (2), ou eux seuls
        // + le Cupidon qui les a formés (3), pour l'emporter. Même logique que le Solo ci-dessous.
        GamePlayer[] couple = getCouple();
        boolean coupleVivant = couple != null && couple[0].isVivant() && couple[1].isVivant();
        if (coupleVivant) {
            if (vivants.size() == 2) {
                terminerPartie(null, "Le Couple (" + couple[0].getNom() + " & " + couple[1].getNom() + ")");
                return;
            }
            GamePlayer cupidon = vivants.stream()
                    .filter(g -> g.getRole() == RoleType.CUPIDON && g != couple[0] && g != couple[1])
                    .findFirst().orElse(null);
            if (vivants.size() == 3 && cupidon != null) {
                terminerPartie(null, "Le Couple & Cupidon (" + couple[0].getNom() + " & " + couple[1].getNom() + " & " + cupidon.getNom() + ")");
                return;
            }
            // Le Couple est vivant mais pas encore réduit à eux seuls (+Cupidon) : la partie
            // continue, quel que soit le rapport de force Village/Loups/Solo à ce moment-là.
            return;
        }

        // Loup-Garou Blanc : compté dans le camp des Loups pour TOUTES les mécaniques de jeu
        // (chat de meute, vision de nuit, liste des alliés, pouvoirs...) - c'est un Loup normal
        // en tout point, sans commande ni capacité à part. Mais il ne partage PAS la victoire
        // collective des Loups : il ne gagne que s'il finit unique survivant, ce qui suppose
        // qu'il élimine lui-même ses anciens alliés au corps-à-corps le moment venu. Ce check
        // doit passer AVANT le calcul loups/solos/village ci-dessous, sans quoi "village == 0"
        // le ferait gagner à tort comme un Loup normal dès que le Village est éliminé, même
        // avec d'autres Loups encore vivants.
        GamePlayer loupBlanc = vivants.stream()
                .filter(g -> g.getRole() == RoleType.LOUP_GAROU_BLANC)
                .findFirst().orElse(null);
        if (vivants.size() == 1 && loupBlanc != null) {
            terminerPartie(null, loupBlanc.getRole().getNomAffiche() + " (" + loupBlanc.getNom() + ")");
            return;
        }

        // Le Loup-Garou Blanc est exclu de "loups" ci-dessous (voir javadoc au-dessus : il ne
        // partage pas leur victoire), et donc aussi de "village" (sans quoi il serait compté à
        // tort comme un Villageois dans le décompte).
        long loups = vivants.stream().filter(g -> g.getCamp() == Camp.LOUPS && g != loupBlanc).count();
        long solos = vivants.stream().filter(g -> g.getCamp() == Camp.SOLO).count();
        long village = vivants.size() - loups - solos - (loupBlanc != null ? 1 : 0);

        // Un Solo (ex: Assassin) est hostile à tout le monde : tant qu'il est vivant,
        // ni le Village ni les Loups ne peuvent être déclarés vainqueurs "par défaut" -
        // il doit être éliminé, ou être l'unique survivant pour gagner lui-même.
        boolean soloSeulSurvivant = vivants.size() == 1 && vivants.get(0).getCamp() == Camp.SOLO;
        if (soloSeulSurvivant) {
            terminerPartie(Camp.SOLO, vivants.get(0).getRole().getNomAffiche() + " (" + vivants.get(0).getNom() + ")");
            return;
        }
        if (solos > 0) {
            // Un ou plusieurs Solo sont encore vivants et pas seuls : la partie continue,
            // quel que soit le rapport de force Village/Loups.
            return;
        }
        if (loupBlanc != null) {
            // Le Loup-Garou Blanc est vivant mais pas encore seul (le cas "seul survivant" est
            // déjà traité plus haut) : comme un Solo, sa seule présence empêche toute victoire
            // "par défaut" du Village ou des Loups tant qu'il n'a pas été éliminé - par le
            // Village ou par ses anciens alliés - ou qu'il n'est pas devenu l'unique survivant.
            return;
        }

        if (loups == 0) {
            terminerPartie(Camp.VILLAGE, "le Village");
            return;
        }
        // Les Loups ne gagnent que lorsqu'il ne reste plus AUCUN Village vivant (élimination
        // complète du camp), et non plus dès qu'ils sont à égalité ou majoritaires : cette
        // ancienne règle de "parité" terminait la partie bien trop tôt (parfois dès la
        // première mort), alors que les 4 camps doivent être réduits à un seul pour que la
        // partie se termine.
        if (village == 0) {
            terminerPartie(Camp.LOUPS, "les Loups-Garous");
            return;
        }
        // Village et Loups coexistent encore tous les deux : la partie continue.
    }

    public void terminerPartie(Camp vainqueur, String nomAffiche) {
        phase = GamePhase.TERMINEE;
        diffuser("&6&l★★★ FIN DE PARTIE ★★★");
        diffuser("&e&lVictoire de : &f" + nomAffiche + " !");
        diffuser("&7Récapitulatif des rôles :");
        for (GamePlayer gp : joueurs.values()) {
            String statut = gp.isVivant() ? "&a[Vivant]" : "&c[Mort]";
            String role = gp.getRole() != null ? gp.getRole().getNomAffiche() : "?";
            // Voir GamePlayer#setEtat("infecte", true) posé au moment de l'infection (LGCommand#infecterPere).
            String suffixeInfecte = gp.getEtat("infecte", false) ? " &c(infecté)" : "";
            String suffixeCouple = gp.estEnCouple() ? " &d(Couple)" : "";
            diffuser(statut + " &f" + gp.getNom() + " &7- " + role + suffixeInfecte + suffixeCouple);
        }
        for (GamePlayer gp : joueurs.values()) {
            Player p = gp.getPlayer();
            if (p != null) {
                p.setGameMode(GameMode.SPECTATOR);
            }
        }
    }

    public void reinitialiser() {
        Location emplacementLobby = getEmplacementLobby();
        for (GamePlayer gp : joueurs.values()) {
            Player p = gp.getPlayer();
            if (p == null) {
                continue;
            }
            p.setWalkSpeed(RenardRole.VITESSE_MARCHE_NORMALE);
            p.setGameMode(GameMode.ADVENTURE);
            if (emplacementLobby != null) {
                p.teleport(emplacementLobby);
            }
            // demarrer() vide déjà l'inventaire/armure au LANCEMENT d'une partie, mais rien
            // n'était fait au RESET : un /lg stop renvoyait les joueurs au lobby avec le stuff
            // de la partie qui vient de se terminer encore sur eux (armure comprise, jamais
            // touchée par Inventory#clear()), potions actives, et une éventuelle vie max
            // réduite de façon permanente (Idiot du Village) qui restait bloquée pour la partie
            // suivante.
            p.getInventory().clear();
            p.getInventory().setArmorContents(new org.bukkit.inventory.ItemStack[4]);
            for (PotionEffect eff : new ArrayList<>(p.getActivePotionEffects())) {
                p.removePotionEffect(eff.getType());
            }
            p.setMaxHealth(20.0);
            p.setHealth(20.0);
            p.setFoodLevel(20);
        }
        joueurs.clear();
        phase = GamePhase.LOBBY;
        episodeActuel = 0;
        periodeDansEpisode = 0;
        numeroNuit = 0;
        blocagePouvoirsVillageJusquaEpisode = -1;
        finPhaseTimestamp = 0L;
        debutPartieTimestamp = 0L;
        ticksEcoulesDansPhase = 0L;
        listeLoupsRevelee = false;
        echeancesDeconnexion.clear();
        debutInvincibiliteTimestamp = 0L;
        dernierRappelInvincibiliteMinute = 0;
        secondesPremierEvenementAleatoire = -1L;
        secondesSecondEvenementAleatoire = -1L;
        premierEvenementAleatoireDeclenche = false;
        secondEvenementAleatoireDeclenche = false;
        secondesRumeursAleatoire = -1L;
        rumeursAleatoireDeclenche = false;
        collecteRumeursActive = false;
        joueursAyantEnvoyeRumeur.clear();
        messagesRumeursCollectes.clear();

        // Tout le monde est sorti du monde de jeu (téléporté au lobby ci-dessus, ou déjà
        // hors ligne) : on peut le régénérer en tâche de fond avant la prochaine partie.
        if (plugin.getWorldResetManager().isAutoApresReset()) {
            plugin.getWorldResetManager().regenerer(null);
        }
    }

    /**
     * Le monde lobby configuré (monde.lobby) s'il existe et est chargé, sinon null. Public pour
     * que d'autres classes (LobbyListener, pour les réglages "monde lobby" : jour perma, pas de
     * mobs, immortalité...) puissent s'y référer sans redupliquer la lecture de monde.lobby.
     */
    public World getMondeLobby() {
        String nom = plugin.getConfig().getString("monde.lobby", null);
        return (nom == null || nom.isEmpty()) ? null : Bukkit.getWorld(nom);
    }

    /**
     * Point d'apparition fixe (0, 54, 0) dans le monde lobby, ou null si monde.lobby n'est pas
     * configuré/chargé. Utilisé partout où on renvoie quelqu'un au lobby (/lg stop, connexion,
     * mort hors partie) plutôt que World#getSpawnLocation() : ce dernier est le spawn "naturel"
     * calculé par Minecraft, pas fiable sur un monde généré à la volée (vide/plat) - il peut
     * atterrir n'importe où, y compris très loin en Y négatif.
     */
    public Location getEmplacementLobby() {
        World lobby = getMondeLobby();
        if (lobby == null) {
            return null;
        }
        return new Location(lobby, 0.5, 54.0, 0.5);
    }

    // ================= Chat (coupure manuelle par un hôte) =================

    public boolean isChatDesactive() {
        return chatDesactive;
    }

    public void setChatDesactive(boolean chatDesactive) {
        this.chatDesactive = chatDesactive;
    }

    // ================= Blocage des pouvoirs (Ancien) =================

    public void activerBlocagePouvoirsVillage() {
        blocagePouvoirsVillageJusquaEpisode = episodeActuel + 1;
    }

    public boolean pouvoirsVillageBloques() {
        return blocagePouvoirsVillageJusquaEpisode >= 0 && episodeActuel <= blocagePouvoirsVillageJusquaEpisode;
    }

    // ================= Outils admin / test =================

    /** Coupe le minuteur en cours et déclenche immédiatement la transition suivante. Pour tester sans attendre. */
    public boolean avancerPhaseImmediatement(CommandSender demandeur) {
        switch (phase) {
            case EPISODE_1: {
                long dureeTicks = plugin.getConfig().getLong("episodes.duree-minutes", 20) * 60L * 20L;
                avancerTempsTotalDe(dureeTicks);
                demandeur.sendMessage(Msg.c("&a[Admin] Fin de l'épisode 1 forcée : attribution des rôles... &7(+" + (dureeTicks / 20 / 60) + " min ajoutées au temps de jeu)"));
                finDePremierEpisode();
                return true;
            }
            case JOUR: {
                long dureeTicks = dureePhaseTicks();
                avancerTempsTotalDe(dureeTicks);
                demandeur.sendMessage(Msg.c("&a[Admin] Passage à la nuit forcé. &7(+" + (dureeTicks / 20 / 60) + " min ajoutées au temps de jeu)"));
                finDeJour();
                return true;
            }
            case NUIT: {
                long dureeTicks = dureePhaseTicks();
                avancerTempsTotalDe(dureeTicks);
                demandeur.sendMessage(Msg.c("&a[Admin] Fin de nuit forcée. &7(+" + (dureeTicks / 20 / 60) + " min ajoutées au temps de jeu)"));
                finDeNuit();
                return true;
            }
            default:
                demandeur.sendMessage(Msg.c("&cImpossible d'avancer la phase depuis l'état actuel (" + phase + ")."));
                return false;
        }
    }

    /**
     * Avance artificiellement le compteur de temps total écoulé de la partie (celui affiché au
     * scoreboard via getTempsTotalEcouleSecondes()) en reculant l'horodatage de début de partie
     * du nombre de ticks skippés par /lg admin skip : le temps de jeu affiché augmente donc
     * immédiatement comme si cette durée s'était réellement écoulée.
     */
    private void avancerTempsTotalDe(long ticks) {
        if (debutPartieTimestamp > 0) {
            debutPartieTimestamp -= ticks * 50L;
        }
    }

    /** Force l'attribution d'un rôle précis à un joueur, hors distribution aléatoire normale. Pour tester un rôle isolément. */
    public void forcerRole(CommandSender demandeur, GamePlayer gp, RoleType type) {
        assignerRoleA(gp, type);
        gp.setVivant(true);
        demandeur.sendMessage(Msg.c("&a[Admin] " + gp.getNom() + " est maintenant " + type.getNomAffiche() + "."));
        Msg.envoyer(gp.getPlayer(), "&e[Admin] Votre rôle a été forcé :");
        envoyerCarteRole(gp, Math.max(episodeActuel, 2));
    }

    /** Tue instantanément un joueur pour tester la chaîne de mort (Chasseur, Couple, Infect...) sans combat réel. */
    public void forcerMort(CommandSender demandeur, GamePlayer cible) {
        if (!cible.isVivant()) {
            demandeur.sendMessage(Msg.c("&cCe joueur est déjà mort."));
            return;
        }
        demandeur.sendMessage(Msg.c("&a[Admin] Mort forcée de " + cible.getNom() + "."));
        eliminer(cible, "a été éliminé(e) (test admin)");
    }

    /** Ressuscite un joueur mort en cours de partie pour continuer à tester après un /lg admin kill. */
    public void forcerVie(CommandSender demandeur, GamePlayer cible) {
        Player p = cible.getPlayer();
        if (p == null) {
            demandeur.sendMessage(Msg.c("&cCe joueur est hors ligne."));
            return;
        }
        // /lg admin kill passe par une VRAIE mort (eliminer() -> setHealth(0)), donc par tout le
        // circuit de DeathManager : stuff intercepté (sauvegarderStuff) et mis de côté, joueur en
        // spectateur avec enAttenteMort=true, fenêtre de résurrection Infect Père/Sorcière
        // programmée 5s plus tard. Sans les deux lignes suivantes, un /lg admin revive pendant ce
        // délai laissait enAttenteMort à true (la fenêtre pouvait donc encore se déclencher sur un
        // joueur déjà vivant) ET laissait le stuff coincé dans DeathManager - finaliserMort()
        // refusant de le faire tomber au sol tant que isVivant() est vrai. Ce stuff (armure, outils
        // tels quels, ex: une pioche figée à sa durabilité du moment) ne réapparaissait alors que
        // beaucoup plus tard, au hasard d'une vraie résurrection (Ancien, Sorcière...) qui allait
        // piocher ces données périmées dans la map de DeathManager pour ce joueur - d'où l'effet
        // "la pioche réapparaît comme par magie" en boucle sur un /lg admin kill + revive répété.
        cible.setEnAttenteMort(false);
        plugin.getDeathManager().restaurerStuff(cible);
        cible.setVivant(true);
        p.setGameMode(GameMode.SURVIVAL);
        p.setMaxHealth(20.0);
        p.setHealth(20.0);
        p.setFoodLevel(20);
        p.setWalkSpeed(RenardRole.VITESSE_MARCHE_NORMALE);
        demandeur.sendMessage(Msg.c("&a[Admin] " + cible.getNom() + " est de nouveau vivant."));
        Msg.envoyer(p, "&e[Admin] Vous avez été ramené(e) à la vie pour les besoins du test.");
        planifierMiseAJourScoreboard();
    }

    // ================= Scoreboard =================

    private void planifierMiseAJourScoreboard() {
        Bukkit.getScheduler().runTask(plugin, () -> plugin.getScoreboardManager().mettreAJour(plugin));
    }

    // ================= Accesseurs =================

    public GamePhase getPhase() {
        return phase;
    }

    public int getEpisodeActuel() {
        return episodeActuel;
    }

    public boolean estNuit() {
        return phase == GamePhase.NUIT;
    }

    public boolean estEnCours() {
        return phase != GamePhase.LOBBY && phase != GamePhase.TERMINEE;
    }

    /** Temps restant estimé (en secondes) avant la fin de la phase actuelle. */
    public long getTempsRestantSecondes() {
        if (finPhaseTimestamp <= 0) {
            return 0;
        }
        long restant = (finPhaseTimestamp - System.currentTimeMillis()) / 1000L;
        return Math.max(0, restant);
    }

    /** Temps total écoulé depuis le lancement de la partie (indépendant du minuteur de phase). */
    public long getTempsTotalEcouleSecondes() {
        if (debutPartieTimestamp <= 0) {
            return 0;
        }
        return Math.max(0, (System.currentTimeMillis() - debutPartieTimestamp) / 1000L);
    }

    /**
     * Compte, pour chaque rôle présent en plusieurs exemplaires dans la
     * composition de départ (ex: Loup-Garou, Sœurs), combien sont encore
     * vivants. Les rôles à exemplaire unique ne sont pas inclus (leur
     * "compte" n'apporte rien de plus que la mort individuelle du joueur).
     */
    public Map<RoleType, Integer> getCompositionRestante() {
        Map<RoleType, Integer> totalDepart = new LinkedHashMap<>();
        for (RoleType type : compositionUtilisee) {
            totalDepart.merge(type, 1, Integer::sum);
        }
        Map<RoleType, Integer> restant = new LinkedHashMap<>();
        for (Map.Entry<RoleType, Integer> entree : totalDepart.entrySet()) {
            if (entree.getValue() <= 1) {
                continue;
            }
            long vivants = getJoueursVivants().stream().filter(g -> g.getRole() == entree.getKey()).count();
            restant.put(entree.getKey(), (int) vivants);
        }
        return restant;
    }

    public Collection<GamePlayer> getTousLesJoueurs() {
        return joueurs.values();
    }

    /** La liste "à plat" des rôles distribués pour cette partie (utile pour piocher un rôle-leurre, ex: Voyante). */
    public List<RoleType> getCompositionUtilisee() {
        return compositionUtilisee;
    }

    public List<GamePlayer> getJoueursVivants() {
        List<GamePlayer> liste = new ArrayList<>();
        for (GamePlayer gp : joueurs.values()) {
            if (gp.isVivant()) {
                liste.add(gp);
            }
        }
        return liste;
    }

    public int compterVivants(Camp camp) {
        int n = 0;
        for (GamePlayer gp : joueurs.values()) {
            if (gp.isVivant() && gp.getCampAffiche() == camp) {
                n++;
            }
        }
        return n;
    }

    public GamePlayer getGamePlayer(UUID uuid) {
        return uuid == null ? null : joueurs.get(uuid);
    }

    /**
     * Retourne les deux amoureux formés par Cupidon (vivants ou morts), ou null si aucun
     * couple n'a été formé dans cette partie. L'ordre des deux éléments n'est pas garanti.
     */
    public GamePlayer[] getCouple() {
        for (GamePlayer gp : joueurs.values()) {
            if (gp.estEnCouple()) {
                GamePlayer autre = getGamePlayer(gp.getAmoureux());
                if (autre != null) {
                    return new GamePlayer[]{gp, autre};
                }
            }
        }
        return null;
    }

    /** Vrai si un couple a été formé et que ses deux membres sont encore vivants. */
    public boolean coupleEstVivant() {
        GamePlayer[] couple = getCouple();
        return couple != null && couple[0].isVivant() && couple[1].isVivant();
    }

    public GamePlayer getGamePlayer(Player p) {
        return p == null ? null : joueurs.get(p.getUniqueId());
    }

    public void diffuser(String message) {
        for (GamePlayer gp : joueurs.values()) {
            Msg.envoyer(gp.getPlayer(), message);
        }
        Bukkit.getConsoleSender().sendMessage(Msg.c(message));
    }
}
