# LGUHC — Loup-Garou UHC pour Minecraft 1.8

Plugin **original**, écrit de zéro, inspiré de l'esprit "Loup-Garou UHC"
(survie UHC + Loup-Garou façon Thiercelieux) : ce n'est pas une copie du
code ni des textes d'un serveur existant. Les 18 rôles demandés, leurs
mécaniques et les 5 compositions (18 à 22 joueurs) sont implémentés.

## ⚠️ A savoir avant de commencer

Je n'ai **pas pu compiler ce projet dans mon environnement** (pas d'accès
réseau vers les dépôts Maven/Spigot depuis le bac à sable où je travaille).
Le code a été écrit à la main avec le plus grand soin, mais il est possible
qu'il reste 1 ou 2 erreurs de compilation mineures (import manquant, typo).
**Compilez-le en premier, et si `mvn package` affiche une erreur, copiez-moi
le message exact** : je corrige immédiatement.

## 1. Compiler le plugin

Pré-requis : Java 8+ (Java 21 fonctionne aussi pour compiler), Maven.

```bash
# 1. Récupérer l'API Spigot 1.8.8 (obligatoire, ça construit le serveur
#    lui-même à partir des sources Mojang + Spigot, donc ça prend un moment)
git clone https://github.com/SpigotMC/BuildTools.git
cd BuildTools
java -jar BuildTools.jar --rev 1.8.8
# → installe spigot-api 1.8.8-R0.1-SNAPSHOT dans votre dépôt Maven local (~/.m2)

# 2. Compiler LGUHC
cd /chemin/vers/lguhc
mvn clean package
# → le jar apparaît dans target/LGUHC.jar
```

## 2. Installer

1. Copiez `target/LGUHC.jar` dans le dossier `plugins/` de votre serveur
   Spigot/Paper **1.8.8**.
2. Démarrez le serveur une fois pour qu'il génère `plugins/LGUHC/config.yml`.
3. Vérifiez `monde.nom` dans ce config.yml (doit correspondre à votre monde).
4. Si un de vos amis joue en version crackée, pensez à mettre
   `online-mode: false` dans `server.properties` (à faire de votre côté,
   ça ne dépend pas du plugin).

## 3. Jouer

| Commande | Effet |
|---|---|
| `/lg join` | rejoindre la partie en attente |
| `/lg leave` | quitter (avant le lancement) |
| `/lg start` | (hôte) lance la partie |
| `/lg stop` | (hôte) réinitialise tout |
| `/lg role` | revoir son rôle |
| `/lg vote <joueur\|blanc>` | voter pendant une réunion du village |
| `/lg groupe <message>` | parler uniquement à son groupe dynamique |
| `/lw <message>` | dernier mot affiché à votre mort |

Chaque rôle a sa propre commande, annoncée en message privé au moment de
l'attribution des rôles (ex : `/lg voir`, `/lg loup`, `/lg love`...). Liste
complète dans `LGCommand.java`.

## 3bis. Commandes admin pour tester sans attendre

Toutes réservées à la permission `lguhc.host` (op par défaut) :

| Commande | Effet |
|---|---|
| `/lg admin skip` | termine la phase actuelle immédiatement (prépa → rôles, jour → nuit, nuit → jour suivant) |
| `/lg admin start` | lance la partie en ignorant le minimum de 4 joueurs (pour tester seul ou à 2-3) |
| `/lg admin role <joueur> <ROLE>` | force un rôle précis sans attendre le tirage aléatoire |
| `/lg admin roles` | liste tous les identifiants de rôles valides (ex : `LOUP_PERFIDE`) |
| `/lg admin kill <joueur>` | élimine instantanément (teste Chasseur, Couple, Infect Père...) |
| `/lg admin revive <joueur>` | ressuscite un joueur pour enchaîner les tests |
| `/lg admin honneur <joueur> <0-100>` | règle l'Honneur d'un coup (teste le rayon du Druide) |
| `/lg admin tp <joueur>` | vous téléporte vers ce joueur |
| `/lg admin border <taille>` | redimensionne la bordure instantanément |

