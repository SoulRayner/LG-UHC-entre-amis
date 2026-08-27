package com.lguhc.menu;

import com.lguhc.LGUHCPlugin;
import com.lguhc.game.EvenementAleatoireManager;
import com.lguhc.game.RoleType;
import com.lguhc.util.ItemBuilder;
import com.lguhc.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Menu de configuration ouvert par /lg config (réservé à lguhc.host, voir LGCommand), à utiliser
 * avant /lg start. 6 onglets dans le menu principal : Compo, Événement aléatoire (WIP), Règle,
 * Map, WIP #1, WIP #2.
 *
 * Routage des clics via ConfigMenuHolder (un InventoryHolder dédié posé sur chaque inventaire créé
 * ici) plutôt que par un préfixe dans le titre (contrairement à l'ancien menu de couleur,
 * LGCommand#ouvrirMenuCouleur / AbilityListener#surClicMenuCouleur) : plus robuste, aucun risque
 * de collision de titre.
 *
 * Persistance : chaque modification (toggle de rôle, +/- sur un réglage) écrit IMMÉDIATEMENT dans
 * plugin.getConfig() puis appelle saveConfig(), plutôt que d'attendre la fermeture du menu - un
 * crash serveur en pleine modification ne doit pas faire perdre le changement.
 *
 * Réglages de bordure (voir REGLAGES_REGLES et REGLAGE_BORDURE_TAILLE ci-dessous) : les clés
 * config.yml "bordure.minutes-avant-mouvement" et "bordure.taille-initiale" sont confirmées par
 * BorderManager#charger(ConfigurationSection). BorderManager gère aussi "bordure.taille-minimale"
 * et "bordure.secondes-par-bloc" (taille finale / vitesse de resserrement), non exposés ici : la
 * demande d'origine ne portait que sur la taille de départ.
 *
 * "monde.distance-spawn-min" (REGLAGE_DISTANCE_SPAWN, onglet Map) contrôle la "couronne" de
 * téléportation de départ dans GameManager#demarrer : 0 = désactivé (comportement classique,
 * disque plein), sinon les joueurs spawn entre cette valeur et +100 blocs du centre. Voir
 * GameManager#emplacementAleatoireEnCouronne pour le détail du tirage.
 */
public class ConfigMenu implements Listener {

    private final LGUHCPlugin plugin;

    // ---- Slots du menu principal (27 emplacements) ----
    private static final int SLOT_COMPO = 10;
    private static final int SLOT_EVENEMENTS = 11;
    private static final int SLOT_REGLES = 12;
    private static final int SLOT_MAP = 14;
    private static final int SLOT_WIP1 = 15;
    private static final int SLOT_WIP2 = 16;

    // ---- Slots communs aux pages "simples" à 27 emplacements (Compo>Catégories, Map, Événements, WIP) ----
    private static final int SLOT_RETOUR_27 = 22;
    private static final int SLOT_CATEGORIE_VILLAGE = 11;
    private static final int SLOT_CATEGORIE_LOUPS = 12;
    private static final int SLOT_CATEGORIE_HYBRIDE = 14;
    private static final int SLOT_CATEGORIE_SOLITAIRE = 15;

    // ---- Slots de la page Règle (45 emplacements) ----
    private static final int SLOT_RETOUR_REGLES = 40;

    // ---- Slots de la page Compo > liste des rôles (54 emplacements, pagination) ----
    private static final int SLOTS_PAR_PAGE_ROLES = 45; // lignes 0 à 4
    private static final int SLOT_RETOUR_ROLES = 45;
    private static final int SLOT_PAGE_PRECEDENTE = 48;
    private static final int SLOT_INFO_PAGE = 49;
    private static final int SLOT_PAGE_SUIVANTE = 50;

    public ConfigMenu(LGUHCPlugin plugin) {
        this.plugin = plugin;
    }

    // ================= Menu principal =================

    public void ouvrirMenuPrincipal(Player p) {
        ConfigMenuHolder holder = new ConfigMenuHolder(ConfigMenuHolder.Page.PRINCIPAL);
        Inventory inv = Bukkit.createInventory(holder, 27, "§8[Config] LGUHC");
        holder.setInventory(inv);
        remplirFiller(inv);

        inv.setItem(SLOT_COMPO, new ItemBuilder(Material.BOOKSHELF)
                .nom("&d&lCompo")
                .lore("&7Choisir les rôles actifs dans", "&7le tirage automatique, par camp.")
                .build());
        inv.setItem(SLOT_EVENEMENTS, new ItemBuilder(Material.NETHER_STAR)
                .nom("&b&lÉvénement aléatoire")
                .lore("&7Exposed / Exposed Inversé / Rumeurs,", "&7chacun activable indépendamment.")
                .build());
        inv.setItem(SLOT_REGLES, new ItemBuilder(Material.ANVIL)
                .nom("&e&lRègle")
                .lore("&7Enchantements, diamants,", "&7minuteries de partie.")
                .build());
        inv.setItem(SLOT_MAP, new ItemBuilder(Material.MAP)
                .nom("&a&lMap")
                .lore("&7Taille de la bordure.")
                .build());
        inv.setItem(SLOT_WIP1, new ItemBuilder(Material.BARRIER)
                .nom("&7&lWIP #1")
                .lore("&7Ne fait rien pour l'instant.")
                .build());
        inv.setItem(SLOT_WIP2, new ItemBuilder(Material.WEB)
                .nom("&7&lWIP #2")
                .lore("&7Ne fait rien pour l'instant.")
                .build());

        p.openInventory(inv);
    }

