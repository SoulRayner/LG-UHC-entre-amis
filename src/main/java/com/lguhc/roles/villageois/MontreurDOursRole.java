package com.lguhc.roles.villageois;

import com.lguhc.LGUHCPlugin;
import com.lguhc.game.Aura;
import com.lguhc.game.GamePlayer;
import com.lguhc.game.RoleType;
import com.lguhc.roles.Role;
import com.lguhc.util.Msg;
import org.bukkit.entity.Player;

/**
 * Montreur d'Ours : au début de chaque épisode, son ours grogne une fois
 * par joueur à Aura Sombre présent dans un rayon de 50 blocs autour de lui
 * (la plupart des Loups, mais pas le Loup-Garou Mystique qui est déguisé).
 * Le grognement est annoncé dans le chat général, visible par tous les
 * joueurs de la partie (pas seulement le Montreur). L'Aura du Montreur
 * lui-même ne change jamais, quel que soit le nombre de grognements.
 */
public class MontreurDOursRole implements Role {

    private static final double RAYON = 50.0;

    @Override
    public RoleType getType() {
        return RoleType.MONTREUR_OURS;
    }

    @Override
    public void onAssign(LGUHCPlugin plugin, GamePlayer gp) {
        Msg.envoyer(gp.getPlayer(), "&5Vous êtes le &dMontreur d'Ours&5 ! Au début de chaque épisode, votre ours grogne dans le chat général (visible de tous) une fois par Loup-Garou présent dans un rayon de 50 blocs autour de vous.");
    }

    @Override
    public void onEpisodeStart(LGUHCPlugin plugin, GamePlayer gp, int episode) {
        Player joueur = gp.getPlayer();
        if (joueur == null || !gp.isVivant()) {
            return;
        }
        // Cas particulier : le Montreur d'Ours lui-même, s'il a été infecté par l'Infect Père des
        // Loups (/lg infecter), est réellement devenu Loup-Garou (son camp réel bascule en Loups,
        // voir LGCommand#infecterPere) même si son rôle garde son nom "Montreur d'Ours" et que son
        // Aura personnelle reste Lumineuse (l'Aura est une propriété fixe du rôle, pas quelque
        // chose qui suit le camp réel - voir GameManager#finaliserMort pour le même principe
        // appliqué à l'Enfant Sauvage). Sans ce cas particulier son propre ours ne grognerait
        // jamais pour lui, contrairement à sa description ("une fois par Loup-Garou à proximité").
        int sombresProches = gp.getEtat("infecte", false) ? 1 : 0;
        for (GamePlayer autre : plugin.getGameManager().getJoueursVivants()) {
            if (autre == gp) {
                continue;
            }
            // Un Loup "classique" a l'Aura Obscure ; un infecté par l'Infect Père reste sur l'Aura
            // de son rôle d'origine (souvent Lumineuse) mais est un vrai Loup-Garou tout comme lui :
            // il doit donc aussi faire grogner l'ours, même si son Aura ne le montre pas. Le Loup
            // Mystique reste bien exclu ici (ni Aura Obscure, ni "infecté").
            boolean estSombreDetectable = autre.getAura() == Aura.OBSCURE || autre.getEtat("infecte", false);
            if (!estSombreDetectable) {
                continue;
            }
            Player autreJoueur = autre.getPlayer();
            if (autreJoueur == null || !autreJoueur.getWorld().equals(joueur.getWorld())) {
                continue;
            }
            if (autreJoueur.getLocation().distance(joueur.getLocation()) <= RAYON) {
                sombresProches++;
            }
        }
        if (sombresProches == 0) {
            // Pas de Pseudo affiché : sinon la révélation "silence" trahirait l'identité
            // du Montreur par élimination, alors même que le grognement doit rester anonyme.
            plugin.getGameManager().diffuser("&7Un ours reste silencieux ce matin quelque part... rien de suspect à proximité.");
        } else {
            StringBuilder grognements = new StringBuilder();
            for (int i = 0; i < sombresProches; i++) {
                if (i > 0) {
                    grognements.append(" ");
                }
                grognements.append("GRRRRR !");
            }
            // Pseudo du Montreur volontairement absent du message : seul le grognement est public.
            plugin.getGameManager().diffuser("&6🐻 &lUn ours gronde quelque part : &c&l" + grognements);
        }
    }
}

