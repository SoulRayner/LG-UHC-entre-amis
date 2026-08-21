package com.lguhc.game;

import com.lguhc.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Gère le Couple formé par Cupidon : si l'un des deux amoureux meurt,
 * l'autre meurt de chagrin - sauf si tous les deux sont les 2 derniers
 * survivants, auquel cas ils gagnent ensemble plutôt que de s'entretuer.
 */
public class CoupleManager {

    /** Nom d'affichage de la boussole de l'amour, utilisé aussi pour la retrouver et la retirer plus tard. */
    private static final String NOM_BOUSSOLE_AMOUR = "§d❤ Boussole de l'Amour";
    /** Distance de rencontre (en blocs) en dessous de laquelle les boussoles disparaissent. */
    private static final double DISTANCE_RENCONTRE = 10.0;

    public void formerCouple(GameManager gm, GamePlayer a, GamePlayer b) {
        a.setAmoureux(b.getUuid());
        b.setAmoureux(a.getUuid());
        Msg.envoyer(a.getPlayer(), "&d&l♥ Vous êtes maintenant amoureux de &f" + b.getNom() + "&d&l ! Si l'un de vous meurt, l'autre en mourra de chagrin.");
        Msg.envoyer(b.getPlayer(), "&d&l♥ Vous êtes maintenant amoureux de &f" + a.getNom() + "&d&l ! Si l'un de vous meurt, l'autre en mourra de chagrin.");

        a.setEtat("boussole_amour_active", true);
        b.setEtat("boussole_amour_active", true);
        donnerBoussoleAmour(a);
        donnerBoussoleAmour(b);
    }

