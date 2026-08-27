package com.lguhc.listeners;

import com.lguhc.LGUHCPlugin;
import com.lguhc.game.GameManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Envoie les joueurs qui se connectent vers le monde lobby (monde.lobby dans config.yml),
 * pour qu'ils n'apparaissent jamais directement sur la carte de jeu, et applique les règles
 * de la salle d'attente tant qu'ils y sont : immortalité, pas de faim, aucun mob (le jour
 * permanent et l'absence de météo, eux, sont des réglages de MONDE et non de joueur — voir
 * LGUHCPlugin.appliquerReglagesMondeLobby(), appliqué une fois au chargement du monde).
 * Sans effet si monde.lobby est vide/absent du config, ou si le joueur est déjà inscrit à une
 * partie en cours (reconnexion pendant une partie déjà lancée) : GameManager.demarrer() se
 * charge lui-même de la téléportation sur la carte de jeu au lancement.
 */
public class LobbyListener implements Listener {

    private final LGUHCPlugin plugin;

    public LobbyListener(LGUHCPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        String nomLobby = plugin.getConfig().getString("monde.lobby", null);
        if (nomLobby == null || nomLobby.isEmpty()) {
            return;
        }
        GameManager gm = plugin.getGameManager();
        Location emplacementLobby = gm.getEmplacementLobby();
        if (emplacementLobby == null) {
            plugin.getLogger().warning("monde.lobby configuré (\"" + nomLobby + "\") mais introuvable/non chargé.");
            return;
        }

        Player p = event.getPlayer();
        if (gm.estEnCours() && gm.getGamePlayer(p) != null) {
            return;
        }
        p.teleport(emplacementLobby);
        p.setGameMode(GameMode.ADVENTURE);

        // Remise à plat à l'arrivée (vie/faim/feu), au cas où le joueur revient d'une partie
        // qui vient tout juste de se terminer : les EventHandler ci-dessous prennent ensuite
        // le relai pour que ça reste bloqué tant qu'il est au lobby.
        p.setHealth(p.getMaxHealth());
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setExhaustion(0f);
        p.setFireTicks(0);
    }

    /** true si `monde` est bien le monde lobby actuellement configuré et chargé. */
    private boolean estMondeLobby(World monde) {
        World lobby = plugin.getGameManager().getMondeLobby();
        return lobby != null && lobby.equals(monde);
    }

    /**
     * Immortalité au lobby : toute cause de dégât (mob, chute, feu, noyade, faim...) est
     * annulée pour un joueur qui s'y trouve. Cas particulier du vide (sous la carte, si le
     * lobby n'a pas de sol construit partout) : dégât annulé comme le reste, mais en plus le
     * joueur est renvoyé au point d'apparition du lobby, sinon il retomberait indéfiniment
     * sans jamais mourir.
     */
    @EventHandler(ignoreCancelled = true)
    public void surDegatsLobby(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player joueur = (Player) event.getEntity();
        if (!estMondeLobby(joueur.getWorld())) {
            return;
        }
        event.setCancelled(true);
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            Location emplacementLobby = plugin.getGameManager().getEmplacementLobby();
            if (emplacementLobby != null) {
                joueur.teleport(emplacementLobby);
            }
        }
    }

    /** Pas de faim au lobby : la barre de faim reste bloquée quoi que fasse le joueur. */
    @EventHandler(ignoreCancelled = true)
    public void surFaimLobby(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player && estMondeLobby(event.getEntity().getWorld())) {
            event.setCancelled(true);
        }
    }

    /**
     * Filet de sécurité en plus de World#setSpawnFlags() (déjà positionné au chargement du
     * monde, voir LGUHCPlugin.appliquerReglagesMondeLobby()) : si un mob apparaît quand même
     * dans le lobby (spawner posé dedans, oeuf d'invocation utilisé par un admin...), on
     * annule son apparition.
     */
    @EventHandler(ignoreCancelled = true)
    public void surSpawnMobLobby(CreatureSpawnEvent event) {
        if (estMondeLobby(event.getLocation().getWorld())) {
            event.setCancelled(true);
        }
    }
}
