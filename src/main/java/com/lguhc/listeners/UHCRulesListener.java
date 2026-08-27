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
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerAchievementAwardedEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;

public class UHCRulesListener implements Listener {

    private final LGUHCPlugin plugin;

    /** XP donnée par lingot, identique à la vraie fonte au four vanilla 1.8 (FurnaceRecipes). */
    private static final double XP_FONTE_FER = 1.0;
    private static final double XP_FONTE_OR = 1.0;
    private static final double XP_FONTE_DIAMAND = 1.0;

    /**
     * Multiplicateur d'XP appliqué à TOUS les minerais (fer/or via la fonte simulée ci-dessus,
     * et charbon/diamant/redstone/lapis/émeraude/quartz du Nether via leur XP vanilla naturelle,
     * boostée dans surMinage). 1.2 = +20% par rapport au vanilla/à la fonte réelle.
     */
    private static final double MULTIPLICATEUR_XP_MINERAIS = 2;

    /** Taux de drop de pomme par feuille cassée, très largement boosté par rapport au vanilla (~0.5%). */
    private static final double TAUX_DROP_POMME = 0.5;

    /** Chance de récupérer du silex plutôt que du gravier en cassant du gravier (10% en vanilla). */
    private static final double TAUX_DROP_SILEX = 0.5;

    /**
     * Multiplicateur de dégâts qu'un coup critique doit donner par rapport à un coup de base
     * (1.0 = aucun bonus, 1.5 = critique vanilla complet). Doit rester strictly entre les deux :
     * un critique reste toujours meilleur qu'un coup de base, mais moins fort qu'un critique
     * Minecraft normal.
     */
    private static final double MULTIPLICATEUR_CRITIQUE_VOULU = 1.25;

    /** Multiplicateur que le serveur (NMS) a DEJA appliqué à l'attaque avant même que cet évènement Bukkit soit levé. */
    private static final double MULTIPLICATEUR_CRITIQUE_VANILLA = 1.5;

    /**
     * Probabilité d'annuler le dégât qu'une enclume vient de subir. En vanilla, chaque réparation
     * a 12% de chance d'endommager l'enclume (codé en dur côté serveur, jusqu'à la casser
     * complètement au 3ème dégât). A 0.6, la chance réelle de dégât retombe à 12% * (1 - 0.6) =
     * 4.8% par réparation, soit environ 2,5x plus de réparations avant la casse qu'en vanilla.
     * Montez vers 1.0 pour une enclume quasi incassable, ou vers 0.0 pour le comportement vanilla.
     */
    private static final double CHANCE_ANNULATION_DEGAT_ENCLUME = 0.6;

    /** Durée de l'invincibilité individuelle accordée à un joueur qui respawn en pleine partie (voir surRespawn). */
    private static final long DUREE_INVINCIBILITE_RESPAWN_MS = 30 * 1000L;

    public UHCRulesListener(LGUHCPlugin plugin) {
        this.plugin = plugin;
    }

