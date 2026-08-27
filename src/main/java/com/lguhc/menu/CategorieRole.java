package com.lguhc.menu;

import com.lguhc.game.Camp;
import com.lguhc.game.RoleType;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * Les 4 sous-catégories affichées dans l'onglet Compo du menu /lg config. Regroupement purement
 * visuel dérivé de RoleType (camp de départ + flag "hybride") : aucune donnée propre, donc pas de
 * liste à tenir à jour en double si un rôle change de camp plus tard.
 *
 * Ordre volontaire : Village, Loups, Hybrides, Solitaire (même ordre que dans la demande).
 */
public enum CategorieRole {

    VILLAGE("§aVillage", Material.WOOL, (short) 5),
    LOUPS("§cLoups-Garous", Material.WOOL, (short) 14),
    HYBRIDE("§5Hybrides", Material.WOOL, (short) 10),
    SOLITAIRE("§6Solitaires", Material.WOOL, (short) 1);

    private final String nomAffiche;
    private final Material materielIcone;
    private final short donneeIcone;

    CategorieRole(String nomAffiche, Material materielIcone, short donneeIcone) {
        this.nomAffiche = nomAffiche;
        this.materielIcone = materielIcone;
        this.donneeIcone = donneeIcone;
    }

    public String getNomAffiche() {
        return nomAffiche;
    }

    public Material getMaterielIcone() {
        return materielIcone;
    }

    public short getDonneeIcone() {
        return donneeIcone;
    }

    /** Vrai si `type` fait partie de cette catégorie (voir la note de classe : dérivé de RoleType, rien à maintenir ici). */
    public boolean correspond(RoleType type) {
        switch (this) {
            case VILLAGE:
                return type.getCampDepart() == Camp.VILLAGE && !type.estHybride();
            case LOUPS:
                return type.getCampDepart() == Camp.LOUPS;
            case HYBRIDE:
                return type.estHybride();
            case SOLITAIRE:
                return type.getCampDepart() == Camp.SOLO;
            default:
                return false;
        }
    }

    /** Rôles de cette catégorie, dans l'ordre naturel de l'enum RoleType (stable d'un appel à l'autre : indispensable pour que la pagination reste cohérente entre l'affichage et le clic). */
    public List<RoleType> getRoles() {
        List<RoleType> liste = new ArrayList<>();
        for (RoleType type : RoleType.values()) {
            if (correspond(type)) {
                liste.add(type);
            }
        }
        return liste;
    }
}
