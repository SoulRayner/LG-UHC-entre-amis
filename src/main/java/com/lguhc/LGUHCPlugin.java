package com.lguhc;

import com.lguhc.commands.LGCommand;
import com.lguhc.commands.LWCommand;
import com.lguhc.game.BorderManager;
import com.lguhc.game.CompositionManager;
import com.lguhc.game.CoupleManager;
import com.lguhc.game.DeathManager;
import com.lguhc.game.GameManager;
import com.lguhc.game.GamePlayer;
import com.lguhc.game.ScoreboardManager;
import com.lguhc.game.VisionMinesTask;
import com.lguhc.game.VoteManager;
import com.lguhc.game.WorldResetManager;
import com.lguhc.listeners.AbilityListener;
import com.lguhc.listeners.CoupleListener;
import com.lguhc.listeners.DeconnexionListener;
import com.lguhc.listeners.LobbyListener;
import com.lguhc.listeners.UHCRulesListener;
import com.lguhc.roles.RoleRegistry;
import com.lguhc.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class LGUHCPlugin extends JavaPlugin {

    private static LGUHCPlugin instance;

    private GameManager gameManager;
    private CompositionManager compositionManager;
    private BorderManager borderManager;
    private VoteManager voteManager;
    private CoupleManager coupleManager;
    private DeathManager deathManager;
    private ScoreboardManager scoreboardManager;
    private RoleRegistry roleRegistry;
    private VisionMinesTask visionMinesTask;
    private WorldResetManager worldResetManager;

    /** Délai avant le Final Heal (tous les joueurs vivants remis à full vie), en secondes RÉELLES depuis le début de la partie. */
    private static final int FINAL_HEAL_SECONDES = 20 * 60;
    private boolean finalHealDeclenche = false;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        reloadConfig();

        this.roleRegistry = new RoleRegistry();
        this.gameManager = new GameManager(this);
        this.compositionManager = new CompositionManager(getLogger());
        this.borderManager = new BorderManager();
        this.voteManager = new VoteManager();
        this.coupleManager = new CoupleManager();
        this.deathManager = new DeathManager();
        this.scoreboardManager = new ScoreboardManager();
        this.worldResetManager = new WorldResetManager(this);

        compositionManager.charger(getConfig().getInt("joueurs-par-loup", 3), getConfig().getConfigurationSection("compositions"));
        borderManager.charger(getConfig().getConfigurationSection("bordure"));
        voteManager.charger(getConfig().getConfigurationSection("vote"));
        gameManager.chargerGroupes(getConfig().getConfigurationSection("groupes-dynamiques"));
        worldResetManager.charger(getConfig().getConfigurationSection("regeneration-monde"));

        // Bukkit ne charge automatiquement que le(s) monde(s) déclaré(s) côté serveur
        // (server.properties / bukkit.yml). monde.nom et monde.lobby doivent donc être
        // chargés manuellement ici s'ils ne le sont pas déjà (dossier présent mais pas
        // encore ouvert, ou monde tout neuf qui n'existe pas encore sur le disque).
        String nomMondeJeu = getConfig().getString("monde.nom", "world");
        String nomMondeParDefaut = Bukkit.getWorlds().get(0).getName();
        if (nomMondeJeu.equals(nomMondeParDefaut)) {
            getLogger().warning("monde.nom (\"" + nomMondeJeu + "\") correspond au monde PAR DEFAUT du serveur "
                    + "(level-name). La régénération va supprimer/reconstruire CE monde à chaque partie ! "
                    + "Si ce n'est pas voulu, mettez un nom distinct dans monde.nom (config.yml) et recopiez-y votre carte.");
        }
        chargerMondeSiBesoin(nomMondeJeu);
        chargerMondeSiBesoin(getConfig().getString("monde.lobby", null));

        getServer().getPluginManager().registerEvents(new UHCRulesListener(this), this);
        getServer().getPluginManager().registerEvents(new AbilityListener(this), this);
        getServer().getPluginManager().registerEvents(new CoupleListener(this), this);
        getServer().getPluginManager().registerEvents(new LobbyListener(this), this);
        getServer().getPluginManager().registerEvents(new DeconnexionListener(this), this);

        getCommand("lg").setExecutor(new LGCommand(this));
        getCommand("lw").setExecutor(new LWCommand(this));

        this.visionMinesTask = new VisionMinesTask(this);
        this.visionMinesTask.demarrer();

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            gameManager.tickCorruption();
            gameManager.tickBoussole();
            // Doit s'exécuter juste après tickBoussole() : les deux ciblent le même attribut
            // natif de boussole du joueur, donc l'ordre d'appel décide laquelle "gagne" visuellement.
            gameManager.tickBoussoleCouple();
            gameManager.tickEquipement();
            gameManager.tickCycleJourNuit();
            gameManager.tickInvisiblesMutuels();
            // Attribution des rôles + démarrage bordure : voir le commentaire dans
            // GameManager.demarrer()/tickMinuteriesAutomatiques() (bascule sur horloge réelle).
            gameManager.tickMinuteriesAutomatiques();
            gameManager.tickDeconnexions();
            tickFinalHeal();
        }, 20L, 20L);

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (gameManager.estEnCours()) {
                scoreboardManager.mettreAJour(this);
            }
        }, 20L, 20L);

        getLogger().info("LGUHC activé — /lg pour commencer.");
    }

    /** Charge (ou crée s'il n'existe pas encore sur le disque) le monde nommé `nom`, sauf s'il est déjà chargé. Ignore silencieusement un nom vide/null. */
    private void chargerMondeSiBesoin(String nom) {
        if (nom == null || nom.isEmpty()) {
            return;
        }
        if (Bukkit.getWorld(nom) == null) {
            World monde = new WorldCreator(nom).createWorld();
            if (monde != null) {
                getLogger().info("Monde \"" + nom + "\" chargé.");
            } else {
                getLogger().warning("Impossible de charger/créer le monde \"" + nom + "\".");
            }
        }
    }

    @Override
    public void onDisable() {
        if (visionMinesTask != null) {
            visionMinesTask.cancel();
        }
        getLogger().info("LGUHC désactivé.");
    }

    /**
     * A 20 minutes RÉELLES pile depuis le début de la partie en cours (drapeau remis à false dès
     * que gameManager.estEnCours() redevient faux, donc à chaque nouvelle partie), remet tous les
     * joueurs vivants à leur vie maximale, une seule fois.
     * Se base sur gameManager.getTempsTotalEcouleSecondes() (System.currentTimeMillis(), donc une
     * vraie horloge murale) plutôt que sur un compteur incrémenté à chaque appel de ce minuteur :
     * l'ancien compteur supposait implicitement que ce minuteur s'exécute exactement 1x par
     * seconde réelle, ce qui n'est vrai que si le serveur tourne à 20 TPS pile. Dès que le serveur
     * ralentit (beaucoup de joueurs, chargement de chunks...), chaque "tick" prend plus de 50ms et
     * le compteur prenait du retard sur l'horloge réelle - d'où un déclenchement observé plusieurs
     * minutes après les 20 minutes annoncées.
     */
    private void tickFinalHeal() {
        if (!gameManager.estEnCours()) {
            finalHealDeclenche = false;
            return;
        }
        if (finalHealDeclenche || gameManager.getTempsTotalEcouleSecondes() < FINAL_HEAL_SECONDES) {
            return;
        }
        finalHealDeclenche = true;

        for (GamePlayer gp : gameManager.getJoueursVivants()) {
            Player joueur = gp.getPlayer();
            if (joueur != null && joueur.isOnline()) {
                joueur.setHealth(joueur.getMaxHealth());
                Msg.envoyer(joueur, "&c&lFinal Heal : &fvous regagnez tout vos coeurs !");
            }
        }
        Bukkit.broadcastMessage(Msg.c("&c&l⚔ Final Heal : &ftous les joueurs vivants regagnent tout leurs coeurs !"));
    }

    public static LGUHCPlugin getInstance() {
        return instance;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public CompositionManager getCompositionManager() {
        return compositionManager;
    }

    public BorderManager getBorderManager() {
        return borderManager;
    }

    public VoteManager getVoteManager() {
        return voteManager;
    }

    public CoupleManager getCoupleManager() {
        return coupleManager;
    }

    public DeathManager getDeathManager() {
        return deathManager;
    }

    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    public RoleRegistry getRoleRegistry() {
        return roleRegistry;
    }

    public WorldResetManager getWorldResetManager() {
        return worldResetManager;
    }
}