Exemple de session de test rapide en solo : `/lg join`, `/lg admin start`,
`/lg admin skip` (attribue les rôles), `/lg admin role VotrePseudo DRUIDE`,
`/lg admin honneur VotrePseudo 100`, puis testez le pouvoir en jeu.

## 4. Composition des rôles

Les 5 compositions (18 à 22 joueurs) que vous avez fournies sont dans
`src/main/resources/config.yml`, section `compositions`. Pour ajouter un
palier (ex : 23 joueurs), copiez-collez un bloc existant en changeant les
nombres — aucune recompilation n'est nécessaire, juste relancer le serveur
(ou une commande `/reload` si vous en avez l'habitude, même si ce n'est
jamais recommandé sur un vrai serveur Bukkit).

## 5. Session de correctifs post-test (dernière grosse mise à jour)

Après ton premier vrai test, deux bugs critiques + une longue liste de
retouches. Dans l'ordre :

**🔴 Bugs critiques corrigés**
- **Spawn hors bordure** : le rayon de téléportation de départ (900) était
  quasiment égal à la bordure (1000), donc une partie des joueurs
  spawnaient hors bordure et mouraient direct. Le rayon est maintenant
  calculé automatiquement pour toujours rester bien en dessous de la
  bordure réelle.
- **Fin de partie au 1er mort en petit test** : pour les tests à 3-4
  joueurs, la composition piochait dans le tableau des 18 joueurs et
  supprimait les Loups-Garous en premier pour ajuster le nombre - un test
  à 3 pouvait donc se retrouver 100% Village, d'où une victoire absurde
  dès le premier mort. Le tirage protège maintenant toujours au moins un
  Loup-Garou.
- **Loup-Garou Blanc à 10♥** : ses 15♥ n'avaient en fait jamais été codés
  (oubli de ma part). C'est fait, `setMaxHealth` appliqué à l'attribution.

**Jour/nuit et épisodes**
- Chaque épisode (à partir du 2) = 4 périodes (Jour, Nuit, Jour, Nuit),
  le numéro d'épisode n'avance qu'après les 2 nuits.
- L'heure du monde suit maintenant vraiment la phase (soleil le jour,
  nuit visible la nuit), avec une transition progressive recalculée
  chaque seconde plutôt qu'un saut brutal.

**Loups & votes : plus aucune mise à mort par commande**
- Retiré le vote de meute (`/lg loup`) : les Loups tuent maintenant
  uniquement en combat direct, comme demandé.
- Vote du village entièrement refait : 1 vote par épisode à partir du
  3ème, chacun ne vote qu'une fois, il faut 3 votes minimum sur quelqu'un
  pour qu'il soit sanctionné (en cas d'égalité à 3+, tirage au sort parmi
  les ex-aequo), résultat après 2 minutes. Sanction : Poison I 13
  secondes + pseudo annoncé publiquement avec 4 rôles tirés au sort parmi
  les vivants (le sien y figure toujours).

**Honneur retiré, Aura conservée et affichée**
- Le système d'Honneur est complètement supprimé (plus aucune trace, y
  compris les effets qui en dépendaient, désormais inconditionnels).
- L'Aura reste et prend ses vraies 3 valeurs : **Lumineuse** (Village),
  **Obscure** (Loups), **Neutre** (Assassin). Annoncée automatiquement à
  l'attribution du rôle et visible avec `/lg role`.

