package com.lguhc.game;

/**
 * Les camps possibles. Un joueur peut en changer en cours de partie
 * (ex : Enfant Sauvage qui se transforme, joueur infecté).
 */
public enum Camp {
    VILLAGE("Village", "§a"),
    LOUPS("Loups-Garous", "§c"),
    SOLO("Solitaire", "§6");

    private final String nomAffiche;
    private final String couleur;

    Camp(String nomAffiche, String couleur) {
        this.nomAffiche = nomAffiche;
        this.couleur = couleur;
    }

    public String getNomAffiche() {
        return nomAffiche;
    }

    public String getCouleur() {
        return couleur;
    }

    /** Nom du camp coloré, prêt à afficher (ex : "§aVillage"). */
    public String getNomFormate() {
        return couleur + nomAffiche;
    }
}
