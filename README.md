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
