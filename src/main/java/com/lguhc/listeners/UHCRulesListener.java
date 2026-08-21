package com.lguhc.listeners;

import com.lguhc.LGUHCPlugin;
import com.lguhc.game.Camp;
import com.lguhc.game.GameManager;
import com.lguhc.game.GamePlayer;
import com.lguhc.game.RoleType;
import com.lguhc.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.player.PlayerAchievementAwardedEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class UHCRulesListener implements Listener {

    private final LGUHCPlugin plugin;

    /** XP donnée par lingot, identique à la vraie fonte au four vanilla 1.8 (FurnaceRecipes). */
    private static final double XP_FONTE_FER = 0.7;
    private static final double XP_FONTE_OR = 1.0;

    /** Taux de drop de pomme par feuille cassée, très largement boosté par rapport au vanilla (~0.5%). */
    private static final double TAUX_DROP_POMME = 0.5;

    public UHCRulesListener(LGUHCPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Simule l'XP qu'un vrai four donnerait à la récupération du lingot. L'XP de fonte
     * n'est pas un nombre entier (0.7 pour le fer, par exemple) : on accumule la partie
     * fractionnaire par joueur (dans son état GamePlayer, comme diamants_mines) et on ne
     * fait apparaître un orbe que lorsque le cumul dépasse 1, exactement comme le fait le
     * four vanilla en interne. L'orbe apparaît au sol, à ramasser comme n'importe quel XP
     * (pas donné directement), pour rester cohérent avec le comportement vanilla.
     */
    private void donnerExpFonte(Location lieu, GamePlayer gp, double montantParLingot) {
        double accumulee = gp.getEtat("xp_fonte_accumulee", 0.0) + montantParLingot;
        int pointsEntiers = (int) Math.floor(accumulee);
        if (pointsEntiers > 0) {
            ExperienceOrb orbe = lieu.getWorld().spawn(lieu, ExperienceOrb.class);
            orbe.setExperience(pointsEntiers);
            accumulee -= pointsEntiers;
        }
        gp.setEtat("xp_fonte_accumulee", accumulee);
    }

    @EventHandler(ignoreCancelled = true)
    public void surRegenNaturelle(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        if (!plugin.getConfig().getBoolean("survie-uhc.regeneration-naturelle", false)
                && event.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void surSeauDeLave(PlayerBucketEmptyEvent event) {
        if (plugin.getConfig().getBoolean("survie-uhc.seaux-de-lave-interdits", true)
                && event.getBucket() == Material.LAVA_BUCKET) {
            event.setCancelled(true);
        }
    }

    /** Niveau maximum autorisé pour cet enchantement sur ce matériau (Integer.MAX_VALUE = pas de limite définie). */
    private int maxAutorise(Enchantment ench, Material materiel) {
        if (ench.equals(Enchantment.FIRE_ASPECT) && plugin.getConfig().getBoolean("survie-uhc.enchant-fire-aspect-interdit", true)) {
            return 0;
        }
        if (ench.equals(Enchantment.ARROW_FIRE) && plugin.getConfig().getBoolean("survie-uhc.enchant-flame-interdit", true)) {
            return 0;
        }
        if (ench.equals(Enchantment.KNOCKBACK) && plugin.getConfig().getBoolean("survie-uhc.enchant-knockback-interdit", true)) {
            return 0;
        }
        if (ench.equals(Enchantment.DAMAGE_ALL)) {
            return plugin.getConfig().getInt("survie-uhc.niveau-max-tranchant", 3);
        }
        if (ench.equals(Enchantment.ARROW_DAMAGE)) {
            return plugin.getConfig().getInt("survie-uhc.niveau-max-puissance", 3);
        }
        if (ench.equals(Enchantment.PROTECTION_ENVIRONMENTAL)) {
            return capProtectionPour(materiel);
        }
        return Integer.MAX_VALUE;
    }

    /** Utilise le nom du matériau plutôt que la constante exacte (plus robuste face aux variantes cuir/or/fer/chaîne). */
    private int capProtectionPour(Material materiel) {
        String nom = materiel.name();
        boolean estUneArmure = nom.contains("HELMET") || nom.contains("CHESTPLATE") || nom.contains("LEGGINGS") || nom.contains("BOOTS");
        if (!estUneArmure) {
            return Integer.MAX_VALUE;
        }
        if (nom.contains("DIAMOND")) {
            return plugin.getConfig().getInt("survie-uhc.niveau-max-protection-diamant", 2);
        }
        return plugin.getConfig().getInt("survie-uhc.niveau-max-protection-fer", 3);
    }

    @EventHandler
    public void surEnchantement(EnchantItemEvent event) {
        Map<Enchantment, Integer> enchants = event.getEnchantsToAdd();
        Map<Enchantment, Integer> corrections = new HashMap<>();
        for (Map.Entry<Enchantment, Integer> entree : enchants.entrySet()) {
            int max = maxAutorise(entree.getKey(), event.getItem().getType());
            if (entree.getValue() > max) {
                corrections.put(entree.getKey(), max);
            }
        }
        for (Map.Entry<Enchantment, Integer> correction : corrections.entrySet()) {
            if (correction.getValue() <= 0) {
                enchants.remove(correction.getKey());
            } else {
                enchants.put(correction.getKey(), correction.getValue());
            }
        }
    }

    /**
     * Plafonne le résultat de l'enclume avec les mêmes limites que surEnchantement() ci-dessus
     * (Tranchant/Puissance/Protection, Feu/Flamme/Recul interdits...) : sans ce handler, l'enclume
     * contourne totalement ces plafonds - combiner deux Tranchant III (table, donc déjà plafonné)
     * produit un Tranchant IV en enclume, et ainsi de suite jusqu'au niveau max vanilla de
     * l'enchantement. S'applique aussi bien à un objet réellement enchanté (épée, armure...) qu'à
     * un livre enchanté (stockage différent côté API, d'où les deux branches ci-dessous).
     */
    @EventHandler(ignoreCancelled = true)
    public void surPreparationEnclume(org.bukkit.event.inventory.PrepareAnvilEvent event) {
        ItemStack resultat = event.getResult();
        if (resultat == null || resultat.getType() == Material.AIR) {
            return;
        }
        boolean modifie = false;

        if (resultat.getItemMeta() instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta) {
            org.bukkit.inventory.meta.EnchantmentStorageMeta meta =
                    (org.bukkit.inventory.meta.EnchantmentStorageMeta) resultat.getItemMeta();
            for (Map.Entry<Enchantment, Integer> entree : new HashMap<>(meta.getStoredEnchants()).entrySet()) {
                int max = maxAutorise(entree.getKey(), resultat.getType());
                if (entree.getValue() > max) {
                    meta.removeStoredEnchant(entree.getKey());
                    if (max > 0) {
                        meta.addStoredEnchant(entree.getKey(), max, true);
                    }
                    modifie = true;
                }
            }
            resultat.setItemMeta(meta);
        } else {
            for (Map.Entry<Enchantment, Integer> entree : new HashMap<>(resultat.getEnchantments()).entrySet()) {
                int max = maxAutorise(entree.getKey(), resultat.getType());
                if (entree.getValue() > max) {
                    resultat.removeEnchantment(entree.getKey());
                    if (max > 0) {
                        resultat.addUnsafeEnchantment(entree.getKey(), max);
                    }
                    modifie = true;
                }
            }
        }

        if (modifie) {
            event.setResult(resultat);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void surDegatsJoueurAvantPartie(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) {
            return;
        }
        if (!plugin.getGameManager().estEnCours() || plugin.getGameManager().getEpisodeActuel() < 2) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void surMortJoueur(PlayerDeathEvent event) {
        Player joueur = event.getEntity();
        GamePlayer gp = plugin.getGameManager().getGamePlayer(joueur);
        if (gp == null || !gp.isVivant()) {
            return;
        }
        event.setDeathMessage(null);
        // Interception du stuff AVANT de vider les drops : sinon il disparaît purement et
        // simplement (ni gardé sur le joueur, ni au sol). DeathManager le rendra au joueur
        // s'il est réanimé, ou le fera tomber au sol quand la mort deviendra définitive.
        plugin.getDeathManager().sauvegarderStuff(gp, event.getDrops());
        event.getDrops().clear();
        event.setDroppedExp(0);

        Player tueur = joueur.getKiller();
        Camp campDetecte = null;
        if (tueur != null) {
            gp.setDernierTueur(tueur.getUniqueId());
            GamePlayer gpTueur = plugin.getGameManager().getGamePlayer(tueur);
            if (gpTueur != null) {
                campDetecte = gpTueur.getCamp();
                if (gpTueur.getCamp() == Camp.LOUPS && gp.getRole() != RoleType.CHASSEUR) {
                    tueur.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, 20 * 60, 0));
                    tueur.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.ABSORPTION, 20 * 60, 0));
                }
            }
        }

        // La raison affichée n'est plus utilisée par DeathManager#annoncerMort (message générique désormais),
        // mais le paramètre reste dans la chaîne d'appel pour ne pas casser les autres appelants de eliminer(...).
        plugin.getGameManager().surMortReelle(gp, campDetecte, null);
    }

    @EventHandler
    public void surRespawn(PlayerRespawnEvent event) {
        Player joueur = event.getPlayer();
        GameManager gm = plugin.getGameManager();

        // Sans lit posé (normal en UHC), Bukkit retombe sur son comportement par défaut : il
        // respawn le joueur dans le monde "principal" du serveur (typiquement "world", pas
        // forcément celui où il vient de mourir), à un endroit que Minecraft calcule tout seul.
        // On force donc systématiquement une destination explicite selon le contexte, plutôt
        // que de laisser Bukkit décider.
        if (!gm.estEnCours()) {
            // Aucune partie en cours (avant /lg start, ou après /lg stop) : direction le lobby,
            // à son point d'apparition fixe.
            Location emplacementLobby = gm.getEmplacementLobby();
            if (emplacementLobby != null) {
                event.setRespawnLocation(emplacementLobby);
            }
            return;
        }

        // Partie en cours : on force la réapparition dans le monde de jeu, à l'intérieur de la
        // bordure actuelle, pour que le joueur reste dans le même monde et puisse continuer à
        // suivre/spectate la partie plutôt que de se retrouver ailleurs.
        World mondeJeu = Bukkit.getWorld(plugin.getConfig().getString("monde.nom", "world"));
        if (mondeJeu != null) {
            event.setRespawnLocation(gm.emplacementAleatoireDansBordure(mondeJeu));
        }

        // Repasse en spectateur au respawn s'il est mort définitivement OU encore dans sa
        // fenêtre de mort différée (résurrection encore possible par la Sorcière/l'Infect Père) :
        // dans les deux cas, il ne doit pas se retrouver en Survival au milieu de la partie.
        GamePlayer gp = gm.getGamePlayer(joueur);
        if (gp != null && (!gp.isVivant() || gp.isEnAttenteMort())) {
            plugin.getServer().getScheduler().runTask(plugin, () -> joueur.setGameMode(GameMode.SPECTATOR));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void surDegatsGeneraux(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player joueur = (Player) event.getEntity();
        GamePlayer gp = plugin.getGameManager().getGamePlayer(joueur);
        if (gp != null && !gp.isVivant()) {
            event.setCancelled(true);
        }
    }

    /**
     * Immunité totale aux dégâts de feu/lave pendant les X premières minutes de jeu réel
     * après /lg start (survie-uhc.duree-immunite-feu-minutes, 20 par défaut) : le compteur
     * utilisé est getTempsTotalEcouleSecondes(), indépendant des phases jour/nuit et des
     * /lg admin skip (contrairement à un minuteur de phase, il ne peut pas être avancé/reculé
     * par erreur par les tests admin). Ne s'applique que si une partie est réellement en
     * cours : sans ce garde-fou, un debutPartieTimestamp jamais initialisé (avant le tout
     * premier /lg start) donnerait un temps écoulé de 0, donc une immunité permanente au lobby.
     * Le joueur continue de prendre feu visuellement (pas de setFireTicks(0) ici), seuls les
     * dégâts sont annulés.
     */
    @EventHandler(ignoreCancelled = true)
    public void surDegatsFeuDebutPartie(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        boolean estDegatFeuOuLave = cause == EntityDamageEvent.DamageCause.FIRE
                || cause == EntityDamageEvent.DamageCause.FIRE_TICK
                || cause == EntityDamageEvent.DamageCause.LAVA;
        if (!estDegatFeuOuLave || !plugin.getGameManager().estEnCours()) {
            return;
        }
        long dureeImmuniteSecondes = plugin.getConfig().getLong("survie-uhc.duree-immunite-feu-minutes", 20) * 60L;
        if (plugin.getGameManager().getTempsTotalEcouleSecondes() < dureeImmuniteSecondes) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void surCombatGeneral(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player) || !(event.getEntity() instanceof Player)) {
            return;
        }
        if (!plugin.getGameManager().estEnCours() || plugin.getGameManager().getEpisodeActuel() < 2) {
            return;
        }
        Player attaquant = (Player) event.getDamager();
        Player victime = (Player) event.getEntity();

        // Nerf des coups critiques : un coup critique (heuristique : joueur en chute libre) n'inflige
        // plus que 35% de ses dégâts, soit 65% de dégâts en moins par rapport à un coup critique normal.
        boolean probablementCritique = !attaquant.isOnGround() && attaquant.getFallDistance() > 0.0f;
        if (probablementCritique) {
            event.setDamage(event.getDamage() * 0.35);
        }

        // Force I systématique pour l'attaquant : 60% du bonus de dégâts de la vraie Force I
        // vanilla (+3 dégâts en plein niveau -> +1.8 ici), appliqué sur CHAQUE coup, sans tirage au sort.
        event.setDamage(event.getDamage() + 1.8);

        // Force 0,5 de rôle : Loups (tous, la nuit) et Assassin (le jour). Un demi-niveau de Force I
        // vanilla = la moitié de son bonus de +3 dégâts, donc +1.5 ici, appliqué à chaque coup, sans
        // tirage au sort (remplace l'ancien PotionEffect en plein niveau appliqué en début de phase).
        GamePlayer gpAttaquant = plugin.getGameManager().getGamePlayer(attaquant);
        if (gpAttaquant != null && gpAttaquant.isVivant() && beneficeForceDemiNiveau(gpAttaquant)) {
            event.setDamage(event.getDamage() + 1.5);
        }

        // Résistance I systématique pour la victime : réduction de 20% des dégâts subis (le taux
        // exact de la vraie Résistance I vanilla), appliquée sur CHAQUE coup, sans tirage au sort.
        event.setDamage(event.getDamage() * 0.8);

        // Résistance 0,25 de rôle : l'Ancien. Un quart de niveau de Résistance I vanilla = un quart
        // de sa réduction de 20%, donc 5% ici (dégâts x0.95), appliqué à chaque coup, sans tirage au
        // sort (remplace l'ancien PotionEffect en plein niveau tiré au sort une fois par phase).
        GamePlayer gpVictime = plugin.getGameManager().getGamePlayer(victime);
        if (gpVictime != null && gpVictime.isVivant() && gpVictime.getRole() == RoleType.ANCIEN) {
            event.setDamage(event.getDamage() * 0.95);
        }
    }

    /**
     * Vrai si ce joueur doit recevoir la moitié de l'effet Force au moment présent :
     * tous les Loups la nuit (Loup-Garou, Loup Mystique, Loup Blanc, Infect Père des
     * Loups, Infecté, Enfant Sauvage transformé — tout ce qui a Camp.LOUPS), l'Assassin
     * le jour, et le Loup Perfide UNIQUEMENT pendant qu'il est invisible (son cas est à
     * part : il n'a pas Force juste parce que c'est la nuit, seulement le temps de son
     * invisibilité active, cf. LGCommand#loupPerfide).
     */
    private boolean beneficeForceDemiNiveau(GamePlayer gp) {
        if (gp.getRole() == RoleType.LOUP_PERFIDE) {
            return gp.getEtat("perfide_invisible_actif", false);
        }
        boolean nuit = plugin.getGameManager().estNuit();
        boolean estLoup = gp.getCamp() == Camp.LOUPS;
        boolean estAssassin = gp.getRole() == RoleType.ASSASSIN;
        return (estLoup && nuit) || (estAssassin && !nuit);
    }

    @EventHandler(ignoreCancelled = true)
    public void surMeteo(WeatherChangeEvent event) {
        if (event.toWeatherState()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void surCanneAPeche(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item != null && item.getType() == Material.FISHING_ROD && plugin.getGameManager().estEnCours()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void surMinage(BlockBreakEvent event) {
        if (!plugin.getGameManager().estEnCours()) {
            return;
        }
        GamePlayer gp = plugin.getGameManager().getGamePlayer(event.getPlayer());
        if (gp == null) {
            return;
        }
        Material type = event.getBlock().getType();

        if (type == Material.IRON_ORE) {
            event.setCancelled(true);
            event.getBlock().setType(Material.AIR);
            event.getPlayer().getWorld().dropItemNaturally(event.getBlock().getLocation(), new ItemStack(Material.IRON_INGOT));
            donnerExpFonte(event.getBlock().getLocation(), gp, XP_FONTE_FER);
            return;
        }
        if (type == Material.GOLD_ORE) {
            event.setCancelled(true);
            event.getBlock().setType(Material.AIR);
            event.getPlayer().getWorld().dropItemNaturally(event.getBlock().getLocation(), new ItemStack(Material.GOLD_INGOT));
            donnerExpFonte(event.getBlock().getLocation(), gp, XP_FONTE_OR);
            return;
        }
        if (type == Material.DIAMOND_ORE) {
            int mines = gp.getEtat("diamants_mines", 0) + 1;
            gp.setEtat("diamants_mines", mines);
            if (mines > 17) {
                event.setCancelled(true);
                event.getBlock().setType(Material.AIR);
                event.getPlayer().getWorld().dropItemNaturally(event.getBlock().getLocation(), new ItemStack(Material.GOLD_INGOT, 2));
                Msg.envoyer(event.getPlayer(), "&6Limite de 17 diamants minés atteinte : vous recevez 2 lingots d'or à la place.");
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void surFeuilles(BlockBreakEvent event) {
        if (!plugin.getGameManager().estEnCours()) {
            return;
        }
        Material type = event.getBlock().getType();
        if ((type == Material.LEAVES || type == Material.LEAVES_2) && Math.random() < TAUX_DROP_POMME) {
            event.getPlayer().getWorld().dropItemNaturally(event.getBlock().getLocation(), new ItemStack(Material.APPLE));
        }
    }

    /**
     * Succès Minecraft entièrement désactivés : rien à voir avec LGUHC, juste du bruit
     * indésirable dans le chat. Annuler l'événement empêche à la fois le message "X vient
     * d'obtenir le succès..." ET l'obtention réelle du succès (pas seulement son annonce).
     */
    @EventHandler(ignoreCancelled = true)
    public void surSucces(PlayerAchievementAwardedEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void surRepas(PlayerItemConsumeEvent event) {
        Material type = event.getItem() != null ? event.getItem().getType() : null;
        boolean estNourriture = type == Material.COOKED_BEEF || type == Material.COOKED_CHICKEN || type == Material.GRILLED_PORK
                || type == Material.BREAD || type == Material.APPLE || type == Material.CARROT || type == Material.BAKED_POTATO
                || type == Material.COOKED_FISH || type == Material.MELON;
        if (!estNourriture) {
            return;
        }
        Player joueur = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            joueur.setFoodLevel(20);
            joueur.setSaturation(Math.min(20f, joueur.getSaturation() + 6f));
        });
    }
}
