package com.lguhc.game;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Données de jeu attachées à un joueur pendant une partie.
 * Le champ "etat" sert de stockage libre pour que chaque rôle
 * puisse y ranger ce dont il a besoin (compteurs, cooldowns...)
 * sans avoir à modifier cette classe à chaque nouveau rôle.
 */
public class GamePlayer {

    private final UUID uuid;
    private final String nom;
    private RoleType role;
    private Camp camp;
    private Camp campAffiche;
    private boolean vivant = true;
    private boolean droitDeVote = true;
    private UUID amoureux = null;
    private double corruption = 0.0;
    private UUID modele = null;
    private String dernierMot = null;
    private Aura aura = Aura.LUMINEUSE;
    private int groupe = 0;
    private boolean enAttenteMort = false;
    private UUID dernierTueur = null;
    private final Map<String, Object> etat = new HashMap<>();
    private final Map<UUID, org.bukkit.ChatColor> couleursPersonnalisees = new HashMap<>();

    public Map<UUID, org.bukkit.ChatColor> getCouleursPersonnalisees() {
        return couleursPersonnalisees;
    }

    public GamePlayer(UUID uuid, String nom) {
        this.uuid = uuid;
        this.nom = nom;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getNom() {
        return nom;
    }

    public Player getPlayer() {
        return Bukkit.getPlayer(uuid);
    }

    public RoleType getRole() {
        return role;
    }

    public void setRole(RoleType role) {
        this.role = role;
        if (role != null) {
            this.camp = role.getCampDepart();
            this.campAffiche = role.getCampDepart();
        }
    }

    public Camp getCamp() {
        return camp;
    }

    public void setCamp(Camp camp) {
        this.camp = camp;
        this.campAffiche = camp;
    }

    /** Change le vrai camp (utilisé pour les conditions de victoire, l'Aura, le chat de meute...) SANS toucher au camp affiché au scoreboard. */
    public void setCampSansAffichage(Camp camp) {
        this.camp = camp;
    }

    /** Camp montré sur le scoreboard - peut différer du vrai camp (ex : Enfant Sauvage transformé reste "Village" à l'affichage). */
    public Camp getCampAffiche() {
        return campAffiche != null ? campAffiche : camp;
    }

    public boolean isVivant() {
        return vivant;
    }

    public void setVivant(boolean vivant) {
        this.vivant = vivant;
    }

    public boolean aDroitDeVote() {
        return droitDeVote;
    }

    public void setDroitDeVote(boolean droitDeVote) {
        this.droitDeVote = droitDeVote;
    }

    public UUID getAmoureux() {
        return amoureux;
    }

    public void setAmoureux(UUID amoureux) {
        this.amoureux = amoureux;
    }

    public boolean estEnCouple() {
        return amoureux != null;
    }

    public double getCorruption() {
        return corruption;
    }

    public void setCorruption(double corruption) {
        this.corruption = Math.max(0.0, Math.min(100.0, corruption));
    }

    public void ajouterCorruption(double montant) {
        setCorruption(this.corruption + montant);
    }

    public boolean estCorrompu() {
        return corruption >= 100.0;
    }

    public UUID getModele() {
        return modele;
    }

    public void setModele(UUID modele) {
        this.modele = modele;
    }

    public String getDernierMot() {
        return dernierMot;
    }

    public void setDernierMot(String dernierMot) {
        this.dernierMot = dernierMot;
    }

    public Aura getAura() {
        return aura;
    }

    public void setAura(Aura aura) {
        this.aura = aura;
    }

    public int getGroupe() {
        return groupe;
    }

    public void setGroupe(int groupe) {
        this.groupe = groupe;
    }

    public boolean isEnAttenteMort() {
        return enAttenteMort;
    }

    public void setEnAttenteMort(boolean enAttenteMort) {
        this.enAttenteMort = enAttenteMort;
    }

    public UUID getDernierTueur() {
        return dernierTueur;
    }

    public void setDernierTueur(UUID dernierTueur) {
        this.dernierTueur = dernierTueur;
    }

    @SuppressWarnings("unchecked")
    public <T> T getEtat(String cle, T valeurParDefaut) {
        Object v = etat.get(cle);
        if (v == null) {
            return valeurParDefaut;
        }
        return (T) v;
    }

    public void setEtat(String cle, Object valeur) {
        etat.put(cle, valeur);
    }

    public int incrementerEtat(String cle) {
        int v = getEtat(cle, 0) + 1;
        setEtat(cle, v);
        return v;
    }
}
