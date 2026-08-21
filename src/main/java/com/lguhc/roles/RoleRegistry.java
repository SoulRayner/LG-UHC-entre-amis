package com.lguhc.roles;

import com.lguhc.game.RoleType;
import com.lguhc.roles.hybrides.CupidonRole;
import com.lguhc.roles.hybrides.EnfantSauvageRole;
import com.lguhc.roles.loups.InfectPereDesLoupsRole;
import com.lguhc.roles.loups.LoupGarouBlancRole;
import com.lguhc.roles.loups.LoupGarouRole;
import com.lguhc.roles.loups.LoupMystiqueRole;
import com.lguhc.roles.loups.LoupPerfideRole;
import com.lguhc.roles.solitaires.AssassinRole;
import com.lguhc.roles.villageois.AncienRole;
import com.lguhc.roles.villageois.ChasseurRole;
import com.lguhc.roles.villageois.DruideRole;
import com.lguhc.roles.villageois.IdiotDuVillageRole;
import com.lguhc.roles.villageois.MontreurDOursRole;
import com.lguhc.roles.villageois.PetiteFilleRole;
import com.lguhc.roles.villageois.RenardRole;
import com.lguhc.roles.villageois.SoeursRole;
import com.lguhc.roles.villageois.SorciereRole;
import com.lguhc.roles.villageois.VoyanteRole;

import java.util.EnumMap;
import java.util.Map;

public class RoleRegistry {

    private final Map<RoleType, Role> roles = new EnumMap<>(RoleType.class);

    public RoleRegistry() {
        enregistrer(new VoyanteRole());
        enregistrer(new MontreurDOursRole());
        enregistrer(new RenardRole());
        enregistrer(new DruideRole());
        enregistrer(new PetiteFilleRole());
        enregistrer(new SorciereRole());
        enregistrer(new ChasseurRole());
        enregistrer(new IdiotDuVillageRole());
        enregistrer(new AncienRole());
        enregistrer(new SoeursRole());

        enregistrer(new LoupGarouRole());
        enregistrer(new InfectPereDesLoupsRole());
        enregistrer(new LoupGarouBlancRole());
        enregistrer(new LoupPerfideRole());
        enregistrer(new LoupMystiqueRole());

        enregistrer(new CupidonRole());
        enregistrer(new EnfantSauvageRole());

        enregistrer(new AssassinRole());
    }

    private void enregistrer(Role role) {
        roles.put(role.getType(), role);
    }

    public Role get(RoleType type) {
        return roles.get(type);
    }
}
