package com.lguhc.commands;

import com.lguhc.LGUHCPlugin;
import com.lguhc.game.Aura;
import com.lguhc.game.Camp;
import com.lguhc.game.GameManager;
import com.lguhc.game.GamePlayer;
import com.lguhc.game.RoleType;
import com.lguhc.roles.loups.LoupPerfideRole;
import com.lguhc.roles.villageois.AnalysteRole;
import com.lguhc.roles.villageois.ChasseurRole;
import com.lguhc.roles.villageois.PetiteFilleRole;
import com.lguhc.roles.villageois.RenardRole;
import com.lguhc.roles.villageois.SoeursRole;
import com.lguhc.util.InventaireUtil;
import com.lguhc.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

public class LGCommand implements CommandExecutor {

    private final LGUHCPlugin plugin;

    public LGCommand(LGUHCPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            envoyerAide(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "join":
            case "rejoindre":
                return joueurRequis(sender, p -> plugin.getGameManager().rejoindre(p));
            case "leave":
            case "quitter":
                return joueurRequis(sender, p -> plugin.getGameManager().quitter(p));
            case "start":
            case "demarrer":
                if (!verifierHote(sender)) return true;
                plugin.getGameManager().demarrer(sender);
                return true;
            case "config":
                if (!verifierHote(sender)) return true;
                return joueurRequis(sender, plugin.getConfigMenu()::ouvrirMenuPrincipal);
            case "stop":
            case "reset":
                if (!verifierHote(sender)) return true;
                plugin.getGameManager().reinitialiser();
                sender.sendMessage(Msg.c("&aPartie réinitialisée."));
                return true;
            case "role":
                return joueurRequis(sender, this::afficherRole);
            case "auras":
                return joueurRequis(sender, this::afficherAuras);
            case "compo":
                return joueurRequis(sender, this::afficherCompo);
            case "voir":
                return joueurRequis(sender, p -> voyanteVoir(p, args));
            case "flairer":
                return joueurRequis(sender, p -> renardFlairer(p, args));
            case "observer":
                return joueurRequis(sender, p -> analysteObserver(p, args));
            case "analyser":
                return joueurRequis(sender, p -> analysteAnalyser(p, args));
            case "ressusciter":
                return joueurRequis(sender, p -> sorciereRessusciter(p, args));
            case "tirer":
                return joueurRequis(sender, p -> chasseurTire(p, args));
            case "perfide":
                return joueurRequis(sender, this::loupPerfide);
            case "espionner":
                return joueurRequis(sender, this::petiteFilleEspionner);
            case "hurler":
                return joueurRequis(sender, this::loupHurler);
            case "infecter":
                return joueurRequis(sender, this::infecterPere);
            case "couple":
                return joueurRequis(sender, p -> cupidonLove(p, args));
            case "choisir":
                return joueurRequis(sender, p -> sauvageModele(p, args));
            case "color":
            case "couleur":
                return joueurRequis(sender, p -> ouvrirMenuCouleur(p, args));
            case "soeur":
                return joueurRequis(sender, p -> soeurMessage(p, args));
            case "vote":
                return joueurRequis(sender, p -> villageVote(p, args));
            case "groupe":
                return joueurRequis(sender, p -> groupeMessage(p, args));
            case "admin":
                return traiterAdmin(sender, args);
            case "aide":
            case "help":
                envoyerAide(sender);
                return true;
            default:
                sender.sendMessage(Msg.c("&cSous-commande inconnue. Tapez &e/lg aide"));
                return true;
        }
    }

    // ================= Utilitaires =================

    private interface ActionJoueur {
        void executer(Player p);
    }

