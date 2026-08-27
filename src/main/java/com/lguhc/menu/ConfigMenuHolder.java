package com.lguhc.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Identifie quelle page du menu /lg config est ouverte dans un inventaire donné, pour router les
 * clics dans ConfigMenu#surClic. Contrairement à l'ancien menu de couleur (AbilityListener /
 * LGCommand#ouvrirMenuCouleur, qui parse un préfixe dans le TITRE de l'inventaire), on passe ici
 * par un InventoryHolder dédié : plus robuste (pas de risque de collision si un titre dépasse la
 * limite d'affichage du client, pas de parsing de chaîne à chaque clic).
 */
public class ConfigMenuHolder implements InventoryHolder {

    public enum Page {
        PRINCIPAL,
        COMPO_CATEGORIES,
        COMPO_ROLES,
        EVENEMENTS,
        REGLES,
        MAP,
        WIP1,
        WIP2
    }

    private final Page page;
    /** Seulement renseigné pour Page.COMPO_ROLES (quelle catégorie est affichée). Null sinon. */
    private final CategorieRole categorie;
    /** Seulement pertinent pour Page.COMPO_ROLES (pagination, 0 = première page). */
    private final int pageIndex;

    private Inventory inventory;

    public ConfigMenuHolder(Page page) {
        this(page, null, 0);
    }

    public ConfigMenuHolder(Page page, CategorieRole categorie, int pageIndex) {
        this.page = page;
        this.categorie = categorie;
        this.pageIndex = pageIndex;
    }

    public Page getPage() {
        return page;
    }

    public CategorieRole getCategorie() {
        return categorie;
    }

    public int getPageIndex() {
        return pageIndex;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