    private void donnerExpFonte(Location lieu, GamePlayer gp, double montantParLingot) {
        double accumulee = gp.getEtat("xp_fonte_accumulee", 0.0) + (montantParLingot * MULTIPLICATEUR_XP_MINERAIS);
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

    /**
     * @param joueur le joueur en train d'enchanter/réparer (peut être null si l'appelant ne le
     *               connaît pas) : sert uniquement à distinguer le plafond Tranchant du Solitaire
     *               (Assassin), qui a droit à un niveau de plus que le reste de la partie. Tout le
     *               reste (Puissance, Protection, interdictions) ne dépend pas du joueur.
     */
    private int maxAutorise(Enchantment ench, Material materiel, Player joueur) {
        FileConfiguration config = plugin.getConfig();
        RoleType role = getRoleJoueur(joueur);

        if (ench.equals(Enchantment.FIRE_ASPECT)) return 0;
        if (ench.equals(Enchantment.KNOCKBACK))   return 0;
        if (ench.equals(Enchantment.ARROW_FIRE))  return 0;


        if (ench.equals(Enchantment.ARROW_KNOCKBACK)) {
            return (role == RoleType.CUPIDON) ? 1 : 0;
        }


        if (ench.equals(Enchantment.DAMAGE_ALL)) {
            return estCampSolo(joueur)
                    ? config.getInt("survie-uhc.niveau-max-tranchant-solo", 4)
                    : config.getInt("survie-uhc.niveau-max-tranchant", 3);
        }

        if (ench.equals(Enchantment.ARROW_DAMAGE)) {
            if (role == RoleType.CHASSEUR) {
                return config.getInt("survie-uhc.chasseur-bonus-puissance", 3);
            }
            return config.getInt("survie-uhc.niveau-max-puissance", 2);
        }

        if (ench.equals(Enchantment.PROTECTION_ENVIRONMENTAL)) {
            return capProtectionPour(materiel, role);
        }

        return Integer.MAX_VALUE;
    }

    private int capProtectionPour(Material materiel, RoleType role) {
        String nom = materiel.name();
        boolean estUneArmure = nom.contains("HELMET") || nom.contains("CHESTPLATE")
                || nom.contains("LEGGINGS") || nom.contains("BOOTS");
        if (!estUneArmure) {
            return Integer.MAX_VALUE;
        }

        if (nom.contains("DIAMOND")) {
            if (role == RoleType.ASSASSIN && materiel == Material.DIAMOND_CHESTPLATE) {
                return 3;
            }
            return plugin.getConfig().getInt("survie-uhc.niveau-max-protection-diamant", 2);
        }

        return plugin.getConfig().getInt("survie-uhc.niveau-max-protection-fer", 3);
    }

    private RoleType getRoleJoueur(Player joueur) {
        if (joueur == null) return null;
        GamePlayer gp = plugin.getGameManager().getGamePlayer(joueur);
        return gp != null ? gp.getRole() : null;
    }

    /** Vrai si ce joueur est actuellement du camp Solitaire (Assassin) — utilisé pour le plafond Tranchant dédié. */
    private boolean estCampSolo(Player joueur) {
        if (joueur == null) {
            return false;
        }
        GamePlayer gp = plugin.getGameManager().getGamePlayer(joueur);
        return gp != null && gp.getCamp() == Camp.SOLO;
    }

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
            int max = maxAutorise(entree.getKey(), event.getItem().getType(), event.getEnchanter());
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
     * Plafonne le résultat de l'enclume en 1.8.8 (remplace PrepareAnvilEvent de la 1.9+).
     */
    @EventHandler(ignoreCancelled = true)
    public void surClicEnclume(InventoryClickEvent event) {
        if (event.getInventory() == null || event.getInventory().getType() != InventoryType.ANVIL) {
            return;
        }
        if (event.getRawSlot() != 2) {
            return;
        }

        ItemStack resultat = event.getCurrentItem();
        if (resultat == null || resultat.getType() == Material.AIR) {
            return;
        }

        Player joueur = (event.getWhoClicked() instanceof Player) ? (Player) event.getWhoClicked() : null;

        if (joueur != null) {
            org.bukkit.block.Block blocCible = joueur.getTargetBlock((java.util.Set<Material>) null, 6);

            if (blocCible != null && blocCible.getType() == Material.ANVIL) {
                Location emplacementEnclume = blocCible.getLocation();
                Material typeAvant = blocCible.getType();
                byte dataAvant = blocCible.getData();

                Bukkit.getScheduler().runTask(plugin, () -> protegerDurabiliteEnclume(emplacementEnclume, typeAvant, dataAvant));
            }
        }

        boolean modifie = false;

        if (resultat.getItemMeta() instanceof EnchantmentStorageMeta) {
            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) resultat.getItemMeta();
            for (Map.Entry<Enchantment, Integer> entree : new HashMap<>(meta.getStoredEnchants()).entrySet()) {
                int max = maxAutorise(entree.getKey(), resultat.getType(), joueur);
                if (entree.getValue() > max) {
                    meta.removeStoredEnchant(entree.getKey());
                    if (max > 0) {
                        meta.addStoredEnchant(entree.getKey(), max, true);
                    }
                    modifie = true;
                }
            }
            if (modifie) {
                resultat.setItemMeta(meta);
            }
        } else {
            for (Map.Entry<Enchantment, Integer> entree : new HashMap<>(resultat.getEnchantments()).entrySet()) {
                int max = maxAutorise(entree.getKey(), resultat.getType(), joueur);
                if (entree.getValue() > max) {
                    resultat.removeEnchantment(entree.getKey());
                    if (max > 0) {
                        resultat.addUnsafeEnchantment(entree.getKey(), max);
                    }
                    modifie = true;
                }
            }
        }
    }