    private boolean joueurRequis(CommandSender sender, ActionJoueur action) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Msg.c("&cCette commande est réservée aux joueurs."));
            return true;
        }
        action.executer((Player) sender);
        return true;
    }

    private boolean verifierHote(CommandSender sender) {
        if (!sender.hasPermission("lguhc.host")) {
            sender.sendMessage(Msg.c("&cVous n'avez pas la permission de faire ça."));
            return false;
        }
        return true;
    }

    private GamePlayer moi(Player p) {
        GamePlayer gp = plugin.getGameManager().getGamePlayer(p);
        if (gp == null) {
            Msg.envoyer(p, "&cVous n'êtes pas inscrit à la partie (utilisez /lg join).");
        }
        return gp;
    }

    private GamePlayer cible(Player demandeur, String nom) {
        Player cibleJoueur = Bukkit.getPlayerExact(nom);
        if (cibleJoueur == null) {
            Msg.envoyer(demandeur, "&cJoueur introuvable : " + nom);
            return null;
        }
        GamePlayer gp = plugin.getGameManager().getGamePlayer(cibleJoueur);
        if (gp == null) {
            Msg.envoyer(demandeur, "&cCe joueur ne participe pas à la partie.");
            return null;
        }
        return gp;
    }

    private boolean verifierRole(Player p, GamePlayer gp, RoleType attendu) {
        if (gp.getRole() != attendu) {
            Msg.envoyer(p, "&cVous n'avez pas ce pouvoir.");
            return false;
        }
        if (!gp.isVivant() || gp.isEnAttenteMort() || p.getGameMode() == GameMode.SPECTATOR) {
            Msg.envoyer(p, "&cLes morts n'ont plus de pouvoirs.");
            return false;
        }
        if (gp.getEtat("pouvoirs_bloques", false)) {
            Msg.envoyer(p, "&cVous avez tué l'Ancien sans être un Loup-Garou : vos pouvoirs sont perdus pour le reste de la partie.");
            return false;
        }
        if (plugin.getGameManager().pouvoirsVillageBloques() && gp.getCamp() == Camp.VILLAGE) {
            Msg.envoyer(p, "&cLes pouvoirs du Village sont temporairement bloqués.");
            return false;
        }
        return true;
    }

    // ================= Pouvoirs Village =================

    private void voyanteVoir(Player p, String[] args) {
        GamePlayer gp = moi(p);
        if (gp == null || !verifierRole(p, gp, RoleType.VOYANTE)) return;
        if (args.length < 2) {
            Msg.envoyer(p, "&cUsage : /lg voir <joueur>");
            return;
        }
        int episodeActuel = plugin.getGameManager().getEpisodeActuel();
        if (gp.getEtat("voyante_dernier_episode", -1) == episodeActuel) {
            Msg.envoyer(p, "&cVous avez déjà utilisé votre pouvoir cet épisode.");
            return;
        }
        GamePlayer cibleGP = cible(p, args[1]);
        if (cibleGP == null || !cibleGP.isVivant() || cibleGP.getRole() == null) {
            Msg.envoyer(p, "&cJoueur invalide.");
            return;
        }
        RoleType vrai = cibleGP.getRole();
        if (vrai == RoleType.VOYANTE) {
            // La cible est la Voyante elle-même : son vrai rôle ne doit jamais
            // apparaître dans les possibilités affichées (voir plus bas), donc
            // ce cas est simplement interdit plutôt que de risquer une fuite.
            Msg.envoyer(p, "&cVous ne pouvez pas utiliser votre pouvoir sur vous-même.");
            return;
        }
        gp.setEtat("voyante_dernier_episode", episodeActuel);

        List<RoleType> composition = new ArrayList<>(plugin.getGameManager().getCompositionUtilisee());
        composition.remove(vrai);
        // Le rôle Voyante ne doit jamais apparaître comme leurre.
        composition.removeIf(r -> r == RoleType.VOYANTE);
        java.util.Collections.shuffle(composition);

        RoleType leurre = composition.isEmpty() ? vrai : composition.get(0);

        boolean unVillageoisPresent = vrai.getCampDepart() == Camp.VILLAGE || leurre.getCampDepart() == Camp.VILLAGE;
        if (!unVillageoisPresent) {
            RoleType leurreVillage = composition.stream()
                    .filter(r -> r.getCampDepart() == Camp.VILLAGE)
                    .findFirst().orElse(null);
            if (leurreVillage != null) {
                leurre = leurreVillage;
            }
        }

        List<String> lesDeux = new ArrayList<>(java.util.Arrays.asList(vrai.getNomAffiche(), leurre.getNomAffiche()));
        java.util.Collections.shuffle(lesDeux);
        Msg.envoyer(p, "&d🔮 " + cibleGP.getNom() + " est en réalité l'un de ces deux rôles : &f" + lesDeux.get(0) + " &7ou &f" + lesDeux.get(1));
    }

    private void renardFlairer(Player p, String[] args) {
        GamePlayer gp = moi(p);
        if (gp == null || !verifierRole(p, gp, RoleType.RENARD)) return;
        if (args.length < 2) {
            Msg.envoyer(p, "&cUsage : /lg flairer <joueur>");
            return;
        }
        if (!plugin.getGameManager().estNuit()) {
            Msg.envoyer(p, "&cVous ne pouvez flairer que la nuit.");
            return;
        }
        int nuitActuelle = plugin.getGameManager().getNumeroNuit();
        if (gp.getEtat("renard_derniere_nuit", -1) == nuitActuelle) {
            Msg.envoyer(p, "&cVous avez déjà flairé cette nuit.");
            return;
        }
        int utilisations = gp.getEtat("renard_utilisations", 0);
        if (utilisations >= RenardRole.UTILISATIONS_MAX) {
            Msg.envoyer(p, "&cVous avez déjà utilisé vos 3 flairages.");
            return;
        }
        GamePlayer cibleGP = cible(p, args[1]);
        if (cibleGP == null || !cibleGP.isVivant() || cibleGP.getPlayer() == null) return;
        if (p.getLocation().distance(cibleGP.getPlayer().getLocation()) > RenardRole.RAYON) {
            Msg.envoyer(p, "&cVous devez être à moins de 15 blocs de ce joueur.");
            return;
        }
        gp.setEtat("renard_derniere_nuit", nuitActuelle);
        Msg.envoyer(p, "&6Vous commencez à flairer " + cibleGP.getNom() + "... restez à moins de 15 blocs pendant " + RenardRole.SECONDES_A_RESTER_PROCHE + " secondes.");

        final int[] secondesEcoulees = {0};
        final org.bukkit.scheduler.BukkitTask[] tacheRef = new org.bukkit.scheduler.BukkitTask[1];
        tacheRef[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!gp.isVivant() || !cibleGP.isVivant() || p.getLocation().distance(cibleGP.getPlayer().getLocation()) > RenardRole.RAYON) {
                Msg.envoyer(p, "&cFlairage interrompu : vous vous êtes trop éloigné.");
                tacheRef[0].cancel();
                return;
            }
            secondesEcoulees[0]++;
            if (secondesEcoulees[0] >= RenardRole.SECONDES_A_RESTER_PROCHE) {
                tacheRef[0].cancel();
                // Le Renard doit détecter le vrai camp (Loups), pas juste l'Aura : un infecté par
                // l'Infect Père reste sur l'Aura de son rôle d'origine mais est un vrai Loup-Garou.
                boolean estLoup = cibleGP.getCamp() == Camp.LOUPS;
                int loupsTrouves = gp.getEtat("renard_loups_trouves", 0) + (estLoup ? 1 : 0);

                int nouvellesUtilisations = gp.getEtat("renard_utilisations", 0) + 1;

                gp.setEtat("renard_utilisations", nouvellesUtilisations);
                gp.setEtat("renard_loups_trouves", loupsTrouves);
                Msg.envoyer(p, "&6Flairage réussi sur " + cibleGP.getNom() + " ! (" + nouvellesUtilisations + "/" + RenardRole.UTILISATIONS_MAX + ")");
                if (nouvellesUtilisations >= RenardRole.UTILISATIONS_MAX) {
                    Msg.envoyer(p, "&6&lParmi vos 3 flairages, &e" + loupsTrouves + " &6&létaient des Loups-Garous !");
                }
            }
        }, 20L, 20L);
    }

    private void analysteObserver(Player p, String[] args) {
        GamePlayer gp = moi(p);
        if (gp == null || !verifierRole(p, gp, RoleType.ANALYSTE)) return;

        long secondesEcoulees = plugin.getGameManager().getTempsTotalEcouleSecondes();
        long secondesAvantDispo = AnalysteRole.MINUTES_AVANT_DISPONIBLE * 60L;
        if (secondesEcoulees < secondesAvantDispo) {
            long resteMinutes = (secondesAvantDispo - secondesEcoulees + 59) / 60;
            Msg.envoyer(p, "&cVotre pouvoir d'observation n'est disponible qu'à partir de " + AnalysteRole.MINUTES_AVANT_DISPONIBLE
                    + " minutes de jeu (encore " + resteMinutes + " min).");
            return;
        }
        if (args.length < 2) {
            Msg.envoyer(p, "&cUsage : /lg observer <joueur>");
            return;
        }

        int utilisations = gp.getEtat(AnalysteRole.CLE_OBSERVATIONS_UTILISEES, 0);
        if (utilisations >= AnalysteRole.UTILISATIONS_OBSERVATION_MAX) {
            Msg.envoyer(p, "&cVous avez déjà utilisé vos " + AnalysteRole.UTILISATIONS_OBSERVATION_MAX + " observations.");
            return;
        }
        long derniereObservationTs = gp.getEtat(AnalysteRole.CLE_DERNIERE_OBSERVATION_TS, 0L);
        long cooldownMs = AnalysteRole.COOLDOWN_OBSERVATION_MINUTES * 60L * 1000L;
        long depuisDerniere = System.currentTimeMillis() - derniereObservationTs;
        if (derniereObservationTs > 0 && depuisDerniere < cooldownMs) {
            long resteSecondes = (cooldownMs - depuisDerniere) / 1000L;
            Msg.envoyer(p, "&cEncore " + (resteSecondes / 60) + "m" + (resteSecondes % 60) + "s avant de pouvoir observer à nouveau.");
            return;
        }

        GamePlayer cibleGP = cible(p, args[1]);
        if (cibleGP == null || !cibleGP.isVivant() || cibleGP.getPlayer() == null) {
            Msg.envoyer(p, "&cCible invalide.");
            return;
        }
        if (cibleGP == gp) {
            Msg.envoyer(p, "&cVous ne pouvez pas vous observer vous-même.");
            return;
        }

        List<PotionEffectType> effetsDetectes = new ArrayList<>();
        for (PotionEffectType type : AnalysteRole.EFFETS_DETECTES) {
            if (cibleGP.getPlayer().hasPotionEffect(type)) {
                effetsDetectes.add(type);
            }
        }
        // Liste éventuellement vide mais toujours stockée (non nulle) : c'est ce marqueur qui
        // autorisera /lg analyser sur cette cible plus tard (voir AnalysteRole.CLE_EFFETS_PREFIXE).
        gp.setEtat(AnalysteRole.CLE_EFFETS_PREFIXE + cibleGP.getUuid(), effetsDetectes);
        gp.setEtat(AnalysteRole.CLE_OBSERVATIONS_UTILISEES, utilisations + 1);
        gp.setEtat(AnalysteRole.CLE_DERNIERE_OBSERVATION_TS, System.currentTimeMillis());
        gp.setAura(Aura.OBSCURE);

        boolean auMoinsUnEffet = !effetsDetectes.isEmpty();
        Msg.envoyer(p, "&6🔍 Observation de " + cibleGP.getNom() + " terminée (" + (utilisations + 1) + "/" + AnalysteRole.UTILISATIONS_OBSERVATION_MAX + ") : "
                + (auMoinsUnEffet ? "&aau moins un effet détecté." : "&7aucun effet détecté."));
        Msg.envoyer(p, "&7Votre Aura devient &1Obscure&7 : vous venez d'utiliser un pouvoir de détection.");
    }

    private void analysteAnalyser(Player p, String[] args) {
        GamePlayer gp = moi(p);
        if (gp == null || !verifierRole(p, gp, RoleType.ANALYSTE)) return;

        if (gp.getEtat(AnalysteRole.CLE_ANALYSE_UTILISEE, false)) {
            Msg.envoyer(p, "&cVous avez déjà utilisé votre unique analyse.");
            return;
        }
        if (args.length < 2) {
            Msg.envoyer(p, "&cUsage : /lg analyser <joueur>");
            return;
        }
        GamePlayer cibleGP = cible(p, args[1]);
        if (cibleGP == null) return;

        List<PotionEffectType> effets = gp.getEtat(AnalysteRole.CLE_EFFETS_PREFIXE + cibleGP.getUuid(), null);
        if (effets == null) {
            Msg.envoyer(p, "&cVous n'avez jamais observé ce joueur : impossible de l'analyser.");
            return;
        }

        gp.setEtat(AnalysteRole.CLE_ANALYSE_UTILISEE, true);
        gp.setAura(Aura.OBSCURE);

        if (effets.isEmpty()) {
            Msg.envoyer(p, "&6🔬 Analyse de " + cibleGP.getNom() + " : &7aucun effet détecté lors de l'observation.");
        } else {
            StringBuilder liste = new StringBuilder();
            for (int i = 0; i < effets.size(); i++) {
                if (i > 0) {
                    liste.append("&7, &f");
                }
                liste.append(AnalysteRole.nomFrancais(effets.get(i)));
            }
            Msg.envoyer(p, "&6🔬 Analyse de " + cibleGP.getNom() + " : &fil possédait &f" + liste + " &fau moment de l'observation.");
        }

        Player cibleJoueur = cibleGP.getPlayer();
        if (cibleJoueur != null) {
            Msg.envoyer(cibleJoueur, "&6Vous avez été analysé(e) par l'Analyste du Village.");
            if (cibleGP.getCamp() != Camp.VILLAGE) {
                Msg.envoyer(cibleJoueur, "&6Son identité vous est révélée : &f" + gp.getNom());
            }
        }
    }

    private void sorciereRessusciter(Player p, String[] args) {
        GamePlayer gp = moi(p);
        if (gp == null || !verifierRole(p, gp, RoleType.SORCIERE)) return;
        if (!gp.getEtat("sorciere_resurrection_dispo", false)) {
            Msg.envoyer(p, "&cVous avez déjà utilisé votre unique résurrection.");
            return;
        }
        if (args.length < 2) {
            Msg.envoyer(p, "&cUsage : /lg ressusciter <joueur>");
            return;
        }
        GamePlayer cibleGP = cible(p, args[1]);
        if (cibleGP == null) return;
        if (cibleGP == gp) {
            Msg.envoyer(p, "&cVous ne pouvez pas vous ressusciter vous-même.");
            return;
        }
        if (!cibleGP.isEnAttenteMort()) {
            Msg.envoyer(p, "&cCe joueur n'est pas (ou plus) en attente de résurrection.");
            return;
        }
        Player cibleJoueur = cibleGP.getPlayer();
        if (cibleJoueur == null) {
            Msg.envoyer(p, "&cCe joueur est hors ligne.");
            return;
        }
        gp.setEtat("sorciere_resurrection_dispo", false);
        cibleGP.setEnAttenteMort(false);
        cibleJoueur.setGameMode(GameMode.SURVIVAL);
        cibleJoueur.setHealth(cibleJoueur.getMaxHealth());
        cibleJoueur.setWalkSpeed(RenardRole.VITESSE_MARCHE_NORMALE);
        cibleJoueur.teleport(emplacementRespawnReanimation(cibleJoueur));
        plugin.getDeathManager().restaurerStuff(cibleGP);
        Msg.envoyer(p, "&d✧ Vous avez ressuscité " + cibleGP.getNom() + " !");
    }

    /**
     * Point de réapparition d'un joueur réanimé (Sorcière / Infect Père des Loups). Il patiente
     * au lobby depuis sa mort apparente (voir DeathManager#debuterFenetreMort), donc
     * cibleJoueur.getWorld() pointe à cet instant sur le monde LOBBY, pas le monde de jeu - on
     * passe explicitement par GameManager#getMondeJeu(). Toujours autour des coordonnées
     * globales (0,0) du monde de jeu, 300 blocs maximum, comme demandé (repli sur l'ancien
     * comportement - un point aléatoire dans toute la bordure - si le monde de jeu n'est
     * introuvable, ce qui ne devrait normalement jamais arriver en pleine partie).
     */
    private org.bukkit.Location emplacementRespawnReanimation(Player cibleJoueur) {
        org.bukkit.World mondeJeu = plugin.getGameManager().getMondeJeu();
        if (mondeJeu != null) {
            return plugin.getGameManager().emplacementAleatoireAutourDuZero(mondeJeu);
        }
        return plugin.getGameManager().emplacementAleatoireDansBordure(cibleJoueur.getWorld());
    }

    private void chasseurTire(Player p, String[] args) {
        GamePlayer gp = moi(p);
        if (gp == null) return;
        if (gp.getRole() != RoleType.CHASSEUR) {
            Msg.envoyer(p, "&cVous n'avez pas ce pouvoir.");
            return;
        }
        if (!gp.getEtat("chasseur_fenetre_ouverte", false)) {
            Msg.envoyer(p, "&cVotre fenêtre de tir est fermée.");
            return;
        }
        if (args.length < 2) {
            Msg.envoyer(p, "&cUsage : /lg tirer <joueur>");
            return;
        }
        GamePlayer cibleGP = cible(p, args[1]);
        if (cibleGP == null || !cibleGP.isVivant() || cibleGP.getPlayer() == null) {
            Msg.envoyer(p, "&cCible invalide.");
            return;
        }
        if (gp.getDernierTueur() != null && gp.getDernierTueur().equals(cibleGP.getUuid())) {
            Msg.envoyer(p, "&cVous ne pouvez pas tirer sur votre propre tueur.");
            return;
        }
        gp.setEtat("chasseur_fenetre_ouverte", false);
        Player cibleJoueur = cibleGP.getPlayer();
        double nouvelleVie = Math.max(0.0, cibleJoueur.getHealth() - ChasseurRole.DEGATS_TIR_COEURS * 2.0);
        cibleJoueur.setHealth(nouvelleVie);
        plugin.getGameManager().diffuser("&c🏹 Dans un dernier geste, " + gp.getNom() + " tire sur " + cibleGP.getNom() + " !");
        if (nouvelleVie <= 0.0) {
            plugin.getGameManager().eliminer(cibleGP, "a été abattu(e) par le Chasseur");
        }
    }

    // ================= Pouvoirs Loups =================

    private void loupPerfide(Player p) {
        GamePlayer gp = moi(p);
        if (gp == null || !verifierRole(p, gp, RoleType.LOUP_PERFIDE)) return;
        if (!plugin.getGameManager().estNuit()) {
            Msg.envoyer(p, "&cUniquement la nuit.");
            return;
        }
        int nuitActuelle = plugin.getGameManager().getNumeroNuit();
        if (gp.getEtat("perfide_derniere_nuit", -1) == nuitActuelle) {
            Msg.envoyer(p, "&cDéjà utilisé cette nuit.");
            return;
        }
        gp.setEtat("perfide_derniere_nuit", nuitActuelle);
        List<ItemStack> armure = new ArrayList<>();
        for (ItemStack piece : p.getInventory().getArmorContents()) {
            if (piece != null && piece.getType() != Material.AIR) {
                armure.add(piece);
            }
        }
        InventaireUtil.donner(p, armure.toArray(new ItemStack[0]));
        p.getInventory().setArmorContents(new ItemStack[4]);
        int dureeTicks = LoupPerfideRole.DUREE_INVISIBILITE_SECONDES * 20;
        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, dureeTicks, 0, false, false));
        gp.setEtat("perfide_invisible_actif", true);
        Msg.envoyer(p, "&8Vous retirez votre armure et disparaissez dans la nuit pendant 5 minutes (rompu si vous rééquipez une armure).");

        final org.bukkit.scheduler.BukkitTask[] tacheRef = new org.bukkit.scheduler.BukkitTask[1];
        final int[] ticksEcoules = {0};
        tacheRef[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            ticksEcoules[0] += 10;
            boolean armureReequipee = false;
            for (ItemStack piece : p.getInventory().getArmorContents()) {
                if (piece != null && piece.getType() != Material.AIR) {
                    armureReequipee = true;
                    break;
                }
            }
            boolean toujoursActif = gp.getEtat("perfide_invisible_actif", false);
            if (armureReequipee && toujoursActif) {
                gp.setEtat("perfide_invisible_actif", false);
                p.removePotionEffect(PotionEffectType.INVISIBILITY);
                Msg.envoyer(p, "&8Votre invisibilité se rompt : armure rééquipée.");
            }
            if (!toujoursActif || armureReequipee || ticksEcoules[0] >= dureeTicks) {
                gp.setEtat("perfide_invisible_actif", false);
                tacheRef[0].cancel();
            }
        }, 10L, 10L);
    }

    private void petiteFilleEspionner(Player p) {
        GamePlayer gp = moi(p);
        if (gp == null || !verifierRole(p, gp, RoleType.PETITE_FILLE)) return;
        if (!plugin.getGameManager().estNuit()) {
            Msg.envoyer(p, "&cUniquement la nuit.");
            return;
        }
        int nuitActuelle = plugin.getGameManager().getNumeroNuit();
        if (gp.getEtat("pf_derniere_nuit", -1) == nuitActuelle) {
            Msg.envoyer(p, "&cDéjà utilisé cette nuit.");
            return;
        }
        gp.setEtat("pf_derniere_nuit", nuitActuelle);
        List<ItemStack> armure = new ArrayList<>();
        for (ItemStack piece : p.getInventory().getArmorContents()) {
            if (piece != null && piece.getType() != Material.AIR) {
                armure.add(piece);
            }
        }
        InventaireUtil.donner(p, armure.toArray(new ItemStack[0]));
        p.getInventory().setArmorContents(new ItemStack[4]);
        int dureeTicks = PetiteFilleRole.DUREE_INVISIBILITE_SECONDES * 20;
        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, dureeTicks, 0, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, dureeTicks, 0, false, false));
        gp.setEtat("pf_invisible_actif", true);
        Msg.envoyer(p, "&8Vous retirez votre armure : invisible mais affaiblie pendant 5 minutes (rompu si vous rééquipez une armure).");

        final org.bukkit.scheduler.BukkitTask[] tacheRef = new org.bukkit.scheduler.BukkitTask[1];
        final int[] ticksEcoules = {0};
        tacheRef[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            ticksEcoules[0] += 10;
            boolean armureReequipee = false;
            for (ItemStack piece : p.getInventory().getArmorContents()) {
                if (piece != null && piece.getType() != Material.AIR) {
                    armureReequipee = true;
                    break;
                }
            }
            boolean toujoursActif = gp.getEtat("pf_invisible_actif", false);
            if (armureReequipee && toujoursActif) {
                gp.setEtat("pf_invisible_actif", false);
                p.removePotionEffect(PotionEffectType.INVISIBILITY);
                p.removePotionEffect(PotionEffectType.WEAKNESS);
                Msg.envoyer(p, "&8Votre invisibilité se rompt : armure rééquipée.");
            }
            if (!toujoursActif || armureReequipee || ticksEcoules[0] >= dureeTicks) {
                gp.setEtat("pf_invisible_actif", false);
                tacheRef[0].cancel();
            }
        }, 10L, 10L);
    }

    private void loupHurler(Player p) {
        GamePlayer gp = moi(p);
        if (gp == null) return;
        if (gp.getCamp() != Camp.LOUPS || !gp.isVivant()) {
            Msg.envoyer(p, "&cVous n'êtes pas dans le camp des Loups-Garous.");
            return;
        }
        if (gp.getEtat("hurlement_utilise", false)) {
            Msg.envoyer(p, "&cVous avez déjà hurlé cette partie (1 fois max).");
            return;
        }
        gp.setEtat("hurlement_utilise", true);
        plugin.getGameManager().diffuser("&8&l🌙 Un hurlement de Loup-Garou retentit au loin...");

        final org.bukkit.Location origine = p.getLocation().clone();

        // Le hurleur apprend combien d'autres Loups sont à moins de 50 blocs de lui.
        long loupsProches = plugin.getGameManager().getJoueursVivants().stream()
                .filter(g -> g != gp && g.getCamp() == Camp.LOUPS && g.getPlayer() != null
                        && g.getPlayer().getWorld().equals(origine.getWorld())
                        && g.getPlayer().getLocation().distance(origine) <= 50.0)
                .count();
        Msg.envoyer(p, "&4🐺 Vous sentez &f" + loupsProches + " &4autre(s) Loup(s)-Garou(s) dans un rayon de 50 blocs.");

        // Le reste de la meute reçoit une flèche (action bar) pointant vers le hurleur pendant 1 minute.
        for (GamePlayer loup : plugin.getGameManager().getJoueursVivants()) {
            if (loup == gp || loup.getCamp() != Camp.LOUPS || loup.getPlayer() == null) {
                continue;
            }
            demarrerFlecheDirection(loup.getPlayer(), origine, 60, "&4🐺 Hurlement : &f");
        }

        // La Petite Fille sent la direction du hurlement ; la durée augmente de 3s
        // à chaque hurlement suivant dans la partie (5s pour le tout premier).
        int numeroHurlement = plugin.getGameManager().incrementerHurlements();
        int dureeDetection = 5 + 3 * (numeroHurlement - 1);
        for (GamePlayer pf : plugin.getGameManager().getJoueursVivants()) {
            if (pf.getRole() != RoleType.PETITE_FILLE || pf.getPlayer() == null) {
                continue;
            }
            demarrerFlecheDirection(pf.getPlayer(), origine, dureeDetection, "&d👂 Hurlement : &f");
        }

        for (GamePlayer sauvage : plugin.getGameManager().getJoueursVivants()) {
            if (sauvage.getRole() != RoleType.ENFANT_SAUVAGE || !sauvage.getEtat("sauvage_transforme", false) || sauvage.getPlayer() == null) {
                continue;
            }
            double distance = sauvage.getPlayer().getLocation().distance(origine);
            Msg.envoyer(sauvage.getPlayer(), "&4🐺 Le hurlement venait de &f" + Math.round(distance) + " blocs&4.");
        }
    }

    /**
     * Affiche à `cible`, pendant `dureeSecondes`, une direction (action bar)
     * pointant vers `origine`, recalculée chaque seconde selon sa position
     * actuelle. S'arrête automatiquement si le joueur se déconnecte ou change
     * de monde.
     */
    private void demarrerFlecheDirection(Player cible, org.bukkit.Location origine, int dureeSecondes, String prefixe) {
        final int[] secondesRestantes = {dureeSecondes};
        final org.bukkit.scheduler.BukkitTask[] tacheRef = new org.bukkit.scheduler.BukkitTask[1];
        tacheRef[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!cible.isOnline() || secondesRestantes[0] <= 0 || !cible.getWorld().equals(origine.getWorld())) {
                if (tacheRef[0] != null) {
                    tacheRef[0].cancel();
                }
                return;
            }
            String direction = calculerDirection(cible.getLocation(), origine);
            Msg.envoyerActionBar(cible, prefixe + direction);
            secondesRestantes[0]--;
        }, 0L, 20L);
    }

    private String calculerDirection(org.bukkit.Location depuis, org.bukkit.Location vers) {
        double dx = vers.getX() - depuis.getX();
        double dz = vers.getZ() - depuis.getZ();
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        if (angle < 0) angle += 360;
        String[] directions = {"Nord", "Nord-Est", "Est", "Sud-Est", "Sud", "Sud-Ouest", "Ouest", "Nord-Ouest"};
        int index = (int) Math.round(angle / 45.0) % 8;
        return directions[index];
    }

    private void infecterPere(Player p) {
        GamePlayer gp = moi(p);
        if (gp == null || !verifierRole(p, gp, RoleType.INFECT_PERE_LOUPS)) return;
        if (!plugin.getDeathManager().peutInfecterMaintenant(gp)) {
            Msg.envoyer(p, "&cAucune infection possible pour le moment.");
            return;
        }
        GamePlayer victime = plugin.getGameManager().getGamePlayer(plugin.getDeathManager().getVictimeCorrompueEnAttente());
        plugin.getDeathManager().consommerFenetreInfection();
        if (victime == null) {
            return;
        }
        Player victimeJoueur = victime.getPlayer();
        if (victimeJoueur == null) {
            Msg.envoyer(p, "&cCe joueur est hors ligne, impossible de le ressusciter.");
            return;
        }
        // Le pouvoir de l'Infect Père est unique pour toute la partie, quel que
        // soit le cas d'usage (lui-même, un allié Loup, ou une victime corrompue).
        gp.setEtat("infect_pouvoir_utilise", true);

        boolean etaitDejaLoup = victime.getCamp() == Camp.LOUPS;
        victime.setVivant(true);
        victime.setEnAttenteMort(false);
        // setCampSansAffichage (et non setCamp) : le camp réel bascule bien en Loups pour toute
        // la logique de jeu (victoire, chat de meute, boussole...), mais le camp AFFICHÉ au
        // scoreboard reste inchangé - sans quoi le compteur Village/Loups bougeait instantanément
        // à l'infection et trahissait le changement de camp avant même l'annonce en jeu.
        victime.setCampSansAffichage(Camp.LOUPS);
        victime.setCorruption(0.0);
        victimeJoueur.setGameMode(GameMode.SURVIVAL);
        victimeJoueur.setHealth(victimeJoueur.getMaxHealth());
        victimeJoueur.teleport(emplacementRespawnReanimation(victimeJoueur));
        plugin.getDeathManager().restaurerStuff(victime);

        if (victime == gp) {
            Msg.envoyer(p, "&4&lVous vous relevez vous-même, plus déterminé que jamais !");
            plugin.getGameManager().diffuser("&4&l" + victime.getNom() + " se relève... la mort ne l'a pas retenu !");
        } else if (etaitDejaLoup) {
            Msg.envoyer(p, "&4✦ Vous avez ressuscité votre allié " + victime.getNom() + " !");
            plugin.getGameManager().diffuser("&4&l" + victime.getNom() + " se relève, sauvé(e) in extremis par son maître !");
        } else {
            victime.setEtat("infecte", true);
            Msg.envoyer(victimeJoueur, "&4&lVous vous relevez... et sentez une noirceur nouvelle vous envahir : vous êtes désormais un Loup-Garou !");
            // Ne PAS révéler le rôle d'origine de la victime au reste de la meute (ex : ne pas dire
            // "garde les pouvoirs de Voyante") : seul le fait qu'elle rejoint la meute est annoncé.
            plugin.getGameManager().annoncerNouvelAllieLoup(victime, "infecté(e) par l'Infect Père des Loups");
        }

        // Cette résurrection court-circuite finaliserMort() (la victime ne meurt jamais "pour de
        // vrai", donc verifierVictoire() n'y est jamais appelée) : si l'infection vient d'éliminer
        // le dernier Village/Solo restant, il faut recalculer la victoire ici, sans quoi la partie
        // continue indéfiniment alors qu'il ne reste plus que des Loups. Appel systématique (même
        // dans les branches "soi-même"/"allié déjà Loup") : sans effet si rien n'a changé.
        plugin.getGameManager().verifierVictoire();
    }

    // ================= Hybrides =================

    private void cupidonLove(Player p, String[] args) {
        GamePlayer gp = moi(p);
        if (gp == null || !verifierRole(p, gp, RoleType.CUPIDON)) return;
        if (gp.getEtat("cupidon_pouvoir_utilise", false)) {
            Msg.envoyer(p, "&cVous avez déjà formé un couple.");
            return;
        }
        if (args.length < 3) {
            Msg.envoyer(p, "&cUsage : /lg couple <joueur1> <joueur2>");
            return;
        }
        GamePlayer a = cible(p, args[1]);
        GamePlayer b = cible(p, args[2]);
        if (a == null || b == null) return;
        if (a == gp || b == gp) {
            Msg.envoyer(p, "&cVous ne pouvez pas vous inclure dans le couple.");
            return;
        }
        if (a == b || !a.isVivant() || !b.isVivant()) {
            Msg.envoyer(p, "&cChoisissez deux joueurs vivants différents.");
            return;
        }
        gp.setEtat("cupidon_pouvoir_utilise", true);
        plugin.getCoupleManager().formerCouple(plugin.getGameManager(), a, b);
        Msg.envoyer(p, "&d✓ Couple formé entre " + a.getNom() + " et " + b.getNom() + ".");
    }

    private void sauvageModele(Player p, String[] args) {
        GamePlayer gp = moi(p);
        if (gp == null || !verifierRole(p, gp, RoleType.ENFANT_SAUVAGE)) return;
        if (gp.getEtat("sauvage_modele_choisi", false)) {
            Msg.envoyer(p, "&cVous avez déjà choisi votre modèle.");
            return;
        }
        if (args.length < 2) {
            Msg.envoyer(p, "&cUsage : /lg choisir <joueur>");
            return;
        }
        GamePlayer modeleGP = cible(p, args[1]);
        if (modeleGP == null || modeleGP == gp || !modeleGP.isVivant()) {
            Msg.envoyer(p, "&cChoisissez un autre joueur vivant.");
            return;
        }
        gp.setEtat("sauvage_modele_choisi", true);
        gp.setModele(modeleGP.getUuid());
        Msg.envoyer(p, "&d✓ " + modeleGP.getNom() + " est désormais votre modèle. S'il/elle meurt, vous deviendrez Loup-Garou.");
    }

    private void ouvrirMenuCouleur(Player p, String[] args) {
        GamePlayer gp = moi(p);
        if (gp == null) return;
        GamePlayer cibleGP;
        if (args.length < 2 || args[1].equalsIgnoreCase("moi")) {
            // Pas d'argument (ou "moi") : on cible soi-même.
            cibleGP = gp;
        } else {
            cibleGP = cible(p, args[1]);
            if (cibleGP == null) return;
        }
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(null, 18,
                "§8" + com.lguhc.util.CouleursDisponibles.PREFIXE_TITRE_MENU + cibleGP.getNom());
        for (int i = 0; i < com.lguhc.util.CouleursDisponibles.NOMS.length; i++) {
            ItemStack laine = new ItemStack(Material.WOOL, 1, (short) i);
            org.bukkit.inventory.meta.ItemMeta meta = laine.getItemMeta();
            meta.setDisplayName(com.lguhc.util.CouleursDisponibles.COULEURS[i] + com.lguhc.util.CouleursDisponibles.NOMS[i]);
            laine.setItemMeta(meta);
            inv.setItem(i, laine);
        }
        ItemStack reset = new ItemStack(Material.BARRIER);
        org.bukkit.inventory.meta.ItemMeta metaReset = reset.getItemMeta();
        metaReset.setDisplayName("§cRéinitialiser (couleur normale)");
        reset.setItemMeta(metaReset);
        inv.setItem(17, reset);
        p.openInventory(inv);
    }

    private void soeurMessage(Player p, String[] args) {
        GamePlayer gp = moi(p);
        if (gp == null || !verifierRole(p, gp, RoleType.SOEURS)) return;
        java.util.UUID partenaireId = gp.getEtat("soeur_partenaire", null);
        if (partenaireId == null) {
            Msg.envoyer(p, "&cVotre Sœur n'a pas encore été déterminée.");
            return;
        }
        GamePlayer partenaire = plugin.getGameManager().getGamePlayer(partenaireId);
        if (partenaire == null || !partenaire.isVivant() || partenaire.getPlayer() == null) {
            Msg.envoyer(p, "&cVotre Sœur n'est plus en mesure de vous répondre.");
            return;
        }
        int utilises = gp.getEtat("soeur_messages_utilises", 0);
        if (utilises >= SoeursRole.MESSAGES_MAX_PAR_EPISODE) {
            Msg.envoyer(p, "&cVous avez déjà utilisé vos 2 messages pour cet épisode.");
            return;
        }
        if (args.length < 2) {
            Msg.envoyer(p, "&cUsage : /lg soeur <message>");
            return;
        }
        String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        gp.setEtat("soeur_messages_utilises", utilises + 1);
        Msg.envoyer(p, "&d[Vous → " + partenaire.getNom() + "] &f" + message);
        Msg.envoyer(partenaire.getPlayer(), "&d[" + gp.getNom() + " → Vous] &f" + message);
    }

    // ================= Groupe =================

    private void groupeMessage(Player p, String[] args) {
        GamePlayer gp = moi(p);
        if (gp == null) return;
        if (gp.getGroupe() <= 0) {
            Msg.envoyer(p, "&cVous n'appartenez à aucun groupe pour l'instant.");
            return;
        }
        if (args.length < 2) {
            Msg.envoyer(p, "&cUsage : /lg groupe <message>");
            return;
        }
        String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        for (GamePlayer membre : plugin.getGameManager().getMembresDuGroupe(gp.getGroupe())) {
            Msg.envoyer(membre.getPlayer(), "&6[Groupe " + gp.getGroupe() + "] " + gp.getNom() + "&7: &f" + message);
        }
    }

    // ================= Admin / test =================

    private boolean traiterAdmin(CommandSender sender, String[] args) {
        if (!verifierHote(sender)) return true;
        if (args.length < 2) {
            envoyerAideAdmin(sender);
            return true;
        }
        String action = args[1].toLowerCase();
        GameManager gm = plugin.getGameManager();

        switch (action) {
            case "skip":
                gm.avancerPhaseImmediatement(sender);
                return true;

            case "start":
            case "forcestart":
                gm.demarrer(sender, true);
                return true;

            case "listelgskip":
                gm.forcerRevelationListeLoups(sender);
                return true;

            case "regen":
                plugin.getWorldResetManager().regenerer(sender);
                return true;

            case "roles":
                sender.sendMessage(Msg.c("&5Rôles disponibles : &f" + java.util.Arrays.stream(RoleType.values())
                        .map(RoleType::name).collect(java.util.stream.Collectors.joining(", "))));
                return true;

            case "role": {
                if (args.length < 4) {
                    sender.sendMessage(Msg.c("&cUsage : /lg admin role <joueur> <ROLE> (voir /lg admin roles)"));
                    return true;
                }
                Player cibleJoueur = Bukkit.getPlayerExact(args[2]);
                if (cibleJoueur == null) {
                    sender.sendMessage(Msg.c("&cJoueur introuvable ou pas inscrit : " + args[2]));
                    return true;
                }
                GamePlayer cibleGP = gm.getGamePlayer(cibleJoueur);
                if (cibleGP == null) {
                    sender.sendMessage(Msg.c("&cCe joueur ne participe pas à la partie."));
                    return true;
                }
                RoleType type;
                try {
                    type = RoleType.valueOf(args[3].toUpperCase());
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(Msg.c("&cRôle inconnu : " + args[3] + " (voir /lg admin roles)"));
                    return true;
                }
                gm.forcerRole(sender, cibleGP, type);
                return true;
            }

            case "kill": {
                GamePlayer cibleGP = cibleAdmin(sender, args, 2);
                if (cibleGP == null) return true;
                gm.forcerMort(sender, cibleGP);
                return true;
            }

            case "revive": {
                GamePlayer cibleGP = cibleAdmin(sender, args, 2);
                if (cibleGP == null) return true;
                gm.forcerVie(sender, cibleGP);
                return true;
            }

            case "tp": {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(Msg.c("&cCommande réservée aux joueurs."));
                    return true;
                }
                GamePlayer cibleGP = cibleAdmin(sender, args, 2);
                if (cibleGP == null || cibleGP.getPlayer() == null) return true;
                ((Player) sender).teleport(cibleGP.getPlayer().getLocation());
                sender.sendMessage(Msg.c("&aTéléporté vers " + cibleGP.getNom() + "."));
                return true;
            }

            case "chat": {
                boolean nouvelEtat = !gm.isChatDesactive();
                gm.setChatDesactive(nouvelEtat);
                Bukkit.broadcastMessage(Msg.c(nouvelEtat
                        ? "&c&lLe chat vient d'être désactivé par un hôte."
                        : "&a&lLe chat est de nouveau activé."));
                return true;
            }

            case "border": {
                if (args.length < 3) {
                    sender.sendMessage(Msg.c("&cUsage : /lg admin border <taille> &7ou&c /lg admin border start"));
                    return true;
                }
                if (args[2].equalsIgnoreCase("start")) {
                    if (!gm.estEnCours()) {
                        sender.sendMessage(Msg.c("&cIl faut une partie en cours pour forcer le resserrement."));
                        return true;
                    }
                    org.bukkit.World monde = Bukkit.getWorld(plugin.getConfig().getString("monde.nom", "world"));
                    if (monde == null) {
                        sender.sendMessage(Msg.c("&cMonde introuvable."));
                        return true;
                    }
                    boolean vientDeDemarrer = plugin.getBorderManager().demarrerResserrement(monde);
                    if (vientDeDemarrer) {
                        sender.sendMessage(Msg.c("&aResserrement de la bordure forcé : il commence maintenant."));
                    } else {
                        sender.sendMessage(Msg.c("&eLe resserrement était déjà en cours, rien à faire."));
                    }
                    return true;
                }
                try {
                    double taille = Double.parseDouble(args[2]);
                    org.bukkit.World monde = Bukkit.getWorld(plugin.getConfig().getString("monde.nom", "world"));
                    if (monde == null) {
                        sender.sendMessage(Msg.c("&cMonde introuvable."));
                        return true;
                    }
                    monde.getWorldBorder().setSize(taille);
                    sender.sendMessage(Msg.c("&aBordure réglée instantanément à " + taille + "."));
                } catch (NumberFormatException e) {
                    sender.sendMessage(Msg.c("&cValeur invalide (nombre attendu, ou \"start\" pour forcer le resserrement)."));
                }
                return true;
            }

            default:
                envoyerAideAdmin(sender);
                return true;
        }
    }

    private GamePlayer cibleAdmin(CommandSender sender, String[] args, int index) {
        if (args.length <= index) {
            sender.sendMessage(Msg.c("&cIl manque un nom de joueur."));
            return null;
        }
        Player cibleJoueur = Bukkit.getPlayerExact(args[index]);
        if (cibleJoueur == null) {
            sender.sendMessage(Msg.c("&cJoueur introuvable : " + args[index]));
            return null;
        }
        GamePlayer gp = plugin.getGameManager().getGamePlayer(cibleJoueur);
        if (gp == null) {
            sender.sendMessage(Msg.c("&cCe joueur ne participe pas à la partie."));
            return null;
        }
        return gp;
    }

    private void envoyerAideAdmin(CommandSender sender) {
        sender.sendMessage(Msg.c("&5&l=== LGUHC Admin (test) ==="));
        sender.sendMessage(Msg.c("&6/lg admin skip &7- termine la phase actuelle immédiatement"));
        sender.sendMessage(Msg.c("&6/lg admin listelgskip &7- force la révélation immédiate de la liste des alliés Loups (sans attendre les 45 min)"));
        sender.sendMessage(Msg.c("&6/lg admin regen &7- régénère immédiatement le monde de jeu depuis le dossier modèle (hors partie)"));
        sender.sendMessage(Msg.c("&6/lg admin start &7- lance la partie en ignorant le minimum de 4 joueurs"));
        sender.sendMessage(Msg.c("&6/lg admin role <joueur> <ROLE> &7- force un rôle précis"));
        sender.sendMessage(Msg.c("&6/lg admin roles &7- liste les identifiants de rôles valides"));
        sender.sendMessage(Msg.c("&6/lg admin kill <joueur> &7- élimine instantanément (teste Chasseur/Couple/Infect...)"));
        sender.sendMessage(Msg.c("&6/lg admin revive <joueur> &7- ressuscite pour continuer à tester"));
        sender.sendMessage(Msg.c("&6/lg admin tp <joueur> &7- vous téléporte vers ce joueur"));
        sender.sendMessage(Msg.c("&6/lg admin border <taille> &7- redimensionne la bordure instantanément"));
        sender.sendMessage(Msg.c("&6/lg admin border start &7- force le début du resserrement progressif (sans attendre le délai normal)"));
        sender.sendMessage(Msg.c("&6/lg admin chat &7- active/désactive le chat général du serveur (basculeur)"));
    }

    // ================= Vote du village =================

    private void villageVote(Player p, String[] args) {
        GamePlayer gp = moi(p);
        if (gp == null) return;
        if (args.length < 2) {
            Msg.envoyer(p, "&cUsage : /lg vote <joueur|blanc>");
            return;
        }
        if (args[1].equalsIgnoreCase("blanc")) {
            plugin.getVoteManager().enregistrerVote(plugin, gp, null);
            return;
        }
        GamePlayer cibleGP = cible(p, args[1]);
        if (cibleGP == null) return;
        plugin.getVoteManager().enregistrerVote(plugin, gp, cibleGP);
    }

    // ================= Divers =================

    private void afficherRole(Player p) {
        GamePlayer gp = moi(p);
        if (gp == null) return;
        if (gp.getRole() == null) {
            Msg.envoyer(p, "&7Aucun rôle ne vous a encore été attribué.");
            return;
        }
        RoleType type = gp.getRole();
        // Réutilise TEL QUEL RoleType#getCarteAnnonce (rôle / objectif / Aura / description),
        // la même méthode qui sert déjà à l'attribution du rôle et aux rappels de début
        // d'épisode (voir GameManager#envoyerCarteRole). Une seule source de vérité pour ce
        // texte : /lg role ne peut plus afficher une description ou une couleur d'Aura
        // différente de l'annonce d'origine. Math.max(..., 2) couvre le même cas que /lg admin
        // role : un rôle déjà attribué mais épisode encore à 0/1 (test en solo).
        int episodeAffiche = Math.max(plugin.getGameManager().getEpisodeActuel(), 2);
        for (String ligne : type.getCarteAnnonce(episodeAffiche)) {
            Msg.envoyer(p, ligne);
        }

        // Statuts propres à /lg role, absents de la carte de base :
        // Infecté par l'Infect Père des Loups : le rôle garde son nom et ses pouvoirs, mais son
        // vrai camp devient Loups-Garous ; on le signale ici en plus, sans toucher à la carte.
        boolean infecte = gp.getEtat("infecte", false);
        if (infecte) {
            Msg.envoyer(p, Camp.LOUPS.getCouleur() + "• Infecté : vous êtes désormais Loup-Garou.");
        }
        // Enfant Sauvage transformé : le scoreboard continue d'afficher "Village" (voir
        // GameManager#finaliserMort), mais /lg role doit le dire clairement au joueur concerné.
        boolean sauvageTransforme = gp.getEtat("sauvage_transforme", false);
        if (sauvageTransforme) {
            Msg.envoyer(p, Camp.LOUPS.getCouleur() + "• Transformé : votre modèle est mort, vous êtes désormais Loup-Garou.");
        }
        // Aura dynamique (ex : Analyste après un /lg observer ou /lg analyser) : gp.getAura()
        // peut différer de la valeur de départ portée par le rôle (type.getAura(), déjà affichée
        // ci-dessus dans la carte) - voir RoleType (javadoc en tête de fichier) et AnalysteRole.
        if (gp.getAura() != type.getAura()) {
            Msg.envoyer(p, "&7• Aura actuelle : " + gp.getAura().getNomFormate() + "&7 (a changé en cours de partie).");
        }
        // Couple (les 2 amoureux, et le Cupidon tant que son couple est vivant)
        String campCouple = campAvecCouplePour(gp);
        if ("&dCouple".equals(campCouple)) {
            Msg.envoyer(p, "&7• Statut : &dCouple");
        }

        // Le Loup-Garou Amnésique caché a sa propre mécanique de liste (construite par proximité,
        // voir GameManager#envoyerListeAlliesLoup / #tickLoupGarouAmnesique), indépendante du
        // minuteur général de 45 min : il doit pouvoir la consulter via /lg role dès le début,
        // même avant que isListeLoupsRevelee() ne devienne vrai pour le reste de la meute.
        boolean amnesiqueCache = gp.getRole() == RoleType.LOUP_GAROU_AMNESIQUE && !gp.getEtat("amnesique_revele", false);
        if (gp.isVivant() && gp.getCamp() == Camp.LOUPS && (plugin.getGameManager().isListeLoupsRevelee() || amnesiqueCache)) {
            plugin.getGameManager().envoyerListeAlliesLoup(gp);
        }
    }

    /**
     * Nom de camp affiché entre parenthèses dans /lg role : "Couple" en rose pour les 2
     * amoureux, et pour le Cupidon qui les a formés tant que son couple est encore vivant
     * (sinon son camp redevient Village, en vert, comme n'importe quel autre Villageois).
     */
    private String campAvecCouplePour(GamePlayer gp) {
        if (gp.estEnCouple()) {
            return "&dCouple";
        }
        if (gp.getRole() == RoleType.CUPIDON && plugin.getGameManager().coupleEstVivant()) {
            return "&dCouple";
        }
        return gp.getCamp().getNomAffiche();
    }

    // ================= /lg auras =================

    /**
     * Liste dynamique : dérivée directement de RoleType.getAura() / getNomFormate(), donc
     * toujours à jour même si un rôle change de camp/Aura plus tard (pas de liste figée à
     * maintenir ici en double).
     */
    private void afficherAuras(Player p) {
        Msg.envoyer(p, "&5&l=== Rôles & Auras ===");
        afficherGroupeAura(p, Aura.LUMINEUSE);
        afficherGroupeAura(p, Aura.NEUTRE);
        afficherGroupeAura(p, Aura.OBSCURE);
    }

    private void afficherGroupeAura(Player p, Aura aura) {
        Msg.envoyer(p, aura.getNomFormate() + " &7:");
        StringBuilder ligne = new StringBuilder();
        boolean premier = true;
        for (RoleType type : RoleType.values()) {
            if (type.getAura() != aura) continue;
            if (!premier) {
                ligne.append("&7, ");
            }
            ligne.append(type.getNomFormate());
            premier = false;
        }
        Msg.envoyer(p, ligne.toString());
    }

    // ================= /lg compo =================

    /** Un rôle par joueur vivant (les rôles en plusieurs exemplaires apparaissent donc plusieurs fois). */
    private void afficherCompo(Player p) {
        List<GamePlayer> vivants = new ArrayList<>(plugin.getGameManager().getJoueursVivants());
        vivants.removeIf(gp -> gp.getRole() == null);
        if (vivants.isEmpty()) {
            Msg.envoyer(p, "&7Aucun rôle vivant pour le moment.");
            return;
        }
        vivants.sort((a, b) -> a.getRole().getNomAffiche().compareTo(b.getRole().getNomAffiche()));
        Msg.envoyer(p, "&5&lListe des rôles vivants :");
        for (GamePlayer gp : vivants) {
            Msg.envoyer(p, "&7• " + gp.getRole().getNomFormate());
        }
    }

    private void envoyerAide(CommandSender sender) {
        sender.sendMessage(Msg.c("&5&l=== LGUHC ==="));
        sender.sendMessage(Msg.c("&d/lg join &7- rejoindre la partie"));
        sender.sendMessage(Msg.c("&d/lg leave &7- quitter la partie"));
        sender.sendMessage(Msg.c("&d/lg role &7- revoir votre rôle"));
        sender.sendMessage(Msg.c("&d/lg auras &7- voir la liste des rôles et leurs Auras"));
        sender.sendMessage(Msg.c("&d/lg compo &7- voir la liste des rôles encore vivants"));
        sender.sendMessage(Msg.c("&d/lg vote <joueur|blanc> &7- voter pendant une réunion du village"));
        sender.sendMessage(Msg.c("&d/lg color [joueur] &7- assigner une couleur personnelle (pour vous seul). Sans argument ou avec \"moi\" : vous colore vous-même"));
        sender.sendMessage(Msg.c("&d/lg groupe <message> &7- parler à votre groupe"));
        sender.sendMessage(Msg.c("&d/lw <message> &7- laisser un dernier mot"));
        sender.sendMessage(Msg.c("&d/helpop <message> &7- demander de l'aide au staff en privé"));
        if (sender.hasPermission("lguhc.host")) {
            sender.sendMessage(Msg.c("&6/lg config &7- ouvrir le menu de configuration (composition, règles, bordure...) (hôte)"));
            sender.sendMessage(Msg.c("&6/lg start &7- démarrer la partie (hôte)"));
            sender.sendMessage(Msg.c("&6/lg stop &7- réinitialiser la partie (hôte)"));
            sender.sendMessage(Msg.c("&6/lg admin &7- outils de test (voir /lg admin)"));
            sender.sendMessage(Msg.c("&6/host <message> &7- annonce serveur bien visible (hôte)"));
        }
        sender.sendMessage(Msg.c("&7Les pouvoirs de rôle apparaissent en jeu une fois votre rôle attribué."));
    }
}
