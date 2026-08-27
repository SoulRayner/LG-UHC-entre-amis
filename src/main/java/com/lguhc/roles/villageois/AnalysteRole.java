package com.lguhc.roles.villageois;

import com.lguhc.LGUHCPlugin;
import com.lguhc.game.GamePlayer;
import com.lguhc.game.RoleType;
import com.lguhc.roles.Role;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Analyste (Village, Aura Lumineuse au départ).
 *
 * Dès {@link #MINUTES_AVANT_DISPONIBLE} minutes de jeu réel, {@code /lg observer <joueur>}
 * (voir LGCommand) révèle si la cible possède au moins un effet parmi {@link #EFFETS_DETECTES}
 * - {@link #UTILISATIONS_OBSERVATION_MAX} fois par partie, {@link #COOLDOWN_OBSERVATION_MINUTES}
 * minutes de cooldown entre deux observations. Chaque observation mémorise en interne (voir les
 * clés d'état ci-dessous) les effets précis trouvés sur la cible à cet instant.
 *
 * {@code /lg analyser <joueur>} (1 seule fois par partie, uniquement sur un joueur déjà observé
 * au moins une fois) révèle alors précisément le(s) effet(s) que ce joueur possédait au moment de
 * cette observation initiale - pas son état actuel. Le joueur analysé en est informé, et apprend
 * l'identité de l'Analyste s'il n'est pas du Village.
 *
 * Dans les deux cas (observer OU analyser), l'Aura de l'Analyste bascule sur Obscure - voir
 * GamePlayer#setAura dans LGCommand, RoleType#getAura() garde lui la valeur de départ (Lumineuse).
 */
public class AnalysteRole implements Role {

    /** Le pouvoir d'observation n'est utilisable qu'à partir de ce nombre de minutes de jeu réel (voir GameManager#getTempsTotalEcouleSecondes). */
    public static final int MINUTES_AVANT_DISPONIBLE = 50;

    /** Nombre maximum d'observations pour toute la partie. */
    public static final int UTILISATIONS_OBSERVATION_MAX = 5;

    /** Cooldown entre deux observations, en minutes. */
    public static final int COOLDOWN_OBSERVATION_MINUTES = 5;

    /** Nombre maximum d'analyses pour toute la partie (1 seule, sur un joueur déjà observé). */
    public static final int UTILISATIONS_ANALYSE_MAX = 1;

    /** Effets recherchés par /lg observer et précisés par /lg analyser, dans l'ordre d'affichage. */
    public static final List<PotionEffectType> EFFETS_DETECTES = Collections.unmodifiableList(Arrays.asList(
            PotionEffectType.INCREASE_DAMAGE,   // Force
            PotionEffectType.DAMAGE_RESISTANCE, // Résistance
            PotionEffectType.WEAKNESS,          // Faiblesse
            PotionEffectType.SPEED,             // Vitesse
            PotionEffectType.INVISIBILITY,      // Invisibilité
            PotionEffectType.ABSORPTION         // Absorption
    ));

    /** Noms affichés en français des effets ci-dessus (Bukkit ne fournit que les noms techniques anglais). */
    private static final Map<PotionEffectType, String> NOMS_FRANCAIS = construireNomsFrancais();

    private static Map<PotionEffectType, String> construireNomsFrancais() {
        Map<PotionEffectType, String> noms = new LinkedHashMap<>();
        noms.put(PotionEffectType.INCREASE_DAMAGE, "Force");
        noms.put(PotionEffectType.DAMAGE_RESISTANCE, "Résistance");
        noms.put(PotionEffectType.WEAKNESS, "Faiblesse");
        noms.put(PotionEffectType.SPEED, "Vitesse");
        noms.put(PotionEffectType.INVISIBILITY, "Invisibilité");
        noms.put(PotionEffectType.ABSORPTION, "Absorption");
        return Collections.unmodifiableMap(noms);
    }

    /** Nom français d'un effet parmi EFFETS_DETECTES (retombe sur le nom technique Bukkit pour tout effet hors liste). */
    public static String nomFrancais(PotionEffectType type) {
        String nom = NOMS_FRANCAIS.get(type);
        return nom != null ? nom : type.getName();
    }

    // ================= Clés d'état (GamePlayer#getEtat/setEtat) =================
    // Regroupées ici comme constantes plutôt qu'en chaînes éparpillées dans LGCommand, pour éviter
    // tout risque de typo entre lecture et écriture (voir RenardRole.UTILISATIONS_MAX etc. pour le
    // même principe côté constantes numériques).

    /** Nombre d'observations déjà utilisées (int, 0 par défaut). */
    public static final String CLE_OBSERVATIONS_UTILISEES = "analyste_observations_utilisees";

    /** Horodatage réel (System.currentTimeMillis) de la dernière observation, pour le cooldown (long, 0 par défaut). */
    public static final String CLE_DERNIERE_OBSERVATION_TS = "analyste_derniere_observation_ts";

    /**
     * Préfixe de clé dynamique : une entrée par cible observée, suffixée par l'UUID de la cible
     * (voir LGCommand#analysteObserver/analysteAnalyser). Stocke la liste des PotionEffectType
     * détectés sur cette cible au moment de l'observation - liste vide mais non nulle si aucun
     * effet trouvé, ce "non null" servant justement de marqueur "cette cible a bien été observée"
     * pour valider /lg analyser.
     */
    public static final String CLE_EFFETS_PREFIXE = "analyste_effets_";

    /** Vrai si l'unique analyse de la partie a déjà été consommée (boolean, false par défaut). */
    public static final String CLE_ANALYSE_UTILISEE = "analyste_analyse_utilisee";

    @Override
    public RoleType getType() {
        return RoleType.ANALYSTE;
    }

    @Override
    public void onAssign(LGUHCPlugin plugin, GamePlayer gp) {
        // Rôle 100% informatif, comme la Voyante : aucun équipement particulier à distribuer.
        // L'Aura de départ (Lumineuse) est déjà posée par GameManager#assignerRoleA via
        // RoleType#getAura() ; elle ne bascule en Obscure qu'au premier /lg observer ou
        // /lg analyser (voir LGCommand), pas ici.
    }
}
