package com.lguhc.util;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * État très léger associé à /helpop : quand un membre du staff clique sur [Répondre] (ou tape
 * "/helpop reply <joueur>" à la main), on retient ici "ce staff attend d'écrire un message
 * destiné à ce joueur". Le prochain message de chat qu'il enverra sera intercepté par
 * {@link com.lguhc.listeners.HelpOpListener}, transformé en message privé, puis retiré d'ici
 * (usage unique : pour répondre à nouveau il faut recliquer sur [Répondre], ou retaper la
 * commande - évite de "coincer" silencieusement le chat d'un membre du staff qui aurait oublié
 * qu'il est en train de répondre).
 *
 * Volontairement une classe à part (comme les autres managers de LGUHCPlugin) plutôt qu'un champ
 * statique dans la commande : la commande ET le listener de chat ont besoin d'y accéder.
 */
public class HelpOpManager {

    /** staffUUID -> UUID du joueur auquel le prochain message de ce staff doit être envoyé. */
    private final Map<UUID, UUID> enAttenteReponse = new HashMap<>();

    public void definirCibleReponse(Player staff, Player cible) {
        enAttenteReponse.put(staff.getUniqueId(), cible.getUniqueId());
    }

    /** Null si ce staff n'est pas en train de répondre à quelqu'un. */
    public UUID getCibleReponse(Player staff) {
        return enAttenteReponse.get(staff.getUniqueId());
    }

    public boolean estEnAttenteReponse(Player staff) {
        return enAttenteReponse.containsKey(staff.getUniqueId());
    }

    public void retirerCibleReponse(Player staff) {
        enAttenteReponse.remove(staff.getUniqueId());
    }
}
