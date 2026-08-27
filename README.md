# LGUHC — Loup-Garou UHC (Minecraft 1.8.8)

Plugin **original** et autonome (développé de zéro) combinant la survie UHC et les mécaniques du jeu Loup-Garou de Thiercelieux.

- **Développement :** 100 % custom (aucun code réutilisé).
- **Rôles :** 18 rôles uniques implémentés.
- **Compositions :** 5 configurations pré-définies (18 à 22 joueurs).

---

> **⚠️ Note de compilation**  
> Si `mvn package` échoue avec une erreur de dépendance ou d'import manquant, copiez le message d'erreur exact dans vos *issues* pour correction.

---

## 1. Compilation

### Prérequis
* **Java :** 8 à 21
* **Outils :** Maven & Git

### Étapes de build

```bash
# 1. Récupérer et compiler l'API Spigot 1.8.8 (installe spigot-api dans le dépôt local ~/.m2)
git clone [https://github.com/SpigotMC/BuildTools.git](https://github.com/SpigotMC/BuildTools.git)
cd BuildTools
java -jar BuildTools.jar --rev 1.8.8

# 2. Compiler LGUHC
cd /chemin/vers/lguhc
mvn clean package
```
*(Le fichier exécutable `.jar` est généré dans le dossier `target/LGUHC.jar`)*

---

## 2. Installation

1. Copiez `target/LGUHC.jar` dans le dossier `plugins/` de votre serveur **Spigot/Paper 1.8.8**.
2. Démarrez le serveur une première fois pour générer `plugins/LGUHC/config.yml`.
3. Vérifiez et modifiez l'option `monde.nom` dans `config.yml` pour qu'elle corresponde au nom de votre monde.
4. *(Optionnel)* Si vous acceptez les versions non-officielles, passez `online-mode: false` dans `server.properties`.

---

## 3. Commandes & Utilisation

### Commandes Joueurs

| Commande | Action |
| :--- | :--- |
| `/lg join` | Rejoindre la partie en attente |
| `/lg leave` | Quitter la file d'attente |
| `/lg start` | *(Hôte)* Lancer la partie |
| `/lg stop` | *(Hôte)* Réinitialiser et arrêter la partie |
| `/lg role` | Consulter la description de son rôle |
| `/lg vote <joueur\|blanc>` | Voter pendant une réunion du village |
| `/lg groupe <message>` | Envoyer un message à son groupe dynamique |
| `/lg color <joueur>` | Ouvrir le menu GUI pour attribuer une couleur à un joueur |
| `/lw <message>` | Définir son dernier mot (affiché publiquement à la mort) |

> *Les commandes spécifiques à chaque rôle (ex : `/lg voir`, `/lg loup`, `/lg love`...) sont annoncées en message privé lors de l'attribution ou consultables dans `LGCommand.java`.*

### Commandes d'Administration

> **Permission requise :** `lguhc.host` *(op par défaut)*

| Commande | Action |
| :--- | :--- |
| `/lg admin skip` | Forcer le passage à la phase suivante (Jour/Nuit/Prépa) |
| `/lg admin start` | Forcer le lancement sans attendre le minimum de 4 joueurs |
| `/lg admin role <joueur> <ROLE>` | Forcer l'attribution d'un rôle précis |
| `/lg admin roles` | Lister tous les identifiants de rôles valides |
| `/lg admin kill <joueur>` | Éliminer instantanément un joueur |
| `/lg admin revive <joueur>` | Ressusciter un joueur |
| `/lg admin tp <joueur>` | Téléporter l'administrateur vers un joueur |
| `/lg admin border <taille>` | Redimensionner la bordure instantanément |
| `/lg admin regen` | Déclencher la régénération du monde de jeu |

---

## 4. Compositions & Rôles

Les 5 compositions (18 à 22 joueurs) sont paramétrables dans `src/main/resources/config.yml` sous la section `compositions`.

Pour ajouter ou modifier un palier :
1. Ouvrez `config.yml`.
2. Dupliquez un bloc existant en modifiant le nombre de joueurs.
3. Rechargez la configuration ou redémarrez le serveur (aucune recompilation requise).

---

## 5. Monde Lobby & Régénération

### Principe
Un monde statique (`world_lobby`) accueille les joueurs en attente. Le monde de jeu (`monde.nom`) est remplacé par une copie vierge à partir du dossier `world_template/` avant chaque partie. La suppression du fichier `level.dat` force la génération d'une nouvelle **seed** (distribution des minerais, lacs et structures), tout en conservant le relief peint sous WorldPainter.

### Configuration

1. Exportez votre carte WorldPainter avec l'option *« Let Minecraft populate the map »* activée.
2. Déposez le dossier exporté à la racine du serveur sous le nom `world_template/`.
3. Ajustez les variables suivantes dans `config.yml` :
   * `monde.nom` : Nom du monde de jeu (ex : `world_lguhc`).
   * `monde.lobby` : Nom du monde d'attente (ex : `world_lobby`).
4. Exécutez `/lg admin regen` pour valider le processus.

---

## 6. Journal des Modifications (Patch Notes)

### Correctifs majeurs
* **Bordure & Apparition :** Calcul dynamique du rayon de téléportation pour éviter tout apparition hors de la bordure.
* **Équilibrage des tests :** Conservation obligatoire d'au moins un Loup-Garou lors des sessions réduites (3 à 4 joueurs).
* **Loup-Garou Blanc :** Application effective du soin maximal à 15♥ (`setMaxHealth`).

### Cycle Jour / Nuit
* Structure à partir de l'épisode 2 : 4 phases par épisode (Jour 1, Nuit 1, Jour 2, Nuit 2).
* Transition lumineuse du temps recalculée chaque seconde.

### Mécaniques de Jeu & Équilibrage
* **Suppression du vote de meute :** Les Loups-Garous éliminent leurs cibles en combat direct.
* **Vote du Village :** 1 vote par épisode à partir de l'épisode 3 (3 votes requis minimum). Sanction : effet Poison I (13s) et diffusion du pseudo avec 4 rôles tirés au sort (dont le vrai).
* **Aura :** Suppression du système d'Honneur. L'Aura affiche 3 états : *Lumineuse* (Village), *Obscure* (Loups) ou *Neutre* (Assassin).
* **Enfant Sauvage :** Conserve l'affichage "Village" sur le scoreboard après sa transformation.
* **Consommables :** La Pomme en Or restaure 3♥ fixes (suppression de l'absorption permanente).
* **Équipement :** Les rôles à équipement (Cupidon, Chasseur, Assassin) reçoivent des livres enchantés à appliquer eux-mêmes.

---

## 7. Architecture du Projet

```text
com.lguhc
├── LGUHCPlugin.java       # Point d'entrée du plugin
├── game/                  # Moteur de jeu (phases, votes, bordure, couples...)
├── roles/                 # Implémentation des 18 rôles
├── commands/              # Gestionnaires des commandes /lg et /lw
├── listeners/             # Événements Bukkit et règles UHC
└── util/                  # Utilitaires (ItemBuilder, messages)
```
