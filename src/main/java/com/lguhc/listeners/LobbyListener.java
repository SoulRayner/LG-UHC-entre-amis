package com.lguhc.listeners;

import com.lguhc.LGUHCPlugin;
import com.lguhc.game.GameManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Envoie les joueurs qui se connectent vers le monde lobby (monde.lobby dans config.yml),
 * pour qu'ils n'apparaissent jamais directement sur la carte de jeu. Sans effet si
 * monde.lobby est vide/absent du config, ou si le joueur est déjà inscrit à une partie en
 * cours (reconnexion pendant une partie déjà lancée) : GameManager.demarrer() se charge
 * lui-même de la téléportation sur la carte de jeu au lancement.
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
    }
}
