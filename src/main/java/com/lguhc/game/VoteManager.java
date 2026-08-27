package com.lguhc.game;

import com.lguhc.LGUHCPlugin;
import com.lguhc.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Vote du village : 1 vote au début de chaque épisode à partir du 4ème
 * (soit 60 min de jeu avec la durée d'épisode par défaut de 20 min),
 * dure 2 minutes. Il faut au moins 3 votes sur un même joueur pour qu'il
 * soit sanctionné (le plus voté l'emporte ; égalité -> tirage au sort
 * parmi les joueurs à égalité). Sanction : Poison I pendant 13 secondes,
 * et son pseudo est annoncé avec 4 rôles tirés au sort parmi les vivants
 * (son vrai rôle est toujours l'un des 4).
 */
public class VoteManager {

    private boolean actif = true;
    private int episodePremierVote = 4;
    private long dureeSecondes = 120;
    private static final int VOTES_MINIMUM = 3;
    private static final int DUREE_POISON_SECONDES = 13;
    private static final int NB_ROLES_REVELES = 4;

    private boolean voteEnCours = false;
    private final Map<UUID, UUID> votes = new HashMap<>();
    private final java.util.Random random = new java.util.Random();

    public void charger(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        actif = section.getBoolean("active", true);
        episodePremierVote = section.getInt("episode-premier-vote", 4);
        dureeSecondes = section.getLong("duree-secondes", 120);
    }

    public boolean isActif() {
        return actif;
    }

    public int getEpisodePremierVote() {
        return episodePremierVote;
    }

    public boolean isVoteEnCours() {
        return voteEnCours;
    }

    public void demarrerVote(LGUHCPlugin plugin) {
        if (!actif || voteEnCours) {
            return;
        }
        voteEnCours = true;
        votes.clear();
        GameManager gm = plugin.getGameManager();
        gm.diffuser("&e&l⚠ Vote du village ! &eVous avez " + dureeSecondes + " secondes pour désigner un suspect avec &6/lg vote <joueur> &e(ou &6/lg vote blanc&e). Résultat révélé dans 2 minutes.");

        Bukkit.getScheduler().runTaskLater(plugin, () -> terminerVote(plugin), dureeSecondes * 20L);
    }

    public void enregistrerVote(LGUHCPlugin plugin, GamePlayer votant, GamePlayer cible) {
        if (!voteEnCours) {
            Msg.envoyer(votant.getPlayer(), "&cAucun vote n'est en cours actuellement.");
            return;
        }
        if (!votant.isVivant()) {
            Msg.envoyer(votant.getPlayer(), "&cLes morts ne votent pas.");
            return;
        }
        if (!votant.aDroitDeVote()) {
            Msg.envoyer(votant.getPlayer(), "&cVous avez perdu votre droit de vote.");
            return;
        }
        if (votes.containsKey(votant.getUuid())) {
            Msg.envoyer(votant.getPlayer(), "&cVous avez déjà voté pour cet épisode.");
            return;
        }
        if (cible != null && !cible.isVivant()) {
            Msg.envoyer(votant.getPlayer(), "&cCe joueur n'est plus en vie.");
            return;
        }
        votes.put(votant.getUuid(), cible == null ? null : cible.getUuid());
        Msg.envoyer(votant.getPlayer(), cible == null ? "&7Vous avez voté blanc." : "&7Vous avez voté contre &f" + cible.getNom() + "&7.");
    }

    private void terminerVote(LGUHCPlugin plugin) {
        if (!voteEnCours) {
            return;
        }
        voteEnCours = false;
        GameManager gm = plugin.getGameManager();

        Map<UUID, Integer> decompte = new HashMap<>();
        for (UUID cible : votes.values()) {
            if (cible == null) {
                continue;
            }
            decompte.merge(cible, 1, Integer::sum);
        }

        int max = decompte.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        if (max < VOTES_MINIMUM) {
            gm.diffuser("&7Le vote est clos : personne n'a atteint les " + VOTES_MINIMUM + " votes nécessaires.");
            return;
        }

        List<UUID> aEgalite = new ArrayList<>();
        for (Map.Entry<UUID, Integer> e : decompte.entrySet()) {
            if (e.getValue() == max) {
                aEgalite.add(e.getKey());
            }
        }
        UUID choisi = aEgalite.size() == 1 ? aEgalite.get(0) : aEgalite.get(random.nextInt(aEgalite.size()));

        GamePlayer designe = gm.getGamePlayer(choisi);
        if (designe == null || !designe.isVivant()) {
            return;
        }

        sanctionner(gm, designe);
    }

    private void sanctionner(GameManager gm, GamePlayer designe) {
        Player p = designe.getPlayer();
        if (p != null) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, DUREE_POISON_SECONDES * 20, 0));
        }

        List<RoleType> rolesVivants = new ArrayList<>();
        for (GamePlayer gp : gm.getJoueursVivants()) {
            if (gp.getRole() != null) {
                rolesVivants.add(gp.getRole());
            }
        }
        rolesVivants.remove(designe.getRole());
        Collections.shuffle(rolesVivants);

        List<String> rolesAffiches = new ArrayList<>();
        rolesAffiches.add(designe.getRole() != null ? designe.getRole().getNomAffiche() : "???");
        for (int i = 0; i < rolesVivants.size() && rolesAffiches.size() < NB_ROLES_REVELES; i++) {
            String nom = rolesVivants.get(i).getNomAffiche();
            if (!rolesAffiches.contains(nom)) {
                rolesAffiches.add(nom);
            }
        }
        Collections.shuffle(rolesAffiches);

        gm.diffuser("&e&l⚠ " + designe.getNom() + " &eest désigné par le vote ! Son rôle est l'un de ceux-ci : &f" + String.join("&7, &f", rolesAffiches));
    }
}
