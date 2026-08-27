package com.lguhc.util;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public class Msg {

    /**
     * Traduit les codes de couleur & en codes Minecraft (§).
     */
    public static String c(String msg) {
        if (msg == null) return "";
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    public static String color(String msg) {
        return c(msg);
    }

    /**
     * Envoie un message coloré à un joueur.
     */
    public static void envoyer(Player player, String message) {
        if (player != null && message != null) {
            player.sendMessage(c(message));
        }
    }

    public static void send(Player player, String message) {
        envoyer(player, message);
    }

    /**
     * Diffuse un message coloré à tout le serveur.
     */
    public static void broadcast(String message) {
        if (message != null) {
            Bukkit.broadcastMessage(c(message));
        }
    }

    /**
     * Envoie un message dans la barre d'action (ActionBar) compatible Spigot 1.8.8.
     */
    public static void envoyerActionBar(Player player, String message) {
        if (player == null || message == null) return;
        try {
            String colored = c(message);
            Object craftPlayer = player.getClass().getMethod("getHandle").invoke(player);
            Object playerConnection = craftPlayer.getClass().getField("playerConnection").get(craftPlayer);

            Class<?> chatSerializerClass = Class.forName("net.minecraft.server.v1_8_R3.IChatBaseComponent$ChatSerializer");
            Class<?> iChatBaseComponentClass = Class.forName("net.minecraft.server.v1_8_R3.IChatBaseComponent");
            Class<?> packetPlayOutChatClass = Class.forName("net.minecraft.server.v1_8_R3.PacketPlayOutChat");

            Object chatComponent = chatSerializerClass.getMethod("a", String.class)
                    .invoke(null, "{\"text\":\"" + colored.replace("\"", "\\\"") + "\"}");
            Object packet = packetPlayOutChatClass.getConstructor(iChatBaseComponentClass, byte.class)
                    .newInstance(chatComponent, (byte) 2);

            playerConnection.getClass().getMethod("sendPacket", Class.forName("net.minecraft.server.v1_8_R3.Packet"))
                    .invoke(playerConnection, packet);
        } catch (Exception e) {
            player.sendMessage(c(message));
        }
    }

    public static void barreAction(Player player, String message) {
        envoyerActionBar(player, message);
    }

    public static void sendActionBar(Player player, String message) {
        envoyerActionBar(player, message);
    }

    /**
     * Envoie un message cliquable via la commande tellraw (sans BungeeCord).
     */
    public static void envoyerCliquable(Player player, String text, String command, String hoverText) {
        if (player == null) return;
        try {
            String json = "{\"text\":\"" + c(text).replace("\"", "\\\"") + "\","
                    + "\"clickEvent\":{\"action\":\"run_command\",\"value\":\"" + command + "\"},"
                    + "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"" + c(hoverText).replace("\"", "\\\"") + "\"}}";

            Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "tellraw " + player.getName() + " " + json);
        } catch (Exception e) {
            player.sendMessage(c(text) + " " + ChatColor.GRAY + "(" + command + ")");
        }
    }

    public static void sendClickable(Player player, String text, String command, String hoverText) {
        envoyerCliquable(player, text, command, hoverText);
    }

    /**
     * Comme envoyerCliquable(), mais seule la FIN de la ligne est cliquable : le texte de base
     * (texteBase) reste du texte normal, et texteClic (ex : "[Répondre]") est accolé juste après
     * sur la même ligne de chat, avec son propre clic/survol. Sert à /helpop, où le message du
     * joueur ne doit pas être cliquable, seul le bouton "[Répondre]" doit l'être.
     * Implémenté en tellraw à 2 segments ("text" + "extra"), toujours sans dépendance à
     * BungeeCord-chat (voir exclusion dans pom.xml), donc compatible Spigot 1.8.8 pur.
     */
    public static void envoyerCliquableSuffixe(Player player, String texteBase, String texteClic, String commande, String hoverText) {
        if (player == null) return;
        try {
            String json = "{\"text\":\"" + escaperJson(c(texteBase)) + "\",\"extra\":[{\"text\":\"" + escaperJson(c(texteClic)) + "\","
                    + "\"clickEvent\":{\"action\":\"run_command\",\"value\":\"" + commande + "\"},"
                    + "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"" + escaperJson(c(hoverText)) + "\"}}]}";

            Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "tellraw " + player.getName() + " " + json);
        } catch (Exception e) {
            player.sendMessage(c(texteBase) + " " + c(texteClic) + " (" + commande + ")");
        }
    }

    /**
     * Échappement JSON minimal mais nécessaire ici : contrairement aux autres méthodes tellraw
     * ci-dessus (qui n'injectent que des textes fixes du plugin), envoyerCliquableSuffixe() peut
     * recevoir du texte tapé librement par un joueur (message /helpop) - antislash et retours à
     * la ligne doivent être neutralisés en plus des guillemets, sous peine de tellraw invalide.
     */
    private static String escaperJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }
}