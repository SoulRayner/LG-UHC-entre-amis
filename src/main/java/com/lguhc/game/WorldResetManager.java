package com.lguhc.game;

import com.lguhc.LGUHCPlugin;
import com.lguhc.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Random;

/**
 * Régénère le monde de jeu (carte WorldPainter) entre deux parties : le dossier du
 * monde vivant est supprimé puis remplacé par une copie fraîche d'un dossier "modèle"
 * jamais chargé par le serveur (voir config.yml -> regeneration-monde.dossier-modele).
 *
 * Si `nouvelle-seed-a-chaque-partie` est activé, le level.dat de cette copie est ensuite
 * supprimé pour forcer Bukkit à en régénérer un (avec une seed aléatoire) au rechargement
 * du monde. Les fichiers de région (region/*.mca), eux, ne sont jamais touchés : le relief
 * et les biomes peints dans WorldPainter restent donc identiques à chaque partie. Seuls
 * les chunks que WorldPainter a laissés "à peupler par Minecraft" (voir config.yml) sont
 * concernés par le changement de seed, et se repeuplent donc différemment (minerais, lacs,
 * structures) à chaque régénération.
 *
 * Toutes les opérations fichiers (potentiellement lourdes en I/O sur une grosse carte) sont
 * faites hors du thread principal ; seuls le déchargement et le rechargement du monde, qui
 * doivent obligatoirement passer par l'API Bukkit, repassent sur le thread principal.
 */
public class WorldResetManager {

    private final LGUHCPlugin plugin;
    private final Random random = new Random();

    private static final int TENTATIVES_SUPPRESSION = 6;
    private static final long DELAI_ENTRE_TENTATIVES_MS = 500;
    private static final long DELAI_AVANT_SUPPRESSION_TICKS = 10L; // 0.5s après le déchargement

    private boolean active = true;
    private String dossierModele = "world_template";
    private boolean nouvelleSeedAleatoire = true;
    private boolean autoApresReset = true;

    private volatile boolean regenEnCours = false;

    public WorldResetManager(LGUHCPlugin plugin) {
        this.plugin = plugin;
    }

    public void charger(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        this.active = section.getBoolean("active", true);
        this.dossierModele = section.getString("dossier-modele", "world_template");
        this.nouvelleSeedAleatoire = section.getBoolean("nouvelle-seed-a-chaque-partie", true);
        this.autoApresReset = section.getBoolean("auto-apres-reset", true);
    }

    public boolean isActive() {
        return active;
    }

    public boolean isAutoApresReset() {
        return autoApresReset;
    }

    /** Vrai pendant toute la durée d'une régénération (le monde peut être momentanément déchargé). */
    public boolean isRegenEnCours() {
        return regenEnCours;
    }

