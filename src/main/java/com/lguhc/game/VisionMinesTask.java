package com.lguhc.game;

import com.lguhc.LGUHCPlugin;
import com.lguhc.game.GamePlayer;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Donne automatiquement la Vision Nocturne aux joueurs vivants qui sont sous
 * la couche seuil (mine), pour ne plus dépendre des torches. L'effet est
 * retiré dès que le joueur remonte au-dessus du seuil, meurt, passe en
 * spectateur, ou si la partie s'arrête.
 *
 * Détection : simple limite de hauteur (Y &lt; seuil, 55 par défaut), peu
 * importe qu'il y ait un toit ou non. Ça évite qu'une base en surface avec
 * un simple toit donne la Vision Nocturne gratuitement.
 */
public class VisionMinesTask extends BukkitRunnable {

    /** Durée appliquée à chaque cycle (largement > intervalle de vérification, donc jamais de coupure visible). */
    private static final int DUREE_EFFET_TICKS = 20 * 60 * 5; // 5 minutes

    private final LGUHCPlugin plugin;
    /** Joueurs à qui CE système a donné l'effet, pour ne jamais toucher une Vision Nocturne obtenue autrement (potion, effet de rôle...). */
    private final Set<UUID> geresParCeSysteme = new HashSet<>();

    public VisionMinesTask(LGUHCPlugin plugin) {
        this.plugin = plugin;
    }

    /** À appeler une seule fois, dans LGUHCPlugin#onEnable() : new VisionMinesTask(this).demarrer(); */
    public void demarrer() {
        int intervalleTicks = plugin.getConfig().getInt("survie-uhc.vision-mines-intervalle-secondes", 3) * 20;
        this.runTaskTimer(plugin, 0L, Math.max(20L, intervalleTicks));
    }

    @Override
    public void run() {
        boolean actif = plugin.getConfig().getBoolean("survie-uhc.vision-mines-active", true)
                && plugin.getGameManager().estEnCours();

        if (!actif) {
            nettoyerTousLesEffets();
            return;
        }

        for (Player joueur : plugin.getServer().getOnlinePlayers()) {
            GamePlayer gp = plugin.getGameManager().getGamePlayer(joueur);
            boolean eligible = gp != null && gp.isVivant() && joueur.getGameMode() == GameMode.SURVIVAL;

            if (!eligible) {
                if (geresParCeSysteme.remove(joueur.getUniqueId())) {
                    joueur.removePotionEffect(PotionEffectType.NIGHT_VISION);
                }
                continue;
            }

            if (estSousLaCouche(joueur.getLocation())) {
                joueur.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, DUREE_EFFET_TICKS, 0, true), true);
                geresParCeSysteme.add(joueur.getUniqueId());
            } else if (geresParCeSysteme.remove(joueur.getUniqueId())) {
                joueur.removePotionEffect(PotionEffectType.NIGHT_VISION);
            }
        }
    }

    /** Vrai si le joueur est sous la couche seuil (config survie-uhc.vision-mines-couche-max, 55 par défaut). */
    private boolean estSousLaCouche(Location loc) {
        int coucheMax = plugin.getConfig().getInt("survie-uhc.vision-mines-couche-max", 55);
        return loc.getBlockY() < coucheMax;
    }

    /** Retire l'effet de tous les joueurs actuellement gérés (fin de partie / feature désactivée). */
    private void nettoyerTousLesEffets() {
        if (geresParCeSysteme.isEmpty()) {
            return;
        }
        for (UUID id : geresParCeSysteme) {
            Player joueur = plugin.getServer().getPlayer(id);
            if (joueur != null) {
                joueur.removePotionEffect(PotionEffectType.NIGHT_VISION);
            }
        }
        geresParCeSysteme.clear();
    }
}
