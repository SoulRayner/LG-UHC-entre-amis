package com.lguhc.util;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Distribution d'objets à un joueur sans perte silencieuse quand l'inventaire est plein.
 *
 * Inventory#addItem(ItemStack...) renvoie une Map des objets qui n'ont pas pu rentrer,
 * mais si ce retour n'est pas récupéré (comme c'était le cas dans onAssign() de chaque
 * rôle), ces objets disparaissent purement et simplement - sans erreur, sans log. C'est
 * particulièrement sensible pour les kits de rôle : ils sont donnés ~20 minutes après le
 * début de partie (fin de l'épisode 1), quand les joueurs ont déjà rempli leur inventaire
 * en minant/explorant, contrairement au kit de départ (donné juste après un
 * inventaire.clear()) qui ne rencontre presque jamais ce problème.
 *
 * A utiliser à la place de joueur.getInventory().addItem(...) partout où on donne un ou
 * plusieurs objets à un joueur en cours de partie : ce qui ne rentre pas est déposé au sol
 * à ses pieds plutôt que perdu.
 */
public final class InventaireUtil {

    private InventaireUtil() {
    }

    /** Donne un ou plusieurs objets à joueur ; ce qui ne tient pas dans l'inventaire est droppé à ses pieds. */
    public static void donner(Player joueur, ItemStack... items) {
        if (joueur == null || items == null || items.length == 0) {
            return;
        }
        for (ItemStack restant : joueur.getInventory().addItem(items).values()) {
            joueur.getWorld().dropItem(joueur.getLocation(), restant);
        }
    }
}