    /**
     * Déclenche la régénération du monde de jeu. `demandeur` peut être null pour un appel
     * automatique (ex: après /lg stop) : les messages partent alors uniquement en console.
     * Ne fait rien si une régénération est déjà en cours, si la fonctionnalité est
     * désactivée, ou si une partie est actuellement en cours (voir GameManager.demarrer()
     * qui refuse de son côté de démarrer tant que regenEnCours est vrai).
     */
    public void regenerer(CommandSender demandeur) {
        if (!active) {
            avertir(demandeur, "&cLa régénération du monde est désactivée dans config.yml.");
            return;
        }
        if (regenEnCours) {
            avertir(demandeur, "&cUne régénération est déjà en cours, patientez.");
            return;
        }
        if (plugin.getGameManager().estEnCours()) {
            avertir(demandeur, "&cImpossible de régénérer le monde pendant une partie en cours.");
            return;
        }

        String nomMonde = plugin.getConfig().getString("monde.nom", "world");
        Path racineServeur = Bukkit.getWorldContainer().toPath();
        Path dossierVivant = racineServeur.resolve(nomMonde);
        Path dossierModelePath = racineServeur.resolve(dossierModele);

        if (!Files.isDirectory(dossierModelePath)) {
            avertir(demandeur, "&cDossier modèle introuvable : " + dossierModelePath
                    + " &7(placez-y une copie vierge de votre export WorldPainter, jamais chargée par le serveur).");
            return;
        }

        regenEnCours = true;
        avertir(demandeur, "&e⏳ Régénération du monde de jeu en cours...");

        // 1) Thread principal (obligatoire pour toute API Bukkit) : évacuer les joueurs
        //    éventuellement encore présents dans le monde de jeu, puis le décharger avant
        //    de toucher à ses fichiers sur le disque.
        Bukkit.getScheduler().runTask(plugin, () -> {
            World mondeVivant = Bukkit.getWorld(nomMonde);
            if (mondeVivant != null) {
                Location repli = emplacementDeRepli();
                for (Player p : new ArrayList<>(mondeVivant.getPlayers())) {
                    p.teleport(repli);
                }
                Bukkit.unloadWorld(mondeVivant, false);
            }

            // 2) Hors thread principal : suppression + copie de fichiers. Un petit délai avant
            //    de toucher au disque : sous Windows, les fichiers de région ne se libèrent pas
            //    toujours instantanément après unloadWorld() (verrou tenu encore une fraction de
            //    seconde, parfois plus avec un antivirus qui scanne).
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                try {
                    supprimerRecursivementAvecRetry(dossierVivant);
                    copierRecursivement(dossierModelePath, dossierVivant);
                    if (nouvelleSeedAleatoire) {
                        // Forcer Bukkit à écrire un level.dat neuf (donc une nouvelle seed)
                        // au rechargement, sans toucher aux fichiers de région du modèle.
                        Files.deleteIfExists(dossierVivant.resolve("level.dat"));
                        Files.deleteIfExists(dossierVivant.resolve("level.dat_old"));
                        Files.deleteIfExists(dossierVivant.resolve("session.lock"));
                    }
                } catch (IOException e) {
                    plugin.getLogger().severe("Echec de la régénération du monde : " + e.getMessage());
                    regenEnCours = false;
                    avertir(demandeur, "&cLa régénération a échoué (voir la console), le monde n'a pas été rechargé.");
                    return;
                }

                // 3) Retour sur le thread principal pour recréer/charger le monde via Bukkit.
                Bukkit.getScheduler().runTask(plugin, () -> {
                    WorldCreator createur = new WorldCreator(nomMonde);
                    if (nouvelleSeedAleatoire) {
                        createur.seed(random.nextLong());
                    }
                    // Empêche Minecraft de générer des structures (villages, mineshafts, temples,
                    // forteresses...) quand les chunks se "populate" au fur et à mesure que les
                    // joueurs explorent. Sans ça, ce flag repart par défaut à "true" à chaque fois
                    // qu'on recrée le level.dat ici. N'affecte que les chunks pas encore populate :
                    // n'efface pas une structure déjà apparue sur une partie précédente si le monde
                    // vivant n'a pas été régénéré depuis (mais le dossier modèle, lui, n'est jamais
                    // chargé par le serveur et n'en contient donc jamais).
                    createur.generateStructures(false);
                    createur.createWorld();
                    regenEnCours = false;
                    avertir(demandeur, "&a✔ Monde de jeu régénéré et prêt.");
                });
            }, DELAI_AVANT_SUPPRESSION_TICKS);
        });
    }

    /** Le monde lobby s'il est configuré et chargé, sinon le premier monde du serveur (jamais null en pratique). */
    private Location emplacementDeRepli() {
        String nomLobby = plugin.getConfig().getString("monde.lobby", null);
        World lobby = (nomLobby == null || nomLobby.isEmpty()) ? null : Bukkit.getWorld(nomLobby);
        World repli = lobby != null ? lobby : Bukkit.getWorlds().get(0);
        return repli.getSpawnLocation();
    }

    private void avertir(CommandSender demandeur, String message) {
        if (demandeur != null) {
            demandeur.sendMessage(Msg.c(message));
        }
        Bukkit.getConsoleSender().sendMessage(Msg.c("&8[WorldReset] " + message));
    }

    // ================= Utilitaires fichiers =================

    /**
     * Comme supprimerRecursivement, mais réessaie plusieurs fois en cas de verrou fichier
     * (typiquement Windows, où la libération du handle après unloadWorld() peut prendre
     * un instant). Abandonne et relance la dernière erreur si toutes les tentatives échouent.
     */
    private void supprimerRecursivementAvecRetry(Path dossier) throws IOException {
        IOException derniereErreur = null;
        for (int tentative = 1; tentative <= TENTATIVES_SUPPRESSION; tentative++) {
            try {
                supprimerRecursivement(dossier);
                return;
            } catch (IOException e) {
                derniereErreur = e;
                if (tentative < TENTATIVES_SUPPRESSION) {
                    plugin.getLogger().warning("Suppression du monde bloquée (tentative " + tentative
                            + "/" + TENTATIVES_SUPPRESSION + "), nouvel essai dans " + DELAI_ENTRE_TENTATIVES_MS + "ms : " + e.getMessage());
                    try {
                        Thread.sleep(DELAI_ENTRE_TENTATIVES_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                }
            }
        }
        throw derniereErreur;
    }

    private void supprimerRecursivement(Path dossier) throws IOException {
        if (!Files.exists(dossier)) {
            return;
        }
        Files.walkFileTree(dossier, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path fichier, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(fichier);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void copierRecursivement(Path source, Path destination) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(destination.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path fichier, BasicFileAttributes attrs) throws IOException {
                Files.copy(fichier, destination.resolve(source.relativize(fichier)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