    private void donnerBoussoleAmour(GamePlayer gp) {
        Player joueur = gp.getPlayer();
        if (joueur == null) {
            return;
        }
        ItemStack boussole = new ItemStack(Material.COMPASS, 1);
        ItemMeta meta = boussole.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(NOM_BOUSSOLE_AMOUR);
            boussole.setItemMeta(meta);
        }
        joueur.getInventory().addItem(boussole);
        Msg.envoyer(joueur, "&dUne boussole magique apparaît dans votre inventaire : elle pointe vers votre amoureux(se), et disparaîtra dès que vous serez réunis.");
    }

    private void retirerBoussoleAmour(GamePlayer gp) {
        gp.setEtat("boussole_amour_active", false);
        Player joueur = gp.getPlayer();
        if (joueur == null) {
            return;
        }
        ItemStack[] contenu = joueur.getInventory().getContents();
        for (int i = 0; i < contenu.length; i++) {
            ItemStack item = contenu[i];
            if (item != null && item.getType() == Material.COMPASS && item.hasItemMeta()
                    && NOM_BOUSSOLE_AMOUR.equals(item.getItemMeta().getDisplayName())) {
                joueur.getInventory().clear(i);
                break;
            }
        }
    }

    /**
     * A appeler chaque seconde (voir GameManager#tickBoussoleCouple, exécuté juste après le
     * tick de la boussole traqueuse générale afin de garder la priorité sur la cible affichée).
     * Met à jour la cible des boussoles de l'amour vers l'autre membre du couple, et les retire
     * des inventaires dès que les 2 amoureux se retrouvent à moins de 10 blocs l'un de l'autre.
     */
    public void tickBoussole(GameManager gm) {
        GamePlayer[] couple = gm.getCouple();
        if (couple == null) {
            return;
        }
        GamePlayer a = couple[0];
        GamePlayer b = couple[1];
        if (!a.getEtat("boussole_amour_active", false) || !b.getEtat("boussole_amour_active", false)) {
            return;
        }
        if (!a.isVivant() || !b.isVivant()) {
            return;
        }
        Player pa = a.getPlayer();
        Player pb = b.getPlayer();
        if (pa == null || pb == null) {
            return;
        }
        if (pa.getWorld().equals(pb.getWorld()) && pa.getLocation().distance(pb.getLocation()) <= DISTANCE_RENCONTRE) {
            retirerBoussoleAmour(a);
            retirerBoussoleAmour(b);
            Msg.envoyer(pa, "&d❤ Vous avez retrouvé " + b.getNom() + " ! Votre boussole de l'amour disparaît.");
            Msg.envoyer(pb, "&d❤ Vous avez retrouvé " + a.getNom() + " ! Votre boussole de l'amour disparaît.");
            return;
        }
        pa.setCompassTarget(pb.getLocation());
        pb.setCompassTarget(pa.getLocation());
    }

    /**
     * Traite la commande /don[Nombre] : transfère `nombre` % de vie (échelle 10 = 1 cœur,
     * donc /don40 = 4 cœurs) du donneur vers son amoureux. Échoue sans rien coûter au donneur
     * si l'amoureux est déjà à pleine vie.
     */
    public void donnerVie(GameManager gm, GamePlayer donneur, int nombre) {
        Player joueurDonneur = donneur.getPlayer();
        if (joueurDonneur == null) {
            return;
        }
        if (!donneur.isVivant()) {
            Msg.envoyer(joueurDonneur, "&cVous êtes mort, vous ne pouvez pas transférer de vie.");
            return;
        }
        if (!donneur.estEnCouple()) {
            Msg.envoyer(joueurDonneur, "&cCette commande est réservée aux membres du Couple.");
            return;
        }
        if (nombre <= 0) {
            Msg.envoyer(joueurDonneur, "&cLe nombre doit être positif (ex : /don40 pour donner 4 cœurs).");
            return;
        }
        GamePlayer receveur = gm.getGamePlayer(donneur.getAmoureux());
        if (receveur == null || !receveur.isVivant() || receveur.getPlayer() == null) {
            Msg.envoyer(joueurDonneur, "&cVotre amoureux(se) n'est pas disponible actuellement.");
            return;
        }
        Player joueurReceveur = receveur.getPlayer();

        if (joueurReceveur.getHealth() >= joueurReceveur.getMaxHealth()) {
            Msg.envoyer(joueurDonneur, "&cVotre transfert de vie à " + receveur.getNom() + " a échoué &7(déjà à pleine vie).");
            return;
        }

        // Echelle : nombre=40 -> 4 cœurs -> 8.0 PV (1 cœur = 10 dans la commande = 2.0 PV).
        double pvATransferer = nombre * 0.2;
        if (joueurDonneur.getHealth() - pvATransferer < 1.0) {
            Msg.envoyer(joueurDonneur, "&cVous n'avez pas assez de vie pour donner autant.");
            return;
        }

        double nouvelleVieReceveur = Math.min(joueurReceveur.getMaxHealth(), joueurReceveur.getHealth() + pvATransferer);
        joueurDonneur.setHealth(joueurDonneur.getHealth() - pvATransferer);
        joueurReceveur.setHealth(nouvelleVieReceveur);

        Msg.envoyer(joueurReceveur, "&d" + donneur.getNom() + " vient de vous transférer " + nombre + " % de vie !");
        Msg.envoyer(joueurDonneur, "&dVotre transfert de vie de " + nombre + " % a bien été envoyé à " + receveur.getNom() + " !");
    }

    /**
     * A appeler juste après qu'un joueur soit mort.
     * @return true si une victoire du Couple a été déclenchée (le moteur de
     *         jeu ne doit alors pas continuer les vérifications de victoire
     *         habituelles pour cette mort).
     */
    public boolean traiterMortPourCouple(GameManager gm, GamePlayer mort) {
        if (!mort.estEnCouple()) {
            return false;
        }
        GamePlayer amoureux = gm.getGamePlayer(mort.getAmoureux());
        if (amoureux == null || !amoureux.isVivant()) {
            return false;
        }

        List<GamePlayer> vivants = gm.getJoueursVivants();
        boolean sontSeulsSurvivants = vivants.size() == 1 && vivants.get(0).getUuid().equals(amoureux.getUuid());

        if (sontSeulsSurvivants) {
            gm.diffuser("&d&l♥ " + mort.getNom() + " et " + amoureux.getNom() + " étaient en couple : ils remportent la partie ensemble, envers et contre tout !");
            gm.terminerPartie(null, "Le Couple (" + mort.getNom() + " & " + amoureux.getNom() + ")");
            return true;
        }

        gm.diffuser("&d💔 Le cœur brisé, &f" + amoureux.getNom() + " &dmeurt de chagrin en apprenant la mort de son amour...");
        amoureux.setEtat("mort_de_chagrin_pour", mort.getNom());
        retirerPunchDeCupidon(gm);
        gm.eliminer(amoureux, "meurt de chagrin");
        return false;
    }

    /** Le couple a échoué : Cupidon doit désormais gagner avec le Village et perd l'enchant Punch I de son arc. */
    private void retirerPunchDeCupidon(GameManager gm) {
        GamePlayer cupidon = gm.getJoueursVivants().stream().filter(g -> g.getRole() == RoleType.CUPIDON).findFirst().orElse(null);
        if (cupidon == null || cupidon.getPlayer() == null) {
            return;
        }
        org.bukkit.entity.Player joueur = cupidon.getPlayer();
        for (org.bukkit.inventory.ItemStack item : joueur.getInventory().getContents()) {
            if (item != null && item.getType() == org.bukkit.Material.BOW && item.containsEnchantment(org.bukkit.enchantments.Enchantment.ARROW_KNOCKBACK)) {
                item.removeEnchantment(org.bukkit.enchantments.Enchantment.ARROW_KNOCKBACK);
            }
        }
        Msg.envoyer(joueur, "&d💔 Votre Couple vient de mourir, vous perdez votre enchantement punch 1 sur votre arc et devez désormais gagner avec le Village !");
    }
}
