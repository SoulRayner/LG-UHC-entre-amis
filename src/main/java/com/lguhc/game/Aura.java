package com.lguhc.game;

/**
 * L'Aura est ce que "voient" les pouvoirs de détection passifs (Montreur
 * d'Ours, Renard, Druide) : le Village/Hybrides ont une Aura Lumineuse, la
 * plupart des Loups une Aura Obscure - mais certains rôles (ex : Loup-Garou
 * Mystique, Assassin) ont une Aura qui ne correspond pas à leur vrai camp,
 * ce qui les rend indétectables par ces pouvoirs.
 */
public enum Aura {
    LUMINEUSE("Lumineuse", "§e"),
    NEUTRE("Neutre", "§7"),
    OBSCURE("Obscure", "§1");

    private final String nomAffiche;
    private final String couleur;

    Aura(String nomAffiche, String couleur) {
        this.nomAffiche = nomAffiche;
        this.couleur = couleur;
    }

    public String getNomAffiche() {
        return nomAffiche;
    }

    public String getCouleur() {
        return couleur;
    }

    /** Nom de l'aura coloré, prêt à afficher (ex : "§eLumineuse"). */
    public String getNomFormate() {
        return couleur + nomAffiche;
    }
}
