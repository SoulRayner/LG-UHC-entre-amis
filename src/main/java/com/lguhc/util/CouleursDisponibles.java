package com.lguhc.util;

import org.bukkit.ChatColor;

/**
 * Les 16 couleurs proposées dans le menu /lg color, avec un nom affiché
 * pour chacune. Index partagé avec la donnée (data value) de la laine
 * utilisée comme icône dans le menu (0-15).
 */
public class CouleursDisponibles {

    public static final String PREFIXE_TITRE_MENU = "Couleur: ";

    public static final ChatColor[] COULEURS = {
            ChatColor.WHITE, ChatColor.GOLD, ChatColor.LIGHT_PURPLE, ChatColor.AQUA,
            ChatColor.YELLOW, ChatColor.GREEN, ChatColor.DARK_PURPLE, ChatColor.GRAY,
            ChatColor.DARK_GRAY, ChatColor.DARK_AQUA, ChatColor.BLUE, ChatColor.DARK_BLUE,
            ChatColor.DARK_RED, ChatColor.DARK_GREEN, ChatColor.RED, ChatColor.BLACK
    };

    public static final String[] NOMS = {
            "Blanc", "Orange", "Magenta", "Bleu clair", "Jaune", "Vert clair", "Violet", "Gris",
            "Gris fonce", "Cyan", "Bleu", "Bleu fonce", "Rouge fonce", "Vert fonce", "Rouge", "Noir"
    };

    private CouleursDisponibles() {
    }
}