    private void gererClicPrincipal(Player p, int slot) {
        if (slot == SLOT_COMPO) {
            ouvrirMenuCompoCategories(p);
        } else if (slot == SLOT_EVENEMENTS) {
            ouvrirMenuEvenements(p);
        } else if (slot == SLOT_REGLES) {
            ouvrirMenuRegles(p);
        } else if (slot == SLOT_MAP) {
            ouvrirMenuMap(p);
        } else if (slot == SLOT_WIP1) {
            ouvrirMenuWip(p, 1);
        } else if (slot == SLOT_WIP2) {
            ouvrirMenuWip(p, 2);
        }
    }

    // ================= Compo : catégories =================

    public void ouvrirMenuCompoCategories(Player p) {
        ConfigMenuHolder holder = new ConfigMenuHolder(ConfigMenuHolder.Page.COMPO_CATEGORIES);
        Inventory inv = Bukkit.createInventory(holder, 27, "§8[Config] Compo");
        holder.setInventory(inv);
        remplirFiller(inv);

        inv.setItem(SLOT_CATEGORIE_VILLAGE, iconeCategorie(CategorieRole.VILLAGE));
        inv.setItem(SLOT_CATEGORIE_LOUPS, iconeCategorie(CategorieRole.LOUPS));
        inv.setItem(SLOT_CATEGORIE_HYBRIDE, iconeCategorie(CategorieRole.HYBRIDE));
        inv.setItem(SLOT_CATEGORIE_SOLITAIRE, iconeCategorie(CategorieRole.SOLITAIRE));
        inv.setItem(SLOT_RETOUR_27, itemRetour());

        p.openInventory(inv);
    }

    private ItemStack iconeCategorie(CategorieRole categorie) {
        ItemStack icone = new ItemStack(categorie.getMaterielIcone(), 1, categorie.getDonneeIcone());
        ItemMeta meta = icone.getItemMeta();
        meta.setDisplayName(categorie.getNomAffiche());
        List<RoleType> roles = categorie.getRoles();
        int actifs = 0;
        for (RoleType type : roles) {
            if (plugin.getCompositionManager().estActif(type)) {
                actifs++;
            }
        }
        meta.setLore(Arrays.asList(
                Msg.c("&7" + actifs + "/" + roles.size() + " rôle(s) actif(s)"),
                Msg.c("&7Clic : voir/modifier")
        ));
        icone.setItemMeta(meta);
        return icone;
    }

    private void gererClicCompoCategories(Player p, int slot) {
        if (slot == SLOT_RETOUR_27) {
            ouvrirMenuPrincipal(p);
            return;
        }
        CategorieRole categorie = categorieAuSlot(slot);
        if (categorie != null) {
            ouvrirMenuCompoRoles(p, categorie, 0);
        }
    }

    private CategorieRole categorieAuSlot(int slot) {
        if (slot == SLOT_CATEGORIE_VILLAGE) return CategorieRole.VILLAGE;
        if (slot == SLOT_CATEGORIE_LOUPS) return CategorieRole.LOUPS;
        if (slot == SLOT_CATEGORIE_HYBRIDE) return CategorieRole.HYBRIDE;
        if (slot == SLOT_CATEGORIE_SOLITAIRE) return CategorieRole.SOLITAIRE;
        return null;
    }

    // ================= Compo : liste des rôles (paginée) =================

