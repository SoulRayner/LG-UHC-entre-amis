package com.lguhc.listeners;

import com.lguhc.LGUHCPlugin;
import com.lguhc.game.Aura;
import com.lguhc.game.GamePlayer;
import com.lguhc.game.RoleType;
import com.lguhc.roles.villageois.DruideRole;
import com.lguhc.util.Msg;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;

public class AbilityListener implements Listener {

    private final LGUHCPlugin plugin;

    public AbilityListener(LGUHCPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void surConsommationDruide(PlayerItemConsumeEvent event) {
        Player joueur = event.getPlayer();
        GamePlayer gp = plugin.getGameManager().getGamePlayer(joueur);
        if (gp == null || gp.getRole() != RoleType.DRUIDE || !gp.isVivant()) {
            return;
        }
        boolean estPommeEnOr = event.getItem().getType() == org.bukkit.Material.GOLDEN_APPLE;
        if (!estPommeEnOr) {
            return;
        }
        if (!joueur.isSneaking()) {
            Msg.envoyer(joueur, "&cVous devez être accroupi pour utiliser ce pouvoir.");
            return;
        }
        if (plugin.getGameManager().getEpisodeActuel() < DruideRole.EPISODE_MIN) {
            Msg.envoyer(joueur, "&cVotre pouvoir n'est pas encore actif (à partir de l'épisode " + DruideRole.EPISODE_MIN + ").");
            return;
        }
        if (gp.getEtat("druide_utilise_ce_episode", false)) {
            Msg.envoyer(joueur, "&cVous avez déjà utilisé ce pouvoir cet épisode.");
            return;
        }
        if (plugin.getGameManager().pouvoirsVillageBloques()) {
            Msg.envoyer(joueur, "&cLes pouvoirs du Village sont bloqués pour le moment.");
            return;
        }
        gp.setEtat("druide_utilise_ce_episode", true);
        long sombresProches = plugin.getGameManager().getJoueursVivants().stream()
                .filter(g -> g.getAura() == Aura.OBSCURE && g.getPlayer() != null
                        && g.getPlayer().getWorld().equals(joueur.getWorld())
                        && g.getPlayer().getLocation().distance(joueur.getLocation()) <= DruideRole.RAYON)
                .count();
        sombresProches--;
        Msg.envoyer(joueur, "&2La nature vous murmure : &a" + sombresProches + " &2aura(s) Obscure(s) détectée(s) dans un rayon de 50 blocs.");
    }

    // NOTE : l'ancien handler surDeconnexion(PlayerQuitEvent) qui éliminait le joueur
    // instantanément à la déconnexion a été retiré (il faisait doublon avec le système de
    // grâce de 2 minutes de DeconnexionListener/GameManager#surDeconnexion, et s'exécutait
    // AVANT lui à priorité égale => mort immédiate au lieu d'attendre les 2 minutes). La
    // déconnexion en pleine partie est désormais gérée exclusivement par DeconnexionListener.

    @EventHandler(ignoreCancelled = true)
    public void surClicMenuCouleur(org.bukkit.event.inventory.InventoryClickEvent event) {
        String titre = event.getView() != null ? event.getView().getTitle() : null;
        String prefixeAttendu = "§8" + com.lguhc.util.CouleursDisponibles.PREFIXE_TITRE_MENU;
        if (titre == null || !titre.startsWith(prefixeAttendu)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player) || event.getCurrentItem() == null) {
            return;
        }
        Player p = (Player) event.getWhoClicked();
        GamePlayer gp = plugin.getGameManager().getGamePlayer(p);
        if (gp == null) {
            return;
        }
        String nomCible = titre.substring(prefixeAttendu.length());
        Player cibleJoueur = org.bukkit.Bukkit.getPlayerExact(nomCible);
        GamePlayer cibleGP = cibleJoueur != null ? plugin.getGameManager().getGamePlayer(cibleJoueur) : null;
        if (cibleGP == null) {
            return;
        }
        org.bukkit.inventory.ItemStack clique = event.getCurrentItem();

        boolean seColoreLuiMeme = cibleGP.getUuid().equals(gp.getUuid());

        if (clique.getType() == org.bukkit.Material.BARRIER) {
            gp.getCouleursPersonnalisees().remove(cibleGP.getUuid());
            Msg.envoyer(p, seColoreLuiMeme ? "&7Votre couleur personnelle a été réinitialisée."
                    : "&7Couleur de " + cibleGP.getNom() + " réinitialisée.");
            p.closeInventory();
            plugin.getScoreboardManager().mettreAJour(plugin);
            return;
        }
        if (clique.getType() == org.bukkit.Material.WOOL) {
            int index = clique.getDurability();
            if (index >= 0 && index < com.lguhc.util.CouleursDisponibles.COULEURS.length) {
                gp.getCouleursPersonnalisees().put(cibleGP.getUuid(), com.lguhc.util.CouleursDisponibles.COULEURS[index]);
                String cible = seColoreLuiMeme ? "Vous serez" : cibleGP.getNom() + " sera";
                Msg.envoyer(p, "&7" + cible + " maintenant affiché" + (seColoreLuiMeme ? "(e)" : "")
                        + " en " + com.lguhc.util.CouleursDisponibles.COULEURS[index]
                        + com.lguhc.util.CouleursDisponibles.NOMS[index] + "&7 (pour vous uniquement).");
                p.closeInventory();
                plugin.getScoreboardManager().mettreAJour(plugin);
            }
        }
    }
}