    /**
     * Rend les enclumes plus résistantes que la vanilla. Le dégât d'enclume (12% de chance par
     * réparation en vanilla, jusqu'à casser complètement au 3ème dégât) est codé en dur côté
     * serveur et n'est exposé par aucun évènement Bukkit en 1.8.8 (contrairement à
     * PrepareAnvilEvent en 1.9+) : impossible donc de l'empêcher AVANT qu'il survienne. On laisse
     * faire, puis on compare l'état du bloc juste avant/après le clic pour annuler le dégât avec
     * la probabilité CHANCE_ANNULATION_DEGAT_ENCLUME. La donnée du bloc encode l'orientation sur
     * les bits 0-1 et le niveau de dégât sur les bits 2-3 (0 = intacte, 1 = abîmée, 2 = très
     * abîmée) ; on ne touche jamais aux bits d'orientation.
     */
    private void protegerDurabiliteEnclume(Location emplacement, Material typeAvant, byte dataAvant) {
        if (typeAvant != Material.ANVIL) {
            return;
        }
        int orientation = dataAvant & 0x3;
        int degatAvant = (dataAvant >> 2) & 0x3;

        org.bukkit.block.Block bloc = emplacement.getBlock();
        if (bloc.getType() != Material.ANVIL) {
            // L'enclume vient de casser complètement (dégât au-delà de "très abîmée").
            if (Math.random() < CHANCE_ANNULATION_DEGAT_ENCLUME) {
                bloc.setType(Material.ANVIL);
                bloc.setData((byte) (orientation | (degatAvant << 2)));
            }
            return;
        }

        byte dataApres = bloc.getData();
        int degatApres = (dataApres >> 2) & 0x3;
        if (degatApres > degatAvant && Math.random() < CHANCE_ANNULATION_DEGAT_ENCLUME) {
            bloc.setData((byte) (orientation | (degatAvant << 2)));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void surDegatsJoueurAvantPartie(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player attaquant = extraireAttaquant(event.getDamager());
        if (attaquant == null) {
            return;
        }
        if (!plugin.getGameManager().estEnCours() || plugin.getGameManager().getEpisodeActuel() < 2) {
            event.setCancelled(true);
        }
    }

    /** Retrouve le joueur réellement à l'origine d'un dégât, y compris pour une flèche (le "damager" est alors l'Arrow, pas le tireur). */
    private Player extraireAttaquant(Entity damager) {
        if (damager instanceof Player) {
            return (Player) damager;
        }
        if (damager instanceof Projectile) {
            ProjectileSource tireur = ((Projectile) damager).getShooter();
            if (tireur instanceof Player) {
                return (Player) tireur;
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void surMortJoueur(PlayerDeathEvent event) {
        Player joueur = event.getEntity();
        GamePlayer gp = plugin.getGameManager().getGamePlayer(joueur);
        if (gp == null || !gp.isVivant()) {
            return;
        }
        event.setDeathMessage(null);
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

        plugin.getGameManager().surMortReelle(gp, campDetecte, null);
    }

    /**
     * Gèle un joueur sur place (position bloquée, la vue reste libre) pendant le compte à rebours
     * de préparation qui suit /lg start, avant le vrai début de partie (voir
     * GameManager#demarrerCompteAReboursDebut). Piloté par le flag d'état "gel_debut_actif",
     * positionné/retiré par GameManager. On ne compare que X/Y/Z (pas yaw/pitch) : le joueur peut
     * toujours regarder autour de lui, il ne peut simplement pas se déplacer.
     */
    @EventHandler(ignoreCancelled = true)
    public void surMouvementGelDebut(PlayerMoveEvent event) {
        GamePlayer gp = plugin.getGameManager().getGamePlayer(event.getPlayer());
        if (gp == null || !gp.getEtat("gel_debut_actif", false)) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            event.setTo(new Location(to.getWorld(), from.getX(), from.getY(), from.getZ(), to.getYaw(), to.getPitch()));
        }
    }

    @EventHandler
    public void surRespawn(PlayerRespawnEvent event) {
        Player joueur = event.getPlayer();
        GameManager gm = plugin.getGameManager();

        if (!gm.estEnCours()) {
            Location emplacementLobby = gm.getEmplacementLobby();
            if (emplacementLobby != null) {
                event.setRespawnLocation(emplacementLobby);
            }
            return;
        }

        World mondeJeu = Bukkit.getWorld(plugin.getConfig().getString("monde.nom", "world"));
        if (mondeJeu != null) {
            event.setRespawnLocation(gm.emplacementAleatoireDansBordure(mondeJeu));
        }

        GamePlayer gp = gm.getGamePlayer(joueur);
        if (gp != null && (!gp.isVivant() || gp.isEnAttenteMort())) {
            plugin.getServer().getScheduler().runTask(plugin, () -> joueur.setGameMode(GameMode.SPECTATOR));
        }

        // Invincibilité de 30 secondes après un respawn (mob/chute/PvP à l'arrivée sur un
        // emplacement aléatoire de la bordure) : on prend le MAXIMUM avec une éventuelle
        // invincibilité déjà en cours (ex: invincibilité générale de début de partie) pour ne
        // jamais RÉDUIRE la protection d'un joueur qui respawn tôt dans la partie.
        if (gp != null) {
            long proposition = System.currentTimeMillis() + DUREE_INVINCIBILITE_RESPAWN_MS;
            long actuelle = gp.getEtat("invincible_jusqua", 0L);
            gp.setEtat("invincible_jusqua", Math.max(actuelle, proposition));
            Msg.envoyer(joueur, "&a&lVous êtes invincible pendant 30 secondes.");
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // Ne prévient de la fin que si l'invincibilité est VRAIMENT retombée entre-temps
                // (elle a pu être prolongée par ailleurs, ex: un nouveau respawn).
                if (gp.getEtat("invincible_jusqua", 0L) <= System.currentTimeMillis()) {
                    Msg.envoyer(joueur, "&c&lVous n'êtes plus invincible.");
                }
            }, DUREE_INVINCIBILITE_RESPAWN_MS / 50L);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void surDegatsGeneraux(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player joueur = (Player) event.getEntity();
        GamePlayer gp = plugin.getGameManager().getGamePlayer(joueur);
        if (gp == null) {
            return;
        }
        // EntityDamageByEntityEvent (PvP, mobs...) partage la même liste d'évènements que
        // EntityDamageEvent : ce handler intercepte donc bien TOUS les types de dégâts, pas
        // seulement les dégâts "génériques" (chute, lave, faim...).
        if (!gp.isVivant() || plugin.getGameManager().estProtege(gp)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void surDegatsFeuDebutPartie(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        boolean estDegatFeuOuLave = cause == EntityDamageEvent.DamageCause.FIRE
                || cause == EntityDamageEvent.DamageCause.FIRE_TICK
                || cause == EntityDamageEvent.DamageCause.LAVA;

        if (estDegatFeuOuLave && plugin.getGameManager().estEnCours()) {
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

        boolean probablementCritique = !attaquant.isOnGround() && attaquant.getFallDistance() > 0.0f;
        if (probablementCritique) {
            event.setDamage(event.getDamage() * (MULTIPLICATEUR_CRITIQUE_VOULU / MULTIPLICATEUR_CRITIQUE_VANILLA));
        }

        event.setDamage(event.getDamage() + 1.8);

        GamePlayer gpAttaquant = plugin.getGameManager().getGamePlayer(attaquant);
        if (gpAttaquant != null && gpAttaquant.isVivant() && beneficeForceDemiNiveau(gpAttaquant)) {
            event.setDamage(event.getDamage() + 1.5);
        }

        event.setDamage(event.getDamage() * 0.8);

        GamePlayer gpVictime = plugin.getGameManager().getGamePlayer(victime);
        if (gpVictime != null && gpVictime.isVivant() && gpVictime.getRole() == RoleType.ANCIEN) {
            event.setDamage(event.getDamage() * 0.95);
        }
    }

    private boolean beneficeForceDemiNiveau(GamePlayer gp) {
        if (gp.getRole() == RoleType.LOUP_PERFIDE) {
            return gp.getEtat("perfide_invisible_actif", false);
        }
        // Loup-Garou Amnésique : reçoit à la place un vrai PotionEffect Force I la nuit (voir
        // GameManager#appliquerEffetsPeriodiques) - propriété distinctive de ce rôle plutôt que
        // le bonus "demi-niveau" générique des autres Loups. Exclu ici pour ne pas cumuler les deux.
        if (gp.getRole() == RoleType.LOUP_GAROU_AMNESIQUE) {
            return false;
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
        ItemStack outil = event.getPlayer().getItemInHand();

        if (type == Material.IRON_ORE) {
            event.setCancelled(true);
            event.getBlock().setType(Material.AIR);
            //event.getBlock().getDrops().clear();
            int quantite = calculerDropFortune(outil, 1);
            event.getPlayer().getWorld().dropItemNaturally(event.getBlock().getLocation(), new ItemStack(Material.IRON_INGOT, quantite));
            donnerExpFonte(event.getBlock().getLocation(), gp, XP_FONTE_FER);
            return;
        }
        if (type == Material.GOLD_ORE) {
            event.setCancelled(true);
            event.getBlock().setType(Material.AIR);
            //event.getBlock().getDrops().clear();
            int quantite = calculerDropFortune(outil, 1);
            event.getPlayer().getWorld().dropItemNaturally(event.getBlock().getLocation(), new ItemStack(Material.GOLD_INGOT, quantite));
            donnerExpFonte(event.getBlock().getLocation(), gp, XP_FONTE_OR);
            return;
        }
        if (type == Material.DIAMOND_ORE) {
            int mines = gp.getEtat("diamants_mines", 0) + 1;
            gp.setEtat("diamants_mines", mines);
            int limite = plugin.getConfig().getInt("survie-uhc.limite-diamants-mines", 17);
            donnerExpFonte(event.getBlock().getLocation(), gp, XP_FONTE_DIAMAND);
            if (mines > limite) {
                event.setCancelled(true);
                event.getBlock().setType(Material.AIR);
                //event.getBlock().getDrops().clear();
                int quantite = calculerDropFortune(outil, 2);
                event.getPlayer().getWorld().dropItemNaturally(event.getBlock().getLocation(), new ItemStack(Material.GOLD_INGOT, quantite));
                Msg.envoyer(event.getPlayer(), "&6Limite de " + limite + " diamants minés atteinte : vous recevez 2 lingots d'or à la place.");
                return;
            }
        }

        if (estMineraiAvecExpVanilla(type)) {
            event.setExpToDrop((int) Math.round(event.getExpToDrop() * MULTIPLICATEUR_XP_MINERAIS));
        }
    }

    private boolean estMineraiAvecExpVanilla(Material type) {
        return type == Material.COAL_ORE
                || type == Material.DIAMOND_ORE
                || type == Material.REDSTONE_ORE
                || type == Material.GLOWING_REDSTONE_ORE
                || type == Material.LAPIS_ORE
                || type == Material.EMERALD_ORE
                || type == Material.QUARTZ_ORE;
    }

    private int calculerDropFortune(ItemStack outil, int dropDeBase) {
        if (outil == null || !outil.containsEnchantment(Enchantment.LOOT_BONUS_BLOCKS)) {
            return dropDeBase;
        }

        int niveau = outil.getEnchantmentLevel(Enchantment.LOOT_BONUS_BLOCKS);
        java.util.Random rand = new java.util.Random();

        int bonus = rand.nextInt(niveau + 2) - 1;
        if (bonus < 0) {
            bonus = 0;
        }

        return dropDeBase * (bonus + 1);
    }

    private static final java.util.Set<Material> PIECES_ARMURE_DIAMANT = java.util.EnumSet.of(
            Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS
    );

    @EventHandler(ignoreCancelled = true)
    public void surCraftDiamant(CraftItemEvent event) {
        if (!plugin.getGameManager().estEnCours() || event.getRecipe() == null || event.getRecipe().getResult() == null) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Material typeResultat = event.getRecipe().getResult().getType();

        // Si l'objet crafté n'est pas une pièce d'armure (ex: pioche, épée), on laisse passer sans limite
        if (!PIECES_ARMURE_DIAMANT.contains(typeResultat)) {
            return;
        }

        Player joueur = (Player) event.getWhoClicked();
        int limite = plugin.getConfig().getInt("survie-uhc.limite-pieces-armure-diamant", 2);

        if (compterArmureDiamant(joueur) >= limite) {
            event.setCancelled(true);
            Msg.envoyer(joueur, "&cVous avez déjà atteint la limite de " + limite + " pièces d'armure en diamant.");
        }
    }

    private int compterArmureDiamant(Player joueur) {
        int total = 0;
        // Vérifie les pièces d'armure dans l'inventaire
        for (ItemStack item : joueur.getInventory().getContents()) {
            if (item != null && PIECES_ARMURE_DIAMANT.contains(item.getType())) {
                total += item.getAmount();
            }
        }
        // Vérifie les pièces d'armure actuellement équipées
        for (ItemStack item : joueur.getInventory().getArmorContents()) {
            if (item != null && PIECES_ARMURE_DIAMANT.contains(item.getType())) {
                total += item.getAmount();
            }
        }
        return total;
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
     * Remplace le tirage vanilla (10% de chance de silex, sinon gravier) par un tirage à
     * TAUX_DROP_SILEX (50% par défaut) : on annule le drop naturel et on fait tomber nous-mêmes
     * soit du silex, soit du gravier, jamais les deux. Le Silk Touch (toujours du gravier en
     * vanilla, jamais de silex) reste respecté : on ne touche à rien dans ce cas.
     */
    @EventHandler(ignoreCancelled = true)
    public void surGravier(BlockBreakEvent event) {
        if (!plugin.getGameManager().estEnCours() || event.getBlock().getType() != Material.GRAVEL) {
            return;
        }
        ItemStack outil = event.getPlayer().getItemInHand();
        if (outil != null && outil.containsEnchantment(Enchantment.SILK_TOUCH)) {
            return;
        }
        event.setCancelled(true);
        event.getBlock().setType(Material.AIR);
        Material drop = Math.random() < TAUX_DROP_SILEX ? Material.FLINT : Material.GRAVEL;
        event.getPlayer().getWorld().dropItemNaturally(event.getBlock().getLocation(), new ItemStack(drop));
    }

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