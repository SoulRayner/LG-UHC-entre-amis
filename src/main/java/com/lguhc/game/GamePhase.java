package com.lguhc.game;

public enum GamePhase {
    LOBBY,          // en attente de joueurs / du lancement par l'hôte
    EPISODE_1,      // préparation : récolte, pas de rôles, pas de PvP
    JOUR,           // phase de jour (cycle courant, épisode >= 2)
    NUIT,           // phase de nuit (cycle courant, épisode >= 2)
    TERMINEE        // partie finie, un camp a gagné
}