    /**
     * Page paginée (45 rôles max par page, voir SLOTS_PAR_PAGE_ROLES) : la composition actuelle
     * n'en a besoin que pour une poignée de rôles par catégorie, mais la pagination est prête pour
     * accueillir les prochains rôles sans qu'un menu à taille fixe ne déborde silencieusement.
     */
    public void ouvrirMenuCompoRoles(Player p, CategorieRole categorie, int page) {
        List<RoleType> roles = categorie.getRoles();
        int totalPages = Math.max(1, (int) Math.ceil(roles.size() / (double) SLOTS_PAR_PAGE_ROLES));
        int pageBornee = Math.max(0, Math.min(page, totalPages - 1));

        ConfigMenuHolder holder = new ConfigMenuHolder(ConfigMenuHolder.Page.COMPO_ROLES, categorie, pageBornee);
        Inventory inv = Bukkit.createInventory(holder, 54, "§8[Config] " + categorie.getNomAffiche());
        holder.setInventory(inv);
        remplirFiller(inv);

        int depart = pageBornee * SLOTS_PAR_PAGE_ROLES;
        for (int i = 0; i < SLOTS_PAR_PAGE_ROLES; i++) {
            int indexRole = depart + i;
            if (indexRole >= roles.size()) {
                break;
            }
            inv.setItem(i, construireItemRole(roles.get(indexRole)));
        }

        inv.setItem(SLOT_RETOUR_ROLES, itemRetour());
        inv.setItem(SLOT_INFO_PAGE, itemInfoPage(categorie, pageBornee, totalPages));
        if (pageBornee > 0) {
            inv.setItem(SLOT_PAGE_PRECEDENTE, itemNavigation("&e« Page précédente"));
        }
        if (pageBornee < totalPages - 1) {
            inv.setItem(SLOT_PAGE_SUIVANTE, itemNavigation("&ePage suivante »"));
        }

        p.openInventory(inv);
    }

