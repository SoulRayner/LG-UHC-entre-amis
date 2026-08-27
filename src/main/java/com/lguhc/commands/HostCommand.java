package com.lguhc.commands;

import com.lguhc.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * /host <message> : annonce serveur bien visible pour le staff/hôte, encadrée de deux lignes
 * pleines (voir LIGNE ci-dessous - le combo couleur + strikethrough sur une suite de "-" est le
 * classique "trait plein" du chat Minecraft : le strikethrough dessine une barre continue qui
 * comble les espaces entre les tirets). Même permission ("lguhc.host") que /lg start, /lg stop,
 * /lg admin... - voir README §3bis.
 */
public class HostCommand implements CommandExecutor {

    private static final String PERMISSION_STAFF = "lguhc.host";
    private static final String LIGNE = "&6&m--------------------------------------------------";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION_STAFF)) {
            sender.sendMessage(Msg.c("&cVous n'avez pas la permission de faire ça."));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(Msg.c("&cUsage : /host <message>"));
            return true;
        }

        String message = String.join(" ", args);
        String nom = sender.getName();

        Msg.broadcast(LIGNE);
        Msg.broadcast("");
        Msg.broadcast("&6&lHOST &f- &e\"" + nom + "\" &7: &f" + message);
        Msg.broadcast("");
        Msg.broadcast(LIGNE);
        return true;
    }
}
