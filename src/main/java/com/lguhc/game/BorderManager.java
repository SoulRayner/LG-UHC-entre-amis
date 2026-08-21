package com.lguhc.game;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Gère la bordure de monde : taille fixe au départ, puis resserrement
 * progressif après un délai, jusqu'à une taille minimale.
 */
public class BorderManager {

    private double tailleInitiale = 1000;
    private double tailleMinimale = 200;
    private long minutesAvantMouvement = 20;
    // Vitesse du resserrement : un bord de la bordure avance d'1 bloc toutes les X secondes.
    private double secondesParBloc = 15;

    // Empêche un double déclenchement si le resserrement est forcé (commande admin)
    // puis redéclenché automatiquement une fois le délai normal écoulé, ou l'inverse.
    private boolean resserrementDemarre = false;

    public void charger(ConfigurationSection racineBordure) {
        if (racineBordure == null) {
            return;
        }
        tailleInitiale = racineBordure.getDouble("taille-initiale", 1000);
        tailleMinimale = racineBordure.getDouble("taille-minimale", 200);
        minutesAvantMouvement = racineBordure.getLong("minutes-avant-mouvement", 20);
        secondesParBloc = racineBordure.getDouble("secondes-par-bloc", 15);
    }

    public void initialiser(World world, Location centre) {
        WorldBorder bordure = world.getWorldBorder();
        bordure.setCenter(centre);
        bordure.setSize(tailleInitiale);
        resserrementDemarre = false;
    }

    public long getMinutesAvantMouvement() {
        return minutesAvantMouvement;
    }

    /**
     * A appeler pour lancer le resserrement progressif jusqu'à la taille minimale.
     * Sans effet si déjà démarré (que ce soit via le délai normal ou un forçage
     * admin) : un seul déclenchement est pris en compte, le suivant est ignoré.
     *
     * @return true si cet appel a effectivement démarré le resserrement,
     *         false s'il était déjà en cours (permet à l'appelant d'adapter son message).
     */
    public boolean demarrerResserrement(World world) {
        if (resserrementDemarre) {
            return false;
        }
        resserrementDemarre = true;
        world.getWorldBorder().setSize(tailleMinimale, calculerDureeResserrementSecondes());
        return true;
    }

    /** Durée en secondes pour que chaque bord de la bordure avance à la vitesse configurée. */
    private long calculerDureeResserrementSecondes() {
        // La "taille" est le diamètre : les deux bords opposés bougent en même temps,
        // donc chaque bord ne parcourt que la moitié de la distance totale à résorber.
        double distanceParBord = (tailleInitiale - tailleMinimale) / 2.0;
        return Math.max(1L, Math.round(distanceParBord * secondesParBloc));
    }

    /** Utile pour la commande admin qui force le début du resserrement : évite un message trompeur si c'est déjà en cours. */
    public boolean isResserrementDemarre() {
        return resserrementDemarre;
    }

    public double getTailleInitiale() {
        return tailleInitiale;
    }
}