    private ItemStack construireItemRole(RoleType type) {
        boolean actif = plugin.getCompositionManager().estActif(type);
        boolean verrouille = (type == RoleType.LOUP_GAROU);
        short donnee = actif ? donneeCouleurCamp(type) : 7; // gris = désactivé

        ItemStack laine = new ItemStack(Material.WOOL, 1, donnee);
        ItemMeta meta = laine.getItemMeta();
        meta.setDisplayName(type.getNomFormate());

        List<String> lore = new ArrayList<>();
        lore.add(Msg.c(type.getDescription()));
        lore.add("");
        if (verrouille) {
            lore.add(Msg.c("&7Toujours actif (rôle de secours de la composition)."));
        } else if (actif) {
            lore.add(Msg.c("&a✔ Actif dans la composition automatique"));
            lore.add(Msg.c("&7Clic : désactiver"));
        } else {
            lore.add(Msg.c("&c✘ Désactivé de la composition automatique"));
            lore.add(Msg.c("&7Clic : activer"));
        }
        meta.setLore(lore);

        if (actif) {
            // Léger effet brillant (sans afficher d'enchantement) pour repérer les rôles actifs au premier coup d'œil.
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        laine.setItemMeta(meta);
        return laine;
    }

    private short donneeCouleurCamp(RoleType type) {
        if (type.estHybride()) {
            return 10; // violet
        }
        switch (type.getCampDepart()) {
            case VILLAGE:
                return 5; // vert clair (lime)
            case LOUPS:
                return 14; // rouge
            case SOLO:
                return 1; // orange
            default:
                return 0; // blanc, ne devrait pas arriver
        }
    }

    private ItemStack itemInfoPage(CategorieRole categorie, int pageBornee, int totalPages) {
        ItemBuilder builder = new ItemBuilder(Material.BOOK)
                .nom("&e" + categorie.getNomAffiche() + " &7- Page " + (pageBornee + 1) + "/" + totalPages)
                .lore("&7Clic gauche : activer/désactiver un rôle", "&7pour le tirage automatique.", "&7Le Loup-Garou de base reste toujours actif.");
        return builder.build();
    }

    private void gererClicCompoRoles(Player p, ConfigMenuHolder holder, InventoryClickEvent event) {
        int slot = event.getSlot();
        if (slot == SLOT_RETOUR_ROLES) {
            ouvrirMenuCompoCategories(p);
            return;
        }
        if (slot == SLOT_PAGE_PRECEDENTE) {
            ouvrirMenuCompoRoles(p, holder.getCategorie(), holder.getPageIndex() - 1);
            return;
        }
        if (slot == SLOT_PAGE_SUIVANTE) {
            ouvrirMenuCompoRoles(p, holder.getCategorie(), holder.getPageIndex() + 1);
            return;
        }
        if (slot < 0 || slot >= SLOTS_PAR_PAGE_ROLES) {
            return; // zone de navigation/filler, rien à faire de plus
        }

        List<RoleType> roles = holder.getCategorie().getRoles();
        int indexRole = holder.getPageIndex() * SLOTS_PAR_PAGE_ROLES + slot;
        if (indexRole >= roles.size()) {
            return; // slot vide sur cette page
        }
        RoleType type = roles.get(indexRole);
        if (type == RoleType.LOUP_GAROU) {
            Msg.envoyer(p, "&7Le Loup-Garou de base reste toujours actif (rôle de secours quand la composition manque de rôles).");
            return;
        }

        boolean nouveauActif = !plugin.getCompositionManager().estActif(type);
        plugin.getCompositionManager().setActif(type, nouveauActif);
        sauvegarderRolesDesactives();
        event.getInventory().setItem(slot, construireItemRole(type));
        Msg.envoyer(p, (nouveauActif ? "&a" : "&c") + type.getNomAffiche()
                + (nouveauActif ? " activé" : " désactivé") + " dans la composition automatique.");
    }

    private void sauvegarderRolesDesactives() {
        List<String> noms = new ArrayList<>();
        for (RoleType type : plugin.getCompositionManager().getRolesDesactives()) {
            noms.add(type.name());
        }
        plugin.getConfig().set("compo-manuelle.roles-desactives", noms);
        plugin.saveConfig();
    }

    // ================= Événement aléatoire =================

    /**
     * 3 toggles indépendants (voir la demande d'origine : pouvoir activer n'importe lequel des 3
     * événements sans forcément activer les autres) - chacun persiste sa propre clé booléenne dans
     * "evenements-aleatoires.*" (voir EvenementAleatoireManager#charger). Exposed et Exposé Inversé
     * partagent malgré tout leurs 2 fenêtres de déclenchement (REGLAGES_EVENEMENTS ci-dessous) :
     * seul le fait qu'ils jouent ou non à chaque fenêtre est indépendant (voir
     * GameManager#declencherEvenementAleatoire). Rumeurs a sa propre fenêtre, indépendante
     * (REGLAGES_RUMEURS).
     */
    private static final int SLOT_TOGGLE_EXPOSE = 10;
    private static final int SLOT_TOGGLE_EXPOSE_INVERSE = 12;
    private static final int SLOT_TOGGLE_RUMEURS = 14;
    private static final int SLOT_RETOUR_EVENEMENTS = 40;

    /**
     * Fenêtres de déclenchement (en minutes de jeu réel depuis /lg start) partagées par Exposed et
     * Exposé Inversé - voir EvenementAleatoireManager, qui porte les mêmes clés/défauts côté
     * lecture. 1er événement (défaut 60-80 min) puis 2e (défaut 100-120 min).
     */
    private static final List<ReglageEntier> REGLAGES_EVENEMENTS = Arrays.asList(
            new ReglageEntier(19, "1er événement - min (min)", "evenements-aleatoires.premier-min-minutes", EvenementAleatoireManager.PREMIER_MIN_DEFAUT, 0, 180, 5),
            new ReglageEntier(20, "1er événement - max (min)", "evenements-aleatoires.premier-max-minutes", EvenementAleatoireManager.PREMIER_MAX_DEFAUT, 0, 180, 5),
            new ReglageEntier(23, "2e événement - min (min)", "evenements-aleatoires.second-min-minutes", EvenementAleatoireManager.SECOND_MIN_DEFAUT, 0, 240, 5),
            new ReglageEntier(24, "2e événement - max (min)", "evenements-aleatoires.second-max-minutes", EvenementAleatoireManager.SECOND_MAX_DEFAUT, 0, 240, 5)
    );

    /** Fenêtre de déclenchement indépendante de Rumeurs (défaut 80-120 min) - une seule, contrairement aux 2 partagées ci-dessus. */
    private static final List<ReglageEntier> REGLAGES_RUMEURS = Arrays.asList(
            new ReglageEntier(28, "Rumeurs - min (min)", "evenements-aleatoires.rumeurs-min-minutes", EvenementAleatoireManager.RUMEURS_MIN_DEFAUT, 0, 240, 5),
            new ReglageEntier(29, "Rumeurs - max (min)", "evenements-aleatoires.rumeurs-max-minutes", EvenementAleatoireManager.RUMEURS_MAX_DEFAUT, 0, 240, 5)
    );

    /**
     * Réglage propre à Exposé Inversé (indépendant des fenêtres partagées ci-dessus) : nombre de
     * joueurs vivants tirés au sort pour l'événement, qui est aussi mécaniquement le seuil minimum
     * de joueurs vivants requis pour qu'il soit jouable (voir
     * EvenementAleatoireManager#getExposeInverseJoueursMinimum() et
     * GameManager#declencherEvenementAleatoire()). Borné à 2 minimum : à 1 seul joueur montré,
     * l'événement reviendrait à annoncer son rôle en clair, comme Exposed.
     */
    private static final List<ReglageEntier> REGLAGES_EXPOSE_INVERSE = Arrays.asList(
            new ReglageEntier(21, "Exposé Inversé - joueurs", "evenements-aleatoires.expose-inverse.joueurs-minimum", EvenementAleatoireManager.EXPOSE_INVERSE_JOUEURS_MIN_DEFAUT, 2, 20, 1)
    );

    public void ouvrirMenuEvenements(Player p) {
        ConfigMenuHolder holder = new ConfigMenuHolder(ConfigMenuHolder.Page.EVENEMENTS);
        // 45 emplacements (au lieu de 27 avant l'ajout de Rumeurs) : nécessaire pour loger les 3
        // toggles indépendants ET les 6 réglages numériques (2 fenêtres partagées + 1 fenêtre
        // dédiée à Rumeurs) sans se marcher dessus - même taille que l'onglet Règle.
        Inventory inv = Bukkit.createInventory(holder, 45, "§8[Config] Événement aléatoire");
        holder.setInventory(inv);
        remplirFiller(inv);

        inv.setItem(SLOT_TOGGLE_EXPOSE, construireItemToggleExpose());
        inv.setItem(SLOT_TOGGLE_EXPOSE_INVERSE, construireItemToggleExposeInverse());
        inv.setItem(SLOT_TOGGLE_RUMEURS, construireItemToggleRumeurs());
        for (ReglageEntier reglage : REGLAGES_EVENEMENTS) {
            inv.setItem(reglage.slot, construireItemReglage(reglage));
        }
        for (ReglageEntier reglage : REGLAGES_RUMEURS) {
            inv.setItem(reglage.slot, construireItemReglage(reglage));
        }
        for (ReglageEntier reglage : REGLAGES_EXPOSE_INVERSE) {
            inv.setItem(reglage.slot, construireItemReglage(reglage));
        }
        inv.setItem(SLOT_RETOUR_EVENEMENTS, itemRetour());

        p.openInventory(inv);
    }

    private ItemStack construireItemToggleExpose() {
        return construireItemToggleEvenement("Exposed", plugin.getEvenementAleatoireManager().isExposeActif(),
                "&7Un pseudo tiré au sort est",
                "&7annoncé à côté de 4 rôles",
                "&7possibles (dont le sien).",
                "&7Partage ses 2 fenêtres de",
                "&7déclenchement avec Exposé",
                "&7Inversé (réglages du bas).");
    }

    private ItemStack construireItemToggleExposeInverse() {
        int nombreJoueurs = plugin.getEvenementAleatoireManager().getExposeInverseJoueursMinimum();
        return construireItemToggleEvenement("Exposé Inversé", plugin.getEvenementAleatoireManager().isExposeInverseActif(),
                "&7" + nombreJoueurs + " pseudos tirés au sort sont",
                "&7annoncés à côté d'UN SEUL rôle,",
                "&7réellement détenu par l'un d'eux",
                "&7(minimum réglable ci-dessous).",
                "&7Partage ses 2 fenêtres de",
                "&7déclenchement avec Exposed.");
    }

    private ItemStack construireItemToggleRumeurs() {
        return construireItemToggleEvenement("Rumeurs", plugin.getEvenementAleatoireManager().isRumeursActif(),
                "&720 secondes pour envoyer",
                "&7un message dans le chat, puis",
                "&7tous les messages reçus sont",
                "&7réaffichés anonymement et",
                "&7dans le désordre.",
                "&7Fenêtre de déclenchement",
                "&7indépendante (réglages du bas).");
    }

    /** Icône générique d'un toggle d'événement aléatoire, partagée par les 3 constructeurs ci-dessus pour ne jamais désynchroniser leur apparence (même technique brillante que construireItemRole()). */
    private ItemStack construireItemToggleEvenement(String nomAffiche, boolean actif, String... loreDescriptif) {
        List<String> lore = new ArrayList<>(Arrays.asList(loreDescriptif));
        lore.add("");
        lore.add(actif ? "&aActuellement activé." : "&cActuellement désactivé.");
        lore.add("&7Clic : " + (actif ? "désactiver" : "activer"));

        ItemStack item = new ItemBuilder(Material.NETHER_STAR)
                .nom((actif ? "&a&l✔ " : "&c&l✘ ") + nomAffiche)
                .lore(lore.toArray(new String[0]))
                .build();
        if (actif) {
            ItemMeta meta = item.getItemMeta();
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void gererClicEvenements(Player p, InventoryClickEvent event) {
        int slot = event.getSlot();
        if (slot == SLOT_RETOUR_EVENEMENTS) {
            ouvrirMenuPrincipal(p);
            return;
        }
        if (slot == SLOT_TOGGLE_EXPOSE) {
            basculerEvenement(p, event.getInventory(), "expose.actif", EvenementAleatoireManager.EXPOSE_ACTIF_DEFAUT,
                    SLOT_TOGGLE_EXPOSE, "Exposed", this::construireItemToggleExpose);
            return;
        }
        if (slot == SLOT_TOGGLE_EXPOSE_INVERSE) {
            basculerEvenement(p, event.getInventory(), "expose-inverse.actif", EvenementAleatoireManager.EXPOSE_INVERSE_ACTIF_DEFAUT,
                    SLOT_TOGGLE_EXPOSE_INVERSE, "Exposé Inversé", this::construireItemToggleExposeInverse);
            return;
        }
        if (slot == SLOT_TOGGLE_RUMEURS) {
            basculerEvenement(p, event.getInventory(), "rumeurs.actif", EvenementAleatoireManager.RUMEURS_ACTIF_DEFAUT,
                    SLOT_TOGGLE_RUMEURS, "Rumeurs", this::construireItemToggleRumeurs);
            return;
        }
        for (ReglageEntier reglage : REGLAGES_EVENEMENTS) {
            if (reglage.slot == slot) {
                ajusterReglage(p, event.getInventory(), reglage, event.isRightClick());
                return;
            }
        }
        for (ReglageEntier reglage : REGLAGES_RUMEURS) {
            if (reglage.slot == slot) {
                ajusterReglage(p, event.getInventory(), reglage, event.isRightClick());
                return;
            }
        }
        for (ReglageEntier reglage : REGLAGES_EXPOSE_INVERSE) {
            if (reglage.slot == slot) {
                ajusterReglage(p, event.getInventory(), reglage, event.isRightClick());
                return;
            }
        }
    }

    /**
     * Bascule (actif <-> inactif) l'un des 3 événements aléatoires, INDÉPENDAMMENT des 2 autres -
     * voir la demande d'origine. Même politique de persistance que le reste du menu (voir doc de
     * classe) : écrit immédiatement dans config.yml + saveConfig(), recharge
     * EvenementAleatoireManager à chaud, puis ne reconstruit que l'icône concernée (les 2 autres
     * toggles ne changent pas).
     */
    private void basculerEvenement(Player p, Inventory inv, String cle, boolean defaut, int slot,
                                    String nomAffiche, java.util.function.Supplier<ItemStack> construireIcone) {
        boolean nouveauActif = !plugin.getConfig().getBoolean("evenements-aleatoires." + cle, defaut);
        plugin.getConfig().set("evenements-aleatoires." + cle, nouveauActif);
        plugin.saveConfig();
        plugin.getEvenementAleatoireManager().charger(plugin.getConfig().getConfigurationSection("evenements-aleatoires"));
        inv.setItem(slot, construireIcone.get());
        Msg.envoyer(p, (nouveauActif ? "&a" : "&c") + nomAffiche + (nouveauActif ? " activé" : " désactivé") + ".");
    }

    // ================= Règle =================

    public void ouvrirMenuRegles(Player p) {
        ConfigMenuHolder holder = new ConfigMenuHolder(ConfigMenuHolder.Page.REGLES);
        Inventory inv = Bukkit.createInventory(holder, 45, "§8[Config] Règle");
        holder.setInventory(inv);
        remplirFiller(inv);

        for (ReglageEntier reglage : REGLAGES_REGLES) {
            inv.setItem(reglage.slot, construireItemReglage(reglage));
        }
        inv.setItem(SLOT_RETOUR_REGLES, itemRetour());

        p.openInventory(inv);
    }

    private void gererClicRegles(Player p, InventoryClickEvent event) {
        int slot = event.getSlot();
        if (slot == SLOT_RETOUR_REGLES) {
            ouvrirMenuPrincipal(p);
            return;
        }
        for (ReglageEntier reglage : REGLAGES_REGLES) {
            if (reglage.slot == slot) {
                ajusterReglage(p, event.getInventory(), reglage, event.isRightClick());
                return;
            }
        }
    }

    // ================= Map =================

    public void ouvrirMenuMap(Player p) {
        ConfigMenuHolder holder = new ConfigMenuHolder(ConfigMenuHolder.Page.MAP);
        Inventory inv = Bukkit.createInventory(holder, 27, "§8[Config] Map");
        holder.setInventory(inv);
        remplirFiller(inv);

        inv.setItem(REGLAGE_BORDURE_TAILLE.slot, construireItemReglage(REGLAGE_BORDURE_TAILLE));
        inv.setItem(REGLAGE_DISTANCE_SPAWN.slot, construireItemReglage(REGLAGE_DISTANCE_SPAWN));
        inv.setItem(SLOT_RETOUR_27, itemRetour());

        p.openInventory(inv);
    }

    private void gererClicMap(Player p, InventoryClickEvent event) {
        int slot = event.getSlot();
        if (slot == SLOT_RETOUR_27) {
            ouvrirMenuPrincipal(p);
            return;
        }
        if (slot == REGLAGE_BORDURE_TAILLE.slot) {
            ajusterReglage(p, event.getInventory(), REGLAGE_BORDURE_TAILLE, event.isRightClick());
        } else if (slot == REGLAGE_DISTANCE_SPAWN.slot) {
            ajusterReglage(p, event.getInventory(), REGLAGE_DISTANCE_SPAWN, event.isRightClick());
        }
    }

    // ================= WIP #1 / WIP #2 =================

    public void ouvrirMenuWip(Player p, int numero) {
        ConfigMenuHolder.Page page = (numero == 1) ? ConfigMenuHolder.Page.WIP1 : ConfigMenuHolder.Page.WIP2;
        ConfigMenuHolder holder = new ConfigMenuHolder(page);
        Inventory inv = Bukkit.createInventory(holder, 27, "§8[Config] WIP #" + numero);
        holder.setInventory(inv);
        remplirFiller(inv);

        inv.setItem(13, new ItemBuilder(Material.BARRIER)
                .nom("&7&lWIP #" + numero)
                .lore("&7Ne fait rien pour l'instant.")
                .build());
        inv.setItem(SLOT_RETOUR_27, itemRetour());

        p.openInventory(inv);
    }

    // ================= Réglages numériques (Règle + Map) =================

    /** Une entrée = un réglage entier ajustable en jeu (+/- au clic), avec sa clé config.yml, sa valeur par défaut et ses bornes. Partagée entre l'affichage et la gestion des clics (voir construireItemReglage / ajusterReglage) pour ne jamais désynchroniser les deux. */
    private static final class ReglageEntier {
        final int slot;
        final String label;
        final String cheminConfig;
        final int defaut;
        final int min;
        final int max;
        final int pas;

        ReglageEntier(int slot, String label, String cheminConfig, int defaut, int min, int max, int pas) {
            this.slot = slot;
            this.label = label;
            this.cheminConfig = cheminConfig;
            this.defaut = defaut;
            this.min = min;
            this.max = max;
            this.pas = pas;
        }
    }

    /**
     * Réglages de l'onglet Règle, dans l'ordre d'affichage (slots 9 à 20 d'un inventaire à 45
     * emplacements, voir ouvrirMenuRegles). Défauts alignés sur BorderManager pour l'entrée
     * "bordure.*", et sur ChasseurRole.NIVEAU_PUISSANCE_DEFAUT pour le bonus Chasseur.
     */
    private static final List<ReglageEntier> REGLAGES_REGLES = Arrays.asList(
            new ReglageEntier(9, "Tranchant (général)", "survie-uhc.niveau-max-tranchant", 3, 0, 5, 1),
            new ReglageEntier(10, "Tranchant (Solitaire)", "survie-uhc.niveau-max-tranchant-solo", 4, 0, 5, 1),
            new ReglageEntier(11, "Protection (fer)", "survie-uhc.niveau-max-protection-fer", 3, 0, 4, 1),
            new ReglageEntier(12, "Protection (diamant)", "survie-uhc.niveau-max-protection-diamant", 2, 0, 4, 1),
            new ReglageEntier(13, "Puissance (arc)", "survie-uhc.niveau-max-puissance", 2, 0, 5, 1),
            new ReglageEntier(14, "Limite de stuff en diamant", "survie-uhc.limite-stuff-diamant", 2, 0, 9, 1),
            new ReglageEntier(15, "Limite de diamants minés", "survie-uhc.limite-diamants-mines", 17, 1, 64, 1),
            new ReglageEntier(16, "Temps avant annonce des rôles (min)", "episodes.duree-minutes", 20, 5, 60, 5),
            new ReglageEntier(17, "Temps avant Final Heal (min)", "survie-uhc.final-heal-minutes", 20, 5, 90, 5),
            new ReglageEntier(18, "Temps avant liste des Loups (min)", "survie-uhc.minutes-avant-liste-loups", 45, 10, 90, 5),
            new ReglageEntier(19, "Temps avant début de bordure (min)", "bordure.minutes-avant-mouvement", 20, 0, 60, 5),
            new ReglageEntier(20, "Puissance (bonus Chasseur)", "survie-uhc.chasseur-bonus-puissance", 3, 1, 5, 1)
    );

    /** Réglage de l'onglet Map (BorderManager#tailleInitiale, défaut 1000). */
    private static final ReglageEntier REGLAGE_BORDURE_TAILLE =
            new ReglageEntier(13, "Taille de la bordure", "bordure.taille-initiale", 1000, 250, 5000, 250);

    /**
     * Réglage de l'onglet Map (GameManager#demarrer, "couronne" de spawn). 0 = désactivé (spawn
     * classique sur tout le disque [0, monde.rayon-teleportation-depart]). À une valeur V > 0, les
     * joueurs apparaissent entre V et V+100 blocs du centre (voir emplacementAleatoireEnCouronne
     * dans GameManager). Max 3000 pour rester cohérent avec la borne max de la taille de bordure
     * ci-dessus (5000, donc un rayon de 2500) une fois la marge de sécurité de demarrer() retirée.
     */
    private static final ReglageEntier REGLAGE_DISTANCE_SPAWN =
            new ReglageEntier(11, "Distance de spawn (0 = désactivé)", "monde.distance-spawn-min", 0, 0, 3000, 100);

    private ItemStack construireItemReglage(ReglageEntier reglage) {
        int valeur = plugin.getConfig().getInt(reglage.cheminConfig, reglage.defaut);
        return new ItemBuilder(Material.PAPER)
                .nom("&e" + reglage.label + " &7: &f" + valeur)
                .lore(
                        "&7Clic gauche : &a+" + reglage.pas,
                        "&7Clic droit : &c-" + reglage.pas,
                        "&8(" + reglage.min + " - " + reglage.max + ")"
                )
                .build();
    }

    private void ajusterReglage(Player p, Inventory inv, ReglageEntier reglage, boolean decrement) {
        int actuel = plugin.getConfig().getInt(reglage.cheminConfig, reglage.defaut);
        int nouveau = decrement ? actuel - reglage.pas : actuel + reglage.pas;
        nouveau = Math.max(reglage.min, Math.min(reglage.max, nouveau));
        if (nouveau == actuel) {
            return;
        }
        plugin.getConfig().set(reglage.cheminConfig, nouveau);
        plugin.saveConfig();
        if (reglage.cheminConfig.startsWith("bordure.")) {
            // Recharge à chaud BorderManager (charger() relit taille-initiale/minutes-avant-mouvement/
            // taille-minimale/secondes-par-bloc depuis la section "bordure") pour appliquer le
            // changement sans redémarrage.
            plugin.getBorderManager().charger(plugin.getConfig().getConfigurationSection("bordure"));
        } else if (reglage.cheminConfig.startsWith("evenements-aleatoires.")) {
            // Idem pour EvenementAleatoireManager (fenêtres de déclenchement des 2 événements).
            plugin.getEvenementAleatoireManager().charger(plugin.getConfig().getConfigurationSection("evenements-aleatoires"));
            if (reglage.cheminConfig.equals("evenements-aleatoires.expose-inverse.joueurs-minimum")) {
                // La lore de l'icône du toggle Exposé Inversé affiche ce nombre ("X pseudos tirés
                // au sort") : la rafraîchir aussi, sinon elle reste affichée avec l'ancienne
                // valeur tant que le menu n'est pas rouvert.
                inv.setItem(SLOT_TOGGLE_EXPOSE_INVERSE, construireItemToggleExposeInverse());
            }
        }
        inv.setItem(reglage.slot, construireItemReglage(reglage));
        Msg.envoyer(p, "&7" + reglage.label + " &7→ &f" + nouveau);
    }

    // ================= Utilitaires communs =================

    private void remplirFiller(Inventory inv) {
        ItemStack pane = filler();
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, pane);
        }
    }

    private ItemStack filler() {
        ItemStack pane = new ItemStack(Material.STAINED_GLASS_PANE, 1, (short) 7); // gris
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        return pane;
    }

    private ItemStack itemRetour() {
        return new ItemBuilder(Material.ARROW).nom("&c« Retour").build();
    }

    private ItemStack itemNavigation(String nom) {
        return new ItemBuilder(Material.ARROW).nom(nom).build();
    }

    // ================= Clics =================

    @EventHandler(ignoreCancelled = true)
    public void surClic(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ConfigMenuHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player p = (Player) event.getWhoClicked();
        // Clic dans l'inventaire du JOUEUR (pas dans le menu lui-même) pendant que le menu est
        // ouvert : déjà annulé ci-dessus, rien de plus à router.
        if (event.getClickedInventory() == null || !(event.getClickedInventory().getHolder() instanceof ConfigMenuHolder)) {
            return;
        }
        if (!p.hasPermission("lguhc.host")) {
            // Sécurité si la permission a été retirée pendant que le menu était déjà ouvert.
            p.closeInventory();
            return;
        }

        ConfigMenuHolder holder = (ConfigMenuHolder) event.getClickedInventory().getHolder();
        int slot = event.getSlot();

        switch (holder.getPage()) {
            case PRINCIPAL:
                gererClicPrincipal(p, slot);
                break;
            case COMPO_CATEGORIES:
                gererClicCompoCategories(p, slot);
                break;
            case COMPO_ROLES:
                gererClicCompoRoles(p, holder, event);
                break;
            case REGLES:
                gererClicRegles(p, event);
                break;
            case MAP:
                gererClicMap(p, event);
                break;
            case EVENEMENTS:
                gererClicEvenements(p, event);
                break;
            case WIP1:
            case WIP2:
                // Onglets pas encore implémentés (voir la demande d'origine) : seul le bouton
                // Retour y fait quelque chose.
                if (slot == SLOT_RETOUR_27) {
                    ouvrirMenuPrincipal(p);
                }
                break;
        }
    }

    /** Empêche de faire glisser un objet (drag) dans le menu : InventoryClickEvent#setCancelled ci-dessus ne couvre pas ce cas séparé de l'API Bukkit. */
    @EventHandler(ignoreCancelled = true)
    public void surGlisser(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ConfigMenuHolder) {
            event.setCancelled(true);
        }
    }
}
