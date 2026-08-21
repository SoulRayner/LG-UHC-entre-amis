package com.lguhc.util;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Petit constructeur fluide d'ItemStack, pour éviter de répéter le
 * getItemMeta()/setItemMeta() partout dans le code des rôles.
 */
public class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this(material, 1);
    }

    public ItemBuilder(Material material, int amount) {
        this.item = new ItemStack(material, amount);
        this.meta = item.getItemMeta();
    }

    public ItemBuilder nom(String nom) {
        meta.setDisplayName(Msg.c(nom));
        return this;
    }

    public ItemBuilder lore(String... lignes) {
        List<String> lore = new ArrayList<>();
        for (String ligne : lignes) {
            lore.add(Msg.c(ligne));
        }
        meta.setLore(lore);
        return this;
    }

    public ItemBuilder enchant(Enchantment enchantement, int niveau) {
        meta.addEnchant(enchantement, niveau, true);
        return this;
    }

    public ItemStack build() {
        item.setItemMeta(meta);
        return item;
    }

    /** Crée un livre enchanté "prêt à l'emploi" contenant un seul enchantement stocké. */
    public static ItemStack livreEnchante(Enchantment enchantement, int niveau) {
        ItemStack livre = new ItemStack(Material.ENCHANTED_BOOK);
        org.bukkit.inventory.meta.EnchantmentStorageMeta meta =
                (org.bukkit.inventory.meta.EnchantmentStorageMeta) livre.getItemMeta();
        meta.addStoredEnchant(enchantement, niveau, true);
        livre.setItemMeta(meta);
        return livre;
    }
}
