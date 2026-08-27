package com.lguhc.commands;

import com.lguhc.LGUHCPlugin;
import com.lguhc.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /helpop <message> : envoie une demande d'aide, colorée pour attirer l'œil, à tous les joueurs
 * en ligne ayant la permission "lguhc.host" (le staff/hôte, même permission que /lg admin, /lg
 * start... - voir README §3bis). Le message porte un bouton [Répondre] cliquable qui prépare une
 * réponse privée (voir HelpOpManager + HelpOpListener).
 *
 * "reply" et "cancel" sont traités comme des sous-commandes réservées (déclenchées par le clic,
 * ou tapables à la main par le staff) plutôt que comme le début d'une vraie demande d'aide -
 * exactement comme /lg utilise déjà args[0] pour choisir entre join/leave/start/etc. Un joueur
 * SANS la permission staff qui écrirait "/helpop reply ..." ou "/helpop cancel ..." est donc
 * toujours traité comme une vraie demande d'aide (voir la condition hasPermission ci-dessous).
 */
public class HelpOpCommand implements CommandExecutor {

    private static final String PERMISSION_STAFF = "lguhc.host";

    private final LGUHCPlugin plugin;

    public HelpOpCommand(LGUHCPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Msg.c("&cCette commande est réservée aux joueurs."));
            return true;
        }
        Player joueur = (Player) sender;

        if (args.length == 0) {
            Msg.envoyer(joueur, "&cUsage : /helpop <message> &7- votre message sera envoyé au staff.");
            return true;
        }

        String premierMot = args[0].toLowerCase();
        if (premierMot.equals("reply") && sender.hasPermission(PERMISSION_STAFF)) {
            return traiterReply(joueur, args);
        }
        if (premierMot.equals("cancel") && sender.hasPermission(PERMISSION_STAFF)) {
            return traiterCancel(joueur);
        }

        return envoyerDemandeAide(joueur, args);
    }

    // ================= /helpop <message> =================

    private boolean envoyerDemandeAide(Player joueur, String[] args) {
        String message = String.join(" ", args);
        boolean staffPrevenu = false;

        for (Player enLigne : Bukkit.getOnlinePlayers()) {
            if (!enLigne.hasPermission(PERMISSION_STAFF)) {
                continue;
            }
            staffPrevenu = true;

            Msg.envoyerCliquableSuffixe(enLigne,
                    "&c&l[HELP-OP] &f" + joueur.getName() + " &7: &f" + message + " ",
                    "&b&l[Répondre]",
                    "/helpop reply " + joueur.getName(),
                    "&7Cliquer pour répondre en privé à &f" + joueur.getName());

            // Son d'alerte pour que le staff remarque le message même s'il ne regarde pas le
            // chat. try/catch défensif : purement cosmétique, ne doit jamais empêcher la
            // demande d'aide (déjà envoyée juste au-dessus) de partir.
            try {
                enLigne.playSound(enLigne.getLocation(), Sound.NOTE_PLING, 1f, 1.6f);
            } catch (Exception ignored) {
                // son optionnel, jamais bloquant
            }
        }

        if (staffPrevenu) {
            Msg.envoyer(joueur, "&7[HelpOp] &fVotre message a été transmis au staff, patientez...");
        } else {
            Msg.envoyer(joueur, "&cAucun membre du staff n'est en ligne actuellement. Réessayez plus tard.");
        }
        return true;
    }

    // ================= /helpop reply <joueur> =================

    private boolean traiterReply(Player staff, String[] args) {
        if (args.length < 2) {
            Msg.envoyer(staff, "&cUsage : /helpop reply <joueur>");
            return true;
        }
        Player cible = Bukkit.getPlayerExact(args[1]);
        if (cible == null) {
            Msg.envoyer(staff, "&cJoueur introuvable ou hors ligne : " + args[1]);
            return true;
        }
        plugin.getHelpOpManager().definirCibleReponse(staff, cible);
        Msg.envoyer(staff, "&7[HelpOp] &fTapez votre prochain message dans le chat : il sera envoyé en privé à &e"
                + cible.getName() + "&f. &7(/helpop cancel pour annuler)");
        return true;
    }

    // ================= /helpop cancel =================

    private boolean traiterCancel(Player staff) {
        if (!plugin.getHelpOpManager().estEnAttenteReponse(staff)) {
            Msg.envoyer(staff, "&7[HelpOp] &fAucune réponse en attente.");
            return true;
        }
        plugin.getHelpOpManager().retirerCibleReponse(staff);
        Msg.envoyer(staff, "&7[HelpOp] &fRéponse annulée.");
        return true;
    }
}
