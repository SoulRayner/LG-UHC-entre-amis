package com.lguhc.commands;

import com.lguhc.LGUHCPlugin;
import com.lguhc.game.GamePlayer;
import com.lguhc.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LWCommand implements CommandExecutor {

    private final LGUHCPlugin plugin;

    public LWCommand(LGUHCPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Msg.c("&cCette commande est réservée aux joueurs."));
            return true;
        }
        Player p = (Player) sender;
        GamePlayer gp = plugin.getGameManager().getGamePlayer(p);
        if (gp == null) {
            Msg.envoyer(p, "&cVous n'êtes pas inscrit à la partie.");
            return true;
        }
        if (args.length == 0) {
            Msg.envoyer(p, "&cUsage : /lw <message> (affiché publiquement à votre mort)");
            return true;
        }
        String message = String.join(" ", args);
        if (message.length() > 100) {
            message = message.substring(0, 100);
        }
        gp.setDernierMot(message);
        Msg.envoyer(p, "&7Votre dernier mot a été enregistré : &f\"" + message + "\"");
        return true;
    }
}