**Enfant Sauvage** : transformé en Loup-Garou, il reste maintenant affiché
comme "Village" sur le scoreboard de tout le monde (le vrai camp - utilisé
pour la victoire, le chat de meute, l'Aura - change bien en interne, seul
l'affichage reste trompeur, comme demandé).

**Objets de rôle en livres** : Cupidon, Chasseur et Assassin reçoivent
maintenant leur équipement de base SANS enchant, plus un livre enchanté à
appliquer eux-mêmes (Punch I, Power III, Tranchant/Protection/Puissance
III) plutôt qu'un objet déjà tout équipé.

**Pomme en or** : soigne maintenant 3♥ pile (au lieu de l'effet
Régénération imprécis qui ne rendait qu'1♥ dans les faits). Absorption
permanente entièrement retirée.

**Nouveau : `/lg color <joueur>`** : ouvre un menu (clic sur une laine
colorée) pour donner à un joueur une couleur de pseudo **visible pour vous
seul** (nom au-dessus de la tête + tab-list), via des équipes de
scoreboard personnelles - 16 couleurs au choix, plus un item pour
réinitialiser. Idéal pour marquer visuellement "je pense que c'est un
Loup" sans que personne d'autre ne le voie.

**Nouveau sur le scoreboard** : taille actuelle de la bordure, et
direction + distance approximative jusqu'à 0,0.

## 6. Ce qui reste volontairement approximé

- **Génération / biomes** : un vrai générateur de monde sur-mesure est
  hors de portée sûre pour moi ici (je ne peux pas tester le rendu). Le
  plugin ne cherche plus de biome particulier près de 0,0 : le centre de
  partie (téléportation de départ + centre de la bordure) est maintenant
  simplement le spawn du monde tel que défini côté serveur (via
  `/setworldspawn` ou directement par ta carte pré-générée), ce qui colle
  à une carte custom où 0,0 ne correspond pas forcément au centre voulu.
  Si tu utilises un mod/plugin dédié à la génération de carte (biome
  imposé, carte pré-générée, seed particulier), assure-toi juste que le
  spawn du monde est bien positionné où tu veux voir la partie démarrer -
  aucun souci à cumuler les deux plugins.
- **Effets "0.5"** (mi-niveau) : toujours approximés par 50% de chance
  d'avoir l'effet complet, faute de pouvoir faire du vrai mi-niveau avec
  les potions vanilla sans un système de dégâts custom plus risqué à
  écrire à l'aveugle.
- **Particules Petite Fille ↔ Loup Perfide** : toujours pas faites (détail
  cosmétique).

Le mod de chat de proximité que tu comptes ajouter n'entre pas en conflit
avec ce plugin (systèmes indépendants). Si tu veux qu'un rôle interagisse
avec plus tard, dis-moi lequel tu utilises et on regardera.

## 7. Monde lobby + régénération du monde de jeu

**Principe** : un monde `world_lobby` statique (jamais touché) où les joueurs attendent,
et le monde de jeu (`monde.nom`, votre carte WorldPainter) qui est entièrement remplacé
par une copie fraîche avant chaque partie, avec une nouvelle seed pour que minerais/lacs/
structures soient différents à chaque fois — le relief et les biomes peints, eux, ne
changent jamais.

**Mise en place (une seule fois)** :
1. Exportez votre carte WorldPainter normalement (celle que vous utilisez déjà), avec
   l'option *"Let Minecraft populate the map"* activée (c'est ce que vous avez fait :
   ores/lacs/grottes laissés à Minecraft). C'est ce qui permet le reseed — si tout est
   déjà pré-peuplé par WorldPainter, ce dossier repartira identique à chaque régénération.
2. Copiez ce dossier exporté à la racine du serveur (à côté de `world/`, `plugins/`...)
   sous le nom `world_template/` (ou le nom donné à `regeneration-monde.dossier-modele`
   dans `config.yml`). **Ce dossier ne doit jamais être ouvert par le serveur** (ne le
   mettez pas en `level-name` et ne faites pas `/lg admin regen` en le pointant sur
   lui-même) : c'est la copie vierge dans laquelle on repioche à chaque partie.
3. Réglez `monde.nom` sur le nom que doit porter le monde de jeu vivant (peut être
   différent de `world_template`, ex: `world_lguhc`).
4. `monde.lobby` (par défaut `world_lobby`) sera créé automatiquement au premier
   démarrage du plugin s'il n'existe pas déjà (un monde vanilla tout simple ; construisez-y
   une petite salle d'attente si vous voulez).
5. Démarrez le serveur, puis lancez `/lg admin regen` une première fois à la main pour
   vérifier que tout se passe bien (voir le point 6 ci-dessous) avant de laisser
   l'automatique s'en charger.

**Fonctionnement en jeu** :
- A la connexion, tout joueur qui n'est pas déjà inscrit à une partie en cours est
  téléporté dans `world_lobby`.
- `/lg stop` renvoie tout le monde au lobby, puis déclenche automatiquement une
  régénération du monde de jeu en tâche de fond (config `auto-apres-reset: true`).
- `/lg admin regen` régénère à la demande (refusé pendant une partie en cours).
- `/lg start` / `/lg admin start` refusent de démarrer tant qu'une régénération est en
  cours (message clair au lieu de planter).
- La copie + suppression de fichiers se fait hors du thread principal (pas de freeze du
  serveur), seuls le déchargement/rechargement du monde repassent dessus (obligatoire
  côté Bukkit) — ça prendra quelques secondes à quelques dizaines de secondes selon la
  taille de votre carte et la vitesse du disque.

**⚠️ A vérifier vous-même, je n'ai pas pu tester dans mon environnement** : le principe
(supprimer `level.dat` pour forcer une nouvelle seed au rechargement, sans toucher aux
fichiers de région) est une technique connue sur les serveurs à carte custom, mais
vérifiez sur votre build exact que (a) le relief après `/lg admin regen` correspond bien
à votre carte peinte, et (b) qu'une zone non peinte par vous (minerais/lacs) diffère
bien d'une régénération à l'autre. Si l'un des deux ne se comporte pas comme prévu,
dites-le moi avec ce que vous observez, je corrige.

**Ce qui n'est PAS régénéré par cette technique** : tout ce que WorldPainter a
lui-même gravé dans l'export (par exemple votre réglage "Cave everywhere" si c'est un
outil appliqué côté WorldPainter plutôt que laissé à Minecraft) fait partie du terrain
figé et restera identique à chaque partie, contrairement au peuplement vanilla (ores,
lacs, structures) qui lui se retire à chaque régénération.

## 7ter. `/lg config` — menu de configuration avant `/lg start`

Réservé à `lguhc.host`. Ouvre un menu à onglets (blocs cliquables) pour régler la partie avant de
la lancer, sans passer par `config.yml` à la main :

| Onglet | Bloc | Effet |
|---|---|---|
| **Compo** | Bibliothèque | 4 sous-catégories (Village / Loups-Garous / Hybrides / Solitaire) listant chaque rôle : clic pour l'activer/désactiver dans le tirage **automatique** (celui basé sur `joueurs-par-loup`). Le Loup-Garou de base reste toujours actif (rôle de secours). Liste paginée (45 rôles/page) pour rester utilisable même quand plus de rôles seront ajoutés plus tard. N'affecte PAS les compositions personnalisées de la section `compositions` (listes exactes par nombre de joueurs) : celles-ci restent à éditer dans `config.yml` comme avant. |
| **Événement aléatoire** | Étoile du Nether | 3 toggles **indépendants** (Exposed / Exposed Inversé / Rumeurs - activer l'un n'active pas les autres), et règle leurs fenêtres de déclenchement (minutes de jeu réel). Voir section 8 ci-dessous. |
| **Règle** | Enclume | Limites d'enchantement (Tranchant général + Tranchant Solitaire, Protection fer/diamant, Puissance arc), limite de diamants minés, limite d'objets en diamant craftables, et les 4 minuteries de partie (annonce des rôles, Final Heal, liste des alliés Loups, début du resserrement de bordure). Clic gauche = augmenter, clic droit = diminuer. |
| **Map** | Carte | Taille de la bordure au lancement (pas de 250, défaut 1000). |
| **WIP #1 / WIP #2** | Barrière / Toile d'araignée | Réservés pour plus tard, ne font rien pour l'instant. |

Chaque changement est appliqué et sauvegardé dans `config.yml` immédiatement (pas besoin de
`/reload` ni de redémarrer). Les nouveaux réglages introduits pour cet écran (`survie-uhc.niveau-max-tranchant-solo`,
`survie-uhc.limite-stuff-diamant`, `survie-uhc.limite-diamants-mines`, `survie-uhc.final-heal-minutes`,
`survie-uhc.minutes-avant-liste-loups`) prennent la valeur par défaut indiquée dans le menu tant
qu'ils n'existent pas encore dans votre `config.yml`.

`BorderManager` gère aussi une taille minimale (`bordure.taille-minimale`, 200 par défaut) et une
vitesse de resserrement (`bordure.secondes-par-bloc`, 15 par défaut) : pas exposées dans le menu
Map car non demandées, mais ça se rajoute en une entrée dans `ConfigMenu.REGLAGES_REGLES` si vous
en avez besoin.

**Limite d'objets en diamant** : ne bloque aujourd'hui que le *craft* (table d'artisanat/inventaire).
Un objet en diamant obtenu autrement (butin d'un joueur mort, `/give` admin...) n'est pas concerné.
Si vous voyez un moyen de contourner la limite en jeu, dites-le moi et j'étendrai la vérification.

## 8. Événements aléatoires : Exposed / Exposed Inversé / Rumeurs

Les 3 sont désactivés par défaut, et s'activent **indépendamment** les uns des autres dans
`/lg config` > **Événement aléatoire** (activer Rumeurs seul, sans Exposed ni Exposed Inversé, est
par exemple tout à fait possible).

### Exposed / Exposed Inversé

Ces deux-là partagent les 2 mêmes fenêtres de déclenchement (temps de jeu réel depuis `/lg start`,
horaires tirés une seule fois au lancement) :

- Le **1er** entre 60 et 80 minutes par défaut.
- Le **2e** entre 100 et 120 minutes par défaut.

Les 4 bornes (min/max de chaque fenêtre) sont réglables directement dans l'onglet, sur le même
principe +/- que les autres réglages numériques du menu.

À chaque horaire, ce qui se passe dépend des toggles des deux :
- Si les deux sont actifs, un tirage au sort (50/50) détermine lequel survient — sauf si Exposed
  Inversé n'est pas jouable à ce moment-là (il faut au moins 5 joueurs vivants), auquel cas c'est
  toujours Exposed qui a lieu.
- Si un seul des deux est actif, c'est toujours lui qui joue à cet horaire.

**Exposed** : un joueur vivant tiré au sort voit son pseudo annoncé dans le chat général à côté de
4 rôles distincts - le sien (toujours présent), un rôle d'un camp différent du sien, et deux rôles
supplémentaires tirés au hasard. Au moins 2 des 4 rôles affichés sont des rôles du camp Village
(Cupidon/Enfant Sauvage inclus). Les rôles proposés sont piochés parmi ceux effectivement détenus
par des joueurs vivants (même principe que l'annonce de sanction du vote), pour rester cohérent
avec ce que les autres joueurs peuvent réellement déduire en jeu.

**Exposed Inversé** : 5 joueurs vivants tirés au sort sont tous affichés côte à côte dans le chat
général, à côté d'un seul et même rôle. Ce rôle est réellement détenu par l'un des 5 (l'unique
"vrai", les 4 autres servent de leurre) : contrairement à Exposed, l'info n'est donc jamais un pur
mensonge, juste noyée dans le bruit.

### Rumeurs

Contrairement aux deux ci-dessus, Rumeurs a sa **propre** fenêtre de déclenchement, indépendante,
tirée une seule fois entre 80 et 120 minutes par défaut (bornes réglables dans l'onglet, comme
Exposed/Exposed Inversé).

À l'horaire tiré, un message annonce à tous les joueurs qu'ils ont 20 secondes pour envoyer un
message dans le chat général. Chaque message envoyé pendant cette fenêtre n'apparaît PAS dans le
chat au moment où il est tapé (un seul message pris en compte par joueur) : une fois les 20
secondes écoulées, tous les messages reçus sont réaffichés d'un coup, anonymement et dans un ordre
mélangé - impossible de savoir qui a écrit quoi.

## 7bis. Architecture (si vous voulez modifier/ajouter des rôles)

```
com.lguhc
├── LGUHCPlugin        (point d'entrée)
├── game/               (moteur : phases, votes, mort différée, bordure, couple, honneur...)
├── roles/              (1 classe par rôle, regroupées villageois/loups/hybrides/solitaires)
├── commands/           (/lg et /lw)
├── listeners/          (règles UHC + pouvoirs déclenchés par événement)
├── menu/               (ConfigMenu, CategorieRole, ConfigMenuHolder — le menu /lg config)
└── util/               (ItemBuilder, messages)
```

Pour ajouter un rôle : créez une classe qui implémente `Role`, enregistrez-la
dans `RoleRegistry`, ajoutez sa valeur dans `RoleType`, et branchez son
pouvoir actif dans `LGCommand` si besoin.
#   L G - U H C - e n t r e - a m i s  
 