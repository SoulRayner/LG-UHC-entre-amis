package com.lguhc.game;

import com.lguhc.LGUHCPlugin;
import com.lguhc.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Centralise tout ce qui doit se passer après la mort "apparente" d'un joueur. La mort n'est
 * jamais annoncée immédiatement. Ordre de priorité de résurrection :
 *  1) Ancien (tué par un Loup-Garou) / Idiot du Village (tué par un Villageois ou un Solitaire) :
 *     réapparition INSTANTANÉE, sans passer par la suite de ce processus.
 *  2) Sinon, le joueur passe en spectateur et on attend 5 secondes.
 *  3) Proposition à l'Infect Père des Loups (8 secondes), si la victime a été tuée par un membre
 *     des Loups-Garous et que son pouvoir est encore disponible.
 *  4) A la suite (ou immédiatement si 3) ne s'applique pas), proposition à la Sorcière (8 secondes).
 * La mort ne devient définitive et publique qu'à l'issue de tout ce processus.
 */
public class DeathManager {

    /** Fenêtre de l'Infect Père des Loups : 8 secondes pour cliquer, comme la Sorcière. */
    private static final long FENETRE_INFECTION_TICKS = 8 * 20L;
    /** Fenêtre de la Sorcière quand l'Infect Père n'entre pas en jeu : 8 secondes. */
    private static final long FENETRE_COURTE_TICKS = 8 * 20L;
    /** Fenêtre totale quand l'Infect Père ET la Sorcière peuvent intervenir : 8s (Infect Père) + 8s (Sorcière). */
    private static final long FENETRE_LONGUE_TICKS = FENETRE_INFECTION_TICKS + FENETRE_COURTE_TICKS;
    private static final long FENETRE_CHASSEUR_TICKS = 30 * 20L;
    /**
     * Délai d'attente après une mort "apparente" avant de proposer une résurrection à l'Infect
     * Père des Loups ou à la Sorcière. Ne concerne PAS l'Ancien ni l'Idiot du Village, qui
     * réapparaissent instantanément (voir ordre de priorité dans debuterFenetreMort).
     */
    private static final long DELAI_AVANT_PROPOSITION_TICKS = 5 * 20L;

    private UUID victimeCorrompueEnAttente = null;
    private UUID infectPereEnAttente = null;

    /** Stuff (inventaire + armure) intercepté à la mort réelle, en attente de restitution ou de chute au sol. */
    private final Map<UUID, List<ItemStack>> stuffSauvegarde = new HashMap<>();
    /** Lieu exact de la mort, capturé avant le passage en spectateur (qui peut faire dériver le joueur). */
    private final Map<UUID, Location> dernierLieuMort = new HashMap<>();

    /** Point d'entrée : un joueur vient "d'apparemment" mourir. */
    public void debuterFenetreMort(LGUHCPlugin plugin, GamePlayer gp, Camp campResponsable, String raisonFinale) {
        GameManager gm = plugin.getGameManager();

        // Sauvegarde du lieu de mort réel (avant le passage en spectateur, qui peut
        // faire dériver le joueur loin de l'endroit où il est vraiment mort).
        Player joueurPourLieu = gp.getPlayer();
        if (joueurPourLieu != null) {
            this.dernierLieuMort.put(gp.getUuid(), joueurPourLieu.getLocation());
        }

        // --- Ancien : survit à une attaque de Loup-Garou (1 seule fois) ---
        if (gp.getRole() == RoleType.ANCIEN && campResponsable == Camp.LOUPS && gp.getEtat("ancien_peut_ressusciter", true)) {
            gp.setEtat("ancien_peut_ressusciter", false);
            gp.setEtat("ancien_resistance_active", false);
            Player joueur = gp.getPlayer();
            if (joueur != null) {
                joueur.setHealth(joueur.getMaxHealth());
                joueur.teleport(gm.emplacementAleatoireDansBordure(joueur.getWorld()));
            }
            restaurerStuff(gp);
            return;
        }
        // --- Ancien tué par un non-Loup : pas de résurrection, le tueur est puni ---
        if (gp.getRole() == RoleType.ANCIEN && campResponsable != null && campResponsable != Camp.LOUPS) {
            GamePlayer tueur = gm.getGamePlayer(gp.getDernierTueur());
            if (tueur != null && tueur.getPlayer() != null) {
                Player tueurJoueur = tueur.getPlayer();
                for (org.bukkit.potion.PotionEffect eff : new java.util.ArrayList<>(tueurJoueur.getActivePotionEffects())) {
                    tueurJoueur.removePotionEffect(eff.getType());
                }
                tueurJoueur.setHealth(Math.max(1.0, tueurJoueur.getHealth() / 2.0));
                tueur.setEtat("pouvoirs_bloques", true);
                gm.diffuser("&6" + tueur.getNom() + " a tué l'Ancien sans être un Loup-Garou... et le paie très cher !");
            }
        }
        // --- Idiot du Village : SECONDE CHANCE UNIQUE. La première fois qu'il est tué par un
        // Villageois ou un rôle Solitaire (jamais par un Loup-Garou), il survit mais perd 2 cœurs
        // de vie maximum de façon PERMANENTE (10♥ -> 8♥). Une fois cette protection consommée,
        // il redevient mortel comme tout le monde, y compris face aux Loups-Garous. ---
        if (gp.getRole() == RoleType.IDIOT_VILLAGE && campResponsable != null && campResponsable != Camp.LOUPS
                && gp.getEtat("idiot_protection_disponible", true)) {
            gp.setEtat("idiot_protection_disponible", false);
            Player joueur = gp.getPlayer();
            if (joueur != null) {
                double nouveauMaxHealth = Math.max(2.0, joueur.getMaxHealth() - 4.0);
                joueur.setMaxHealth(nouveauMaxHealth);
                joueur.setHealth(nouveauMaxHealth);
                joueur.teleport(gm.emplacementAleatoireDansBordure(joueur.getWorld()));
            }
            restaurerStuff(gp);
            gm.diffuser("&6&l" + gp.getNom() + " se relève, amoindri ! C'était l'Idiot du Village.");
            return;
        }

        gp.setEnAttenteMort(true);
        Player joueur = gp.getPlayer();
        if (joueur != null) {
            joueur.setGameMode(GameMode.SPECTATOR);
            // Renvoyé au lobby pendant TOUTE la fenêtre d'attente (5s + jusqu'à 16s de
            // proposition Infect Père/Sorcière) : évite qu'il entende des informations de jeu
            // via la proximité vocale (Mumble) alors qu'il a encore une chance d'être réanimé.
            // S'il ne l'est pas, GameManager#finaliserMort le renverra spectateur dans le monde
            // de jeu (à l'endroit exact de sa mort, voir getDernierLieuMort ci-dessous) ; s'il
            // est réanimé, LGCommand le téléportera lui-même autour du (0,0) du monde de jeu.
            Location emplacementLobby = gm.getEmplacementLobby();
            if (emplacementLobby != null) {
                joueur.teleport(emplacementLobby);
            }
            Msg.envoyer(joueur, "&7Vous patientez au lobby, en attente d'une éventuelle réanimation...");
        }

        // --- Ordre de priorité de résurrection, 3) et 4) : après un délai de 5 secondes
        // (l'Ancien et l'Idiot du Village, ci-dessus, ont déjà été traités instantanément et
        // n'ont pas atteint ce point), on propose l'Infection puis, à sa suite, la Sorcière. ---
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (gp.isEnAttenteMort()) {
                ouvrirFenetresResurrection(plugin, gp, campResponsable, raisonFinale);
            }
        }, DELAI_AVANT_PROPOSITION_TICKS);
    }

    /**
     * Ordre de priorité de résurrection, 3) et 4) : propose l'Infection à l'Infect Père des
     * Loups si la victime a été tuée par un membre des Loups-Garous et que son pouvoir est
     * encore disponible, puis (à sa suite, ou immédiatement si l'Infect Père n'est pas
     * éligible) la résurrection par la Sorcière. Appelée 5 secondes après la mort apparente.
     */
    private void ouvrirFenetresResurrection(LGUHCPlugin plugin, GamePlayer gp, Camp campResponsable, String raisonFinale) {
        GameManager gm = plugin.getGameManager();

        // isVivant() reste vrai tant que la mort n'est pas finalisée (voir finaliserMort) : un
        // Infect Père actuellement dans SA PROPRE fenêtre de mort différée (déjà en spectateur,
        // en train d'attendre sa propre résurrection) apparaîtrait donc encore dans
        // getJoueursVivants(). On l'exclut explicitement avec !isEnAttenteMort() : mort ou en
        // spectateur, il ne peut pas utiliser son pouvoir (ni sur un autre joueur, ni sur lui-même).
        GamePlayer infectPere = gm.getJoueursVivants().stream()
                .filter(g -> g.getRole() == RoleType.INFECT_PERE_LOUPS && !g.isEnAttenteMort())
                .findFirst().orElse(null);

        // Seule condition désormais : la victime meurt d'un membre du camp des Loups-Garous
        // (l'Infect Père lui-même inclus). Plus besoin de corruption ni de proximité préalable.
        // Si la victime était déjà Loups (Infect Père compris), elle est simplement ressuscitée
        // telle quelle (voir LGCommand#infecterPere) ; sinon elle devient Loup-Garou.
        boolean tueParLoup = campResponsable == Camp.LOUPS;
        boolean pouvoirInfectDisponible = infectPere != null && !infectPere.getEtat("infect_pouvoir_utilise", false);
        boolean infectEligible = tueParLoup && pouvoirInfectDisponible;

        if (infectEligible) {
            proposerInfection(plugin, infectPere, gp);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (gp.isEnAttenteMort()) {
                    ouvrirFenetreSorciere(plugin, gp);
                }
            }, FENETRE_INFECTION_TICKS);
            Bukkit.getScheduler().runTaskLater(plugin, () -> finaliserSiToujoursMort(plugin, gp, campResponsable, raisonFinale), FENETRE_LONGUE_TICKS);
        } else {
            ouvrirFenetreSorciere(plugin, gp);
            Bukkit.getScheduler().runTaskLater(plugin, () -> finaliserSiToujoursMort(plugin, gp, campResponsable, raisonFinale), FENETRE_COURTE_TICKS);
        }
    }

    private void ouvrirFenetreSorciere(LGUHCPlugin plugin, GamePlayer gp) {
        GamePlayer sorciere = plugin.getGameManager().getJoueursVivants().stream()
                .filter(g -> g.getRole() == RoleType.SORCIERE
                        && g.getEtat("sorciere_resurrection_dispo", false)
                        // La Sorcière ne peut pas se ressusciter elle-même : si c'est elle qui
                        // vient de mourir, on ne lui propose pas sa propre résurrection.
                        && g != gp
                        // Ni utiliser son pouvoir si ELLE-MEME est actuellement dans sa propre
                        // fenêtre de mort différée (déjà en spectateur, en train d'attendre sa
                        // propre résurrection) : isVivant() reste vrai jusqu'à la finalisation,
                        // isEnAttenteMort() est le bon indicateur ici.
                        && !g.isEnAttenteMort())
                .findFirst().orElse(null);
        if (sorciere != null && sorciere.getPlayer() != null) {
            String commande = "/lg ressusciter " + gp.getNom();
            Msg.envoyerCliquable(sorciere.getPlayer(),
                    "&d&l✝ Le joueur " + gp.getNom() + " vient de mourir. &d&nCliquez ici pour le ressusciter&5 (1 seule fois dans la partie, 8 secondes pour décider).",
                    commande, "&d" + commande);
        }
    }

    private void finaliserSiToujoursMort(LGUHCPlugin plugin, GamePlayer gp, Camp campResponsable, String raison) {
        if (!gp.isEnAttenteMort()) {
            return; // sauvé entre temps par la Sorcière ou l'Infect Père des Loups
        }
        plugin.getGameManager().finaliserMort(gp, raison, campResponsable);
    }

    public void annoncerMort(LGUHCPlugin plugin, GamePlayer gp, String raisonAffichage) {
        GameManager gm = plugin.getGameManager();

        String role = gp.getRole() != null ? gp.getRole().getNomAffiche() : "???";
        // Membre du Couple (les deux, y compris celui mort "normalement" en premier) : un cœur
        // à gauche du message de mort. Voir CoupleManager#traiterMortPourCouple pour le flag
        // "mort_de_chagrin_pour", posé uniquement sur le second amoureux à mourir.
        String prefixeCoeur = gp.estEnCouple() ? "&d♥ " : "";
        String nomAmoureuxMort = gp.getEtat("mort_de_chagrin_pour", "");

        if (!nomAmoureuxMort.isEmpty()) {
            gm.diffuser(prefixeCoeur + "&8☠ &7Le joueur &f" + gp.getNom() + " &7est mort de chagrin suite à la mort de &f" + nomAmoureuxMort + "&7, il était &f" + role + "&7.");
        } else {
            gm.diffuser(prefixeCoeur + "&8☠ &7Le joueur &f" + gp.getNom() + " &7est mort, son rôle était &f" + role + "&7.");
        }

        if (gp.getDernierMot() != null && !gp.getDernierMot().isEmpty()) {
            gm.diffuser("&8✎ &7Dernier mot de " + gp.getNom() + " : &f\"" + gp.getDernierMot() + "\"");
        }
        // Le joueur est déjà en spectateur depuis le début de la fenêtre de mort.
    }

    public void proposerTirChasseur(GamePlayer chasseur) {
        chasseur.setEtat("chasseur_fenetre_ouverte", true);
        Player p = chasseur.getPlayer();
        Msg.envoyer(p, "&c&lVous êtes le Chasseur ! Vous avez 30 secondes pour tirer sur un joueur : &6/lg tirer <joueur>");
        LGUHCPlugin plugin = LGUHCPlugin.getInstance();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (chasseur.getEtat("chasseur_fenetre_ouverte", false)) {
                chasseur.setEtat("chasseur_fenetre_ouverte", false);
                Msg.envoyer(p, "&7Trop tard, votre fenêtre de tir est refermée.");
            }
        }, FENETRE_CHASSEUR_TICKS);
    }

    /**
     * A appeler quand l'Infect Père des Loups peut intervenir sur une mort :
     * soit une victime corrompue tuée par un Loup (elle rejoindra la meute),
     * soit un membre des Loups-Garous - lui-même y compris - tué par un
     * autre Loup (il sera simplement ressuscité, sans changement de camp).
     *
     * NB : le cas {@code victime == infectPere} ci-dessous ne peut plus se produire depuis
     * l'ajout du filtre {@code !isEnAttenteMort()} sur infectPere dans
     * {@link #ouvrirFenetresResurrection} : l'Infect Père ne peut plus se ressusciter
     * lui-même puisqu'il ne peut pas utiliser son pouvoir tant qu'il est lui-même mort/en
     * spectateur. Laissé en place au cas où ce filtre serait un jour assoupli.
     */
    public void proposerInfection(LGUHCPlugin plugin, GamePlayer infectPere, GamePlayer victime) {
        this.infectPereEnAttente = infectPere.getUuid();
        this.victimeCorrompueEnAttente = victime.getUuid();
        String texte;
        if (victime == infectPere) {
            texte = "&c&lVous venez de mourir ! &4&nCliquez ici pour vous ressusciter vous-même&c (1 seule fois dans la partie, 8 secondes).";
        } else if (victime.getCamp() == Camp.LOUPS) {
            texte = "&c&lVotre allié " + victime.getNom() + " vient de mourir ! &4&nCliquez ici pour le ressusciter&c (1 seule fois dans la partie, 8 secondes).";
        } else {
            texte = "&c&lVotre victime, " + victime.getNom() + ", vient de mourir sous les crocs d'un Loup ! &4&nCliquez ici pour l'infecter&c (1 seule fois dans la partie, 8 secondes).";
        }
        Msg.envoyerCliquable(infectPere.getPlayer(), texte, "/lg infecter", "&c/lg infecter");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (victime.getUuid().equals(victimeCorrompueEnAttente)) {
                victimeCorrompueEnAttente = null;
                infectPereEnAttente = null;
            }
        }, FENETRE_INFECTION_TICKS);
    }

    public boolean peutInfecterMaintenant(GamePlayer infectPere) {
        return infectPereEnAttente != null && infectPereEnAttente.equals(infectPere.getUuid()) && victimeCorrompueEnAttente != null;
    }

    public UUID getVictimeCorrompueEnAttente() {
        return victimeCorrompueEnAttente;
    }

    public void consommerFenetreInfection() {
        victimeCorrompueEnAttente = null;
        infectPereEnAttente = null;
    }

    // ================= Stuff (bug de dupe/perte à la mort) =================

    /**
     * A appeler dans le listener de PlayerDeathEvent, AVANT de vider
     * {@code event.getDrops()} : intercepte tout ce que la mort réelle
     * s'apprêtait à faire tomber au sol (inventaire + armure confondus) et
     * le met de côté. Rien n'apparaît au sol tout de suite (mort différée),
     * mais on peut soit le rendre au joueur s'il est réanimé (Ancien, Idiot
     * du Village, Sorcière, Infect Père), soit le faire tomber au sol une
     * fois la mort devenue définitive.
     */
    public void sauvegarderStuff(GamePlayer gp, List<ItemStack> drops) {
        stuffSauvegarde.put(gp.getUuid(), new ArrayList<>(drops));
    }

    /**
     * Consulte (SANS le retirer) le lieu de mort mémorisé pour ce joueur. A appeler par
     * {@link GameManager#finaliserMort} avant dropperStuff() (qui, lui, le retire) pour
     * renvoyer le joueur spectateur dans le monde de jeu à l'endroit exact de sa mort, puisque
     * entre-temps il patientait au lobby (voir debuterFenetreMort ci-dessus).
     */
    public Location getDernierLieuMort(UUID uuid) {
        return dernierLieuMort.get(uuid);
    }

    /**
     * Rend son stuff exact à un joueur réanimé (armure remise dans les
     * bonnes cases, reste de l'inventaire complété par-dessus ce qu'il a
     * pu ramasser entre-temps).
     */
    public void restaurerStuff(GamePlayer gp) {
        List<ItemStack> items = stuffSauvegarde.remove(gp.getUuid());
        dernierLieuMort.remove(gp.getUuid());
        if (items == null) {
            return;
        }
        // On ne restaure PAS tout de suite : à cet instant, le joueur est encore en train
        // d'être "tué" côté serveur (PlayerDeathEvent n'est qu'une étape du processus de mort
        // vanilla) et le respawn qui suit dans la foulée vide de nouveau son inventaire, ce qui
        // effaçait ce qu'on venait juste de lui redonner (seuls quelques objets survivaient par
        // coïncidence de timing, d'où les steaks restants mais cassés). On attend donc que tout
        // ce cycle mort/respawn soit terminé (tick suivant) avant de rendre le stuff.
        Bukkit.getScheduler().runTask(LGUHCPlugin.getInstance(), () -> {
            Player joueur = gp.getPlayer();
            if (joueur == null) {
                return;
            }
            ItemStack[] armure = new ItemStack[4];
            List<ItemStack> resteInventaire = new ArrayList<>();
            for (ItemStack item : items) {
                if (item == null) {
                    continue;
                }
                String nom = item.getType().name();
                if (nom.contains("BOOTS")) {
                    armure[0] = item;
                } else if (nom.contains("LEGGINGS")) {
                    armure[1] = item;
                } else if (nom.contains("CHESTPLATE")) {
                    armure[2] = item;
                } else if (nom.contains("HELMET")) {
                    armure[3] = item;
                } else {
                    resteInventaire.add(item);
                }
            }
            // On écrase directement les 4 emplacements d'armure avec ce qu'il portait avant sa
            // mort (l'armure y est retombée juste après un respawn tout frais, donc vide) plutôt
            // que de vérifier si l'emplacement est libre : c'est justement ce test qui échouait
            // et faisait retomber les pièces d'armure comme objets normaux dans la hotbar,
            // écrasant ce qui s'y trouvait déjà.
            joueur.getInventory().setArmorContents(armure);
            for (ItemStack item : resteInventaire) {
                Map<Integer, ItemStack> surplus = joueur.getInventory().addItem(item);
                for (ItemStack itemRestant : surplus.values()) {
                    joueur.getWorld().dropItemNaturally(joueur.getLocation(), itemRestant);
                }
            }
        });
    }

    /**
     * Fait tomber au sol, à l'endroit exact de la mort, le stuff d'un joueur
     * dont la mort vient de devenir définitive. A appeler depuis
     * {@link GameManager#finaliserMort}.
     */
    public void dropperStuff(GamePlayer gp) {
        List<ItemStack> items = stuffSauvegarde.remove(gp.getUuid());
        Location lieu = dernierLieuMort.remove(gp.getUuid());
        if (items == null || lieu == null || lieu.getWorld() == null) {
            return;
        }
        for (ItemStack item : items) {
            if (item != null) {
                lieu.getWorld().dropItemNaturally(lieu, item);
            }
        }
    }
}
