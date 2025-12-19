# 🌍 UbuPlugin

[![Version](https://img.shields.io/badge/version-2.0.0-blue.svg)](https://github.com/Tannoxx/UbuPlugin)
[![Minecraft](https://img.shields.io/badge/minecraft-1.21.10-green.svg)](https://papermc.io/)
[![Java](https://img.shields.io/badge/java-21-orange.svg)](https://adoptium.net/)
[![License](https://img.shields.io/badge/license-Proprietary-red.svg)](LICENSE)

Plugin professionnel complet pour serveur Minecraft **UbuEarth SMP**, développé avec Paper 1.21.10.

> **UbuPlugin** est une solution tout-en-un offrant un système d'enchantements customs avancé, un gestionnaire de ranks sophistiqué, des outils de géolocalisation Earth et bien plus encore.

---

## 📋 Table des matières

- [Présentation générale](#-présentation-générale)
- [Fonctionnalités](#-fonctionnalités)
   - [Module Enchantements](#-module-enchantements)
   - [Module Ranks](#-module-ranks)
   - [Module Earth Tools](#-module-earth-tools)
   - [Module Lobby Chat](#-module-lobby-chat)
   - [Module Anti-AFK](#-module-anti-afk)
- [Installation](#-installation)
- [Configuration](#-configuration)
- [Commandes & Permissions](#-commandes--permissions)
- [Base de données](#-base-de-données)
- [Support & Contact](#-support--contact)

---

## 🎯 Présentation générale

**UbuPlugin** est un plugin Minecraft entièrement développé en Java 21 pour Paper 1.21.4, conçu spécifiquement pour le serveur **UbuEarth SMP**. Il combine plusieurs systèmes essentiels en un seul plugin optimisé et performant.

### Caractéristiques principales

- ⚡ **Performances optimales** : Architecture thread-safe avec caches Caffeine et pool de connexions HikariCP
- 🗄️ **Base de données SQLite** : Stockage efficace avec migrations automatiques depuis YAML
- 🌐 **Multi-langue** : Support français et anglais avec détection automatique
- 🔧 **Modulaire** : Activation/désactivation indépendante de chaque module
- 🎨 **Modern API** : Utilisation de MiniMessage pour les messages formatés
- 📦 **Datapack requis** : Les enchantements customs nécessitent le datapack UbuPlugin

### Technologies utilisées

| Technologie | Version  | Usage |
|------------|----------|-------|
| Java | 21       | Langage principal |
| Paper API | 1.21.10  | API serveur |
| SQLite | 3.46.1.3 | Base de données |
| HikariCP | 5.1.0    | Pool de connexions |
| Caffeine | 3.1.8    | Système de cache |
| Gson | 2.11.0   | Traitement JSON |
| Adventure | (Paper)  | Messages formatés |

---

## 🎮 Fonctionnalités

### ⚔️ Module Enchantements

Système d'enchantements customs avancés avec 9 enchantements uniques.

#### 🪓 Timber (Bûcheron Fou)
- **Description** : Coupe automatiquement tous les troncs connectés d'un arbre
- **Niveaux** : 1
- **Cooldown** : Configurable (défaut: 1s)
- **Limite** : 150 blocs maximum
- **Compatibilité** : Magnetic (auto-ramassage)
- **Toggle** : `/toggle timber`

#### 🧲 Magnetic (Magnétique)
- **Description** : Récupère automatiquement les items minés dans l'inventaire
- **Niveaux** : 1
- **Portée** : Configurable
- **Compatibilité** : Fonctionne avec tous les outils
- **Toggle** : `/toggle magnetic`
- **Note** : Compatible avec Timber, Explosive et Veinminer

#### 📚 Experience (Bonus XP)
- **Description** : Augmente l'expérience gagnée
- **Niveaux** : 1-3
- **Bonus** : +25% par niveau
- **Application** : Blocs minés et mobs tués
- **Cumul** : Oui (avec tous les enchantements de minage)

#### 💥 Excavator (Minage 3x3)
- **Description** : Mine en 3x3 dans la direction regardée
- **Niveaux** : 1
- **Compatibilité** : Fortune, Silk Touch, Experience, Magnetic
- **Durabilité** : Consomme 1 durabilité par bloc (Unbreaking compatible)
- **XP** : Récupère l'XP de tous les blocs
- **Toggle** : `/toggle excavator`
- **Désactivation** : Maintenir Shift

#### ⛏️ Veinminer (Mineur de filons)
- **Description** : Mine automatiquement tous les minerais connectés d'un même type
- **Niveaux** : 1
- **Limite** : 150 blocs maximum
- **Cooldown** : Configurable (défaut: 1s)
- **Compatibilité** : Fortune, Silk Touch, Experience, Magnetic
- **Minerais supportés** : Tous les minerais vanilla (Overworld, Deepslate, Nether)
- **Toggle** : `/toggle veinminer`
- **Désactivation** : Maintenir Shift

#### 🚀 Dash (Propulsion)
- **Description** : Propulsion rapide avec double-sneak
- **Activation** : Appuyer 2 fois rapidement sur Shift

| Niveau | Vitesse | Cooldown | Spécial |
|--------|---------|----------|---------|
| 1 | 1.5 | 10s | - |
| 2 | 2.0 | 5s | - |
| 3 | 2.5 | 3s | **Invulnérable + Inarrêtable (1.5s)** |

#### 👻 Soulbound (Lié à l'âme)
- **Description** : Conserve les items après la mort
- **Niveaux** : 1
- **Retour** : Items rendus après 2 ticks (configurable)
- **Application** : Sur tous les items équipés/inventaire

#### 🔧 Auto-Repair (Réparation Auto)
- **Description** : Répare automatiquement la durabilité des items
- **Niveaux** : 1-3
- **Réparation** : 5 points de durabilité par intervalle (configurable)
- **Intervalle** : 10 secondes (configurable)
- **Zone** : Armure équipée + inventaire (optionnel)

#### 🔮 Beaconator (Extension Beacon)
- **Description** : Étend la portée des effets de beacon
- **Niveaux** : 1-4
- **Bonus portée** : +20 blocs par niveau
- **Niveau 4** : Augmente les effets de +1 niveau
- **Application** : Casque uniquement

---

### 👑 Module Ranks

Système de grades avec préfixes/suffixes personnalisables et intégration TAB/chat.

#### Fonctionnalités
- **Grades personnalisables** : JOUEUR, VIP, STAR, MOD, ADMIN (configurables)
- **Préfixes customs** : Texte personnalisé avant le nom du joueur
- **Suffixes** : Badge de grade après le nom (visible TAB + nametag)
- **Système de mute** : Temporaire ou permanent avec raisons
- **TAB list personnalisée** : Header/footer configurables avec MiniMessage
- **Priorité des grades** : Ordre d'affichage dans le TAB
- **Intégration chat** : Format automatique avec grades et préfixes

#### Grades par défaut

| Grade | Suffix | Priorité | Couleur |
|-------|--------|----------|---------|
| ADMIN | `[ADMIN]` | 100 | Rouge dégradé |
| MOD | `[MOD]` | 80 | Cyan dégradé |
| STAR | `[STAR]` | 60 | Violet dégradé |
| VIP | `[VIP]` | 40 | Rose dégradé |
| JOUEUR | - | 0 | - |

#### Système de Mute
- **Durée** : Minutes, heures, jours ou permanent
- **Raisons** : Configurables (Spam, Langage inapproprié, etc.)
- **Expiration** : Automatique avec vérification en temps réel
- **Notification** : Message au joueur avec raison et durée

---

### 🌍 Module Earth Tools

Outils spécialisés pour serveurs Earth (carte 1:750 du monde).

#### /gps - Conversion de coordonnées
Convertit les coordonnées GPS ↔ Minecraft.

**Sous-commandes** :
- `/gps tominecraft <latitude> <longitude>` : GPS → Minecraft
- `/gps togps <x> <z>` : Minecraft → GPS

**Formules** :
- Latitude → Z : `z = latitude × -136.653`
- Longitude → X : `x = longitude × 136.653`

#### /country - Détection de pays
Affiche le pays actuel basé sur les coordonnées GPS.

**Fonctionnalités** :
- 🌐 **APIs multiples** : Nominatim (OpenStreetMap) + BigDataCloud (fallback)
- 💾 **Cache intelligent** : 5 minutes pour succès, 1 minute pour erreurs
- 🔄 **Système de fallback** : Bascule automatique si une API échoue
- 🎨 **Remplacements personnalisables** : `country_replacements.yml`
- 📍 **Précision** : Cache avec arrondi à 0.01° (environ 1km)

**Fichier de remplacements** : Permet de personnaliser les noms de pays affichés avec couleurs MiniMessage.

#### /tpr - Téléportation aléatoire
Téléporte à un emplacement aléatoire sûr.

**Sécurités** :
- ✅ Évite les zones Towny protégées
- ✅ Vérifie la solidité du sol
- ✅ Évite lave, eau, blocs dangereux
- ✅ 50 tentatives maximum
- ✅ Support Nether (plateformes sûres)
- ⏱️ Cooldown configurable (défaut: 60s)

**Limites de carte** :
- X : ±24597 blocs
- Z : ±12298 blocs

#### /uptime - Temps de jeu
Affiche le temps de jeu des joueurs.

**Sous-commandes** :
- `/uptime <joueur>` : Temps de jeu d'un joueur
- `/uptime leaderboard` : Classement complet

**Classement** :
- 🥇 1er : Or
- 🥈 2ème : Argent
- 🥉 3ème : Bronze
- Autres : Jaune

#### /countrylist - Liste des pays
Affiche un texte personnalisé (bannière serveur, liste de pays, etc.).

**Configuration** :
- Fichier : `countrylist.txt`
- Support codes couleur : `&` classiques + `&#RRGGBB` hexadécimaux
- Commande admin : `/setcountrylist <texte>` (utiliser `\n` pour les sauts de ligne)

---

### 💬 Module Lobby Chat

Fonctionnalités de lobby et personnalisation du chat.

#### /lobby & /hub
Téléporte au spawn du monde principal.

**Configuration** :
- Monde : `world` (configurable)
- Spawn : Utilise le spawn du monde ou coordonnées fixes

#### Remplacements de chat
Système de remplacements automatiques dans le chat.

**Exemples** :
```yaml
replacements:
  france: "⓪"
  UK: "ⓧ"
  japan: "⑥"
```

Plus de **100 remplacements de pays** pré-configurés avec symboles Unicode.

---

### ⏱️ Module Anti-AFK

Système de détection d'inactivité.

**Fonctionnalités** :
- ⏱️ **Timer** : Kick après 5 minutes d'inactivité (configurable)
- ⚠️ **Avertissement** : 30 secondes avant kick (configurable)
- 🎯 **Détection précise** : Mouvements, actions, commandes, chat, inventaire
- 🔔 **Notification admin** : Optionnelle lors des kicks

**Activités détectées** :
- Mouvements (déplacement + rotation caméra)
- Casse/pose de blocs
- Clics inventaire
- Messages chat
- Commandes
- Attaques
- Interactions
- Drop/ramassage d'items

---

## 📦 Installation

### Prérequis

- ✅ Serveur **Paper 1.21.4** (ou supérieur)
- ✅ **Java 21** minimum
- ✅ **Datapack UbuPlugin** (pour les enchantements)
- ⚠️ **Towny** (optionnel, pour protection TPR)

### Étapes d'installation

1. **Téléchargez** le fichier `UbuPlugin-2.0.0.jar`

2. **Placez** le fichier dans le dossier `plugins/` de votre serveur

3. **Installez le datapack** :
   - Placez le datapack dans `world/datapacks/`
   - Ou utilisez `/datapack enable "file/ubuplugin_datapack.zip"`

4. **Démarrez** le serveur

5. **Vérifiez** l'installation :
   ```
   /ubu info
   ```

6. **(Optionnel)** Configurez le plugin :
   - Éditez `plugins/UbuPlugin/config.yml`
   - Rechargez : `/ubu reload`

### Structure des fichiers

```
plugins/UbuPlugin/
├── config.yml                    # Configuration principale
├── country_replacements.yml      # Remplacements de pays
├── messages_fr.yml              # Traductions françaises
├── messages_en.yml              # Traductions anglaises
├── countrylist.txt              # Liste de pays personnalisée
├── ubuplugin.db                 # Base de données SQLite
└── backups/                     # Sauvegardes automatiques
    └── players_YYYY-MM-DD_HH-mm-ss.yml
```

---

## ⚙️ Configuration

### Configuration principale (config.yml)

Le fichier `config.yml` est entièrement commenté et permet de personnaliser tous les aspects du plugin.

#### Sections principales

```yaml
general:
  default-language: fr           # Langue par défaut (fr/en)
  auto-detect-language: true     # Détection automatique
  debug: false                   # Mode debug (logs détaillés)

database:
  type: SQLITE                   # Type de BDD
  file: ubuplugin.db            # Nom du fichier
  pool:
    maximum-pool-size: 10        # Taille du pool
    
modules:
  enabled:
    enchants: true               # Activer/désactiver chaque module
    ranks: true
    earthtools: true
    lobbychat: true
    antiafk: true
```

#### Configuration des enchantements

Chaque enchantement dispose de sa propre section :

```yaml
enchants:
  timber:
    enabled: true
    max-blocks: 150              # Limite de blocs
    cooldown: 1                  # Secondes
    
  magnetic:
    enabled: true
    attraction-radius: 5         # Blocs
    
  dash:
    enabled: true
    cooldown:
      level-1: 10
      level-2: 5
      level-3: 3                 # Dash invulnérable
    speed:
      level-1: 1.5
      level-2: 2.0
      level-3: 2.5
    invulnerability-duration: 30 # Ticks (1.5s)
```

### Configuration des ranks

```yaml
ranks:
  tab:
    header: "<gray>≡ <aqua>Serveur</aqua> <gray>≡"
    footer: "<gold>play.exemple.fr"
    
  list:
    ADMIN:
      suffix: "<red>[ADMIN]"
      priority: 100
    VIP:
      suffix: "<gold>[VIP]"
      priority: 40
```

### Remplacements de pays

Fichier `country_replacements.yml` :

```yaml
# Format: nom_original: nom_personnalisé
france: "<blue>France</blue> <white>⚜</white>"
united states: "<red>USA</red> <white>🦅</white>"
japan: "<white>Japan</white> <red>🗾</red>"
```

Supporte les codes couleur **MiniMessage** :
- `<red>`, `<gold>`, `<green>`, etc.
- `<bold>`, `<italic>`, `<underlined>`
- `<gradient:red:blue>Texte</gradient>`

---

## 📜 Commandes & Permissions

### Commande principale

| Commande | Description | Permission |
|----------|-------------|------------|
| `/ubu help` | Affiche l'aide | `ubuplugin.command.main` |
| `/ubu info` | Informations plugin | `ubuplugin.command.main` |
| `/ubu modules` | Liste des modules | `ubuplugin.command.main` |
| `/ubu reload` | Recharge la config | `ubuplugin.command.reload` |
| `/ubu debug` | Toggle debug | `ubuplugin.admin` |

---

### Module Enchantements

#### Commandes admin

| Commande | Description | Permission |
|----------|-------------|------------|
| `/timber give <joueur>` | Donner Timber | `ubuplugin.enchants.admin` |
| `/magnetic give <joueur>` | Donner Magnetic | `ubuplugin.enchants.admin` |
| `/experience give <joueur> [1-3]` | Donner Experience | `ubuplugin.enchants.admin` |
| `/explosive give <joueur>` | Donner Excavator | `ubuplugin.enchants.admin` |
| `/veinminer give <joueur>` | Donner Veinminer | `ubuplugin.enchants.admin` |
| `/dash give <joueur> [1-3]` | Donner Dash | `ubuplugin.enchants.admin` |
| `/soulbound give <joueur>` | Donner Soulbound | `ubuplugin.enchants.admin` |
| `/autorepair give <joueur> [1-3]` | Donner Auto-Repair | `ubuplugin.enchants.admin` |
| `/beaconator give <joueur> [1-4]` | Donner Beaconator | `ubuplugin.enchants.admin` |

#### Commandes joueurs

| Commande | Description | Permission |
|----------|-------------|------------|
| `/toggle timber` | Toggle Timber | `ubuplugin.enchants.toggle` |
| `/toggle magnetic` | Toggle Magnetic | `ubuplugin.enchants.toggle` |
| `/toggle excavator` | Toggle Excavator | `ubuplugin.enchants.toggle` |
| `/toggle veinminer` | Toggle Veinminer | `ubuplugin.enchants.toggle` |

---

### Module Ranks

| Commande | Description | Permission |
|----------|-------------|------------|
| `/rank <joueur> <grade>` | Définir un grade | `ubuplugin.admin` |
| `/prefix <joueur> <texte>` | Définir un prefix | `ubuplugin.admin` |
| `/mute <joueur> [durée] [raison]` | Mute un joueur | `ubuplugin.admin` |
| `/unmute <joueur>` | Unmute un joueur | `ubuplugin.admin` |

**Exemples** :
```
/rank Tannoxx ADMIN
/prefix Tannoxx <gold>[Dev]
/mute Player 60 Spam
/unmute Player
```

---

### Module Earth Tools

| Commande | Description | Permission |
|----------|-------------|------------|
| `/gps tominecraft <lat> <lon>` | GPS → Minecraft | `ubuplugin.earthtools.gps` |
| `/gps togps <x> <z>` | Minecraft → GPS | `ubuplugin.earthtools.gps` |
| `/country` | Afficher le pays | `ubuplugin.earthtools.country` |
| `/tpr` | Téléportation aléatoire | `ubuplugin.earthtools.tpr` |
| `/uptime <joueur>` | Temps de jeu | `ubuplugin.earthtools.uptime` |
| `/uptime leaderboard` | Classement | `ubuplugin.earthtools.uptime` |
| `/countrylist` | Afficher la liste | `ubuplugin.earthtools.countrylist` |
| `/setcountrylist <texte>` | Modifier la liste | `ubuplugin.admin` |

---

### Module Lobby Chat

| Commande | Description | Permission |
|----------|-------------|------------|
| `/lobby` | Retour au lobby | `ubuplugin.lobbychat.use` |
| `/hub` | Alias de /lobby | `ubuplugin.lobbychat.use` |

---

### Permissions globales

| Permission | Description | Défaut |
|-----------|-------------|--------|
| `ubuplugin.*` | Toutes les permissions | OP |
| `ubuplugin.admin` | Accès administrateur | OP |
| `ubuplugin.command.*` | Toutes les commandes | OP |
| `ubuplugin.enchants.*` | Tous les enchantements | OP |
| `ubuplugin.enchants.use` | Utiliser les enchantements | true |
| `ubuplugin.enchants.toggle` | Toggle enchantements | true |
| `ubuplugin.earthtools.*` | Tous Earth Tools | true |
| `ubuplugin.lobbychat.*` | Lobby Chat | true |

---

## 🗄️ Base de données

### Architecture

UbuPlugin utilise **SQLite** avec **HikariCP** pour des performances optimales.

#### Tables principales

**players**
```sql
uuid            TEXT PRIMARY KEY  -- UUID du joueur
username        TEXT              -- Nom
rank            TEXT              -- Grade (JOUEUR, VIP, etc.)
prefix          TEXT              -- Prefix personnalisé
muted           BOOLEAN           -- Statut mute
mute_reason     TEXT              -- Raison du mute
mute_expires    INTEGER           -- Timestamp expiration
first_join      INTEGER           -- Première connexion
last_seen       INTEGER           -- Dernière connexion
```

**enchant_cooldowns**
```sql
uuid            TEXT              -- UUID du joueur
enchant_type    TEXT              -- Type d'enchantement
expires_at      INTEGER           -- Timestamp expiration
```

**magnetic_toggles**
```sql
uuid            TEXT PRIMARY KEY  -- UUID du joueur
enabled         BOOLEAN           -- Magnetic activé/désactivé
```

**country_cache**
```sql
cache_key       TEXT PRIMARY KEY  -- Clé cache (lat,lon arrondi)
country_name    TEXT              -- Nom du pays
is_error        BOOLEAN           -- Cache d'erreur
timestamp       INTEGER           -- Timestamp création
```

### Migration automatique

Le plugin migre automatiquement les données depuis les anciens fichiers YAML :

1. **Détection** : Au démarrage, vérifie si `players.yml` existe
2. **Backup** : Crée une sauvegarde dans `backups/`
3. **Migration** : Importe les données dans SQLite
4. **Flag** : Crée `.migration_completed` pour éviter les doubles migrations

Configuration :
```yaml
database:
  migration:
    enabled: true        # Activer migration
    backup: true         # Créer backup
```

### Optimisations

- ✅ **WAL mode** : Write-Ahead Logging pour performances
- ✅ **Pool HikariCP** : 2-10 connexions
- ✅ **Cache Caffeine** : Réduction des requêtes SQL
- ✅ **Prepared Statements** : Sécurité SQL Injection
- ✅ **Batch inserts** : Insertion groupée (100 entrées)
- ✅ **Index automatiques** : Sur colonnes fréquentes

---

## 🔧 Support & Contact

### Problèmes connus

- ❌ **Enchantements non chargés** : Vérifiez que le datapack est bien installé (`/datapack list`)
- ❌ **Erreur SQLite native** : Le plugin shade correctement les bibliothèques natives
- ❌ **Conflits Towny** : Désactivez `check-towny` dans `/tpr` si problèmes

### Support

Pour toute question ou problème :

- 🌐 **Web map** : [http://ubuearth.fr/8080](http://ubuearth.fr:8080)
- 💬 **Discord** : [Rejoindre le serveur](https://discord.gg/GuwgpBk2MX)
- 📧 **Serveur** : `play.ubuearth.fr`

### Développeur

- 👤 **Auteur** : Tannoxx
- 📅 **Dernière mise à jour** : Décembre 2024
- 🏷️ **Version actuelle** : 2.0.0

---

## 📄 License

Copyright © 2024 Tannoxx - UbuEarth SMP

Tous droits réservés. Ce plugin est propriétaire et développé spécifiquement pour **UbuEarth SMP**.

---

<div align="center">

**🌍 Développé avec ❤️ pour UbuEarth SMP**

[![Paper](https://img.shields.io/badge/Paper-1.21.10-00ADD8?style=for-the-badge&logo=minecraft)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=java)](https://adoptium.net/)

</div>