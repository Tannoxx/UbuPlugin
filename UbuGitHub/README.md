# 🎮 UbuPlugin v2.0.0

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net/)
[![Paper](https://img.shields.io/badge/Paper-1.21.4-blue.svg)](https://papermc.io/)
[![License](https://img.shields.io/badge/License-Private-red.svg)]()

Plugin professionnel pour le serveur Minecraft **UbuEarth SMP** (play.ubuearth.fr)

## 📋 Fonctionnalités

### 🔮 Enchantements Customs
- **Timber** - Coupe tous les troncs connectés (configurable jusqu'à 150 blocs)
- **Magnetic** - Items récupérés directement (toggle avec Shift + Clic Droit)
- **Experience** - Bonus XP configurable par niveau
- **Explosive** - Minage 3x3 avec Fortune/Silk Touch compatible
- **Dash** - Propulsion rapide (double-sneak)
- **Soulbound** - Garde les items après la mort
- **Auto-Repair** - Réparation automatique configurable
- **Beaconator** - Étend la portée des beacons jusqu'à +140 blocs

### 👑 Système de Ranks
- Ranks personnalisables avec priorités
- Prefixes customs avec MiniMessage
- TAB list formatée
- Système de mute intégré
- Chat personnalisé

### 🌍 Earth Tools
- **GPS** - Conversion coordonnées GPS ↔ Minecraft
- **Country** - Détection automatique du pays (cache optimisé)
- **TPR** - Téléportation aléatoire sécurisée (évite Towny)
- **Uptime** - Statistiques de temps de jeu avec leaderboard
- **Country List** - Liste personnalisable des pays

### 💬 Lobby Chat
- Téléportation au spawn (/lobby, /hub)
- Remplacements de chat configurables (emojis, symboles)

### ⚒️ Anvil
- Supprime la limite "Too Expensive"
- Coût maximum personnalisable
- Avertissement pour coûts élevés

## 🚀 Installation

### Prérequis
- **Java 21+** (OpenJDK ou Adoptium)
- **Paper/Purpur 1.21.4+**
- **Towny** (optionnel - pour protection TPR)

### Étapes

1. **Télécharger le plugin**
   ```bash
   # Compiler depuis les sources
   mvn clean package
   ```

2. **Installer sur le serveur**
   ```bash
   # Copier le .jar dans plugins/
   cp target/UbuPlugin-2.0.0.jar /path/to/server/plugins/
   ```

3. **Installer le datapack des enchantements**
    - Placez le datapack dans `world/datapacks/`
    - Rechargez avec `/reload`

4. **Démarrer le serveur**
    - Le plugin créera automatiquement la configuration
    - La migration depuis YAML sera automatique (avec backup)

## ⚙️ Configuration

### Fichier principal : `config.yml`

```yaml
# Langue par défaut
general:
  default-language: fr
  auto-detect-language: true
  debug: false

# Base de données (SQLite)
database:
  type: SQLITE
  file: ubuplugin.db
  migration:
    enabled: true
    backup: true

# Activer/désactiver les modules
modules:
  enabled:
    enchants: true
    ranks: true
    earthtools: true
    lobbychat: true
    anvil: true
```

### Enchantements configurables

```yaml
enchants:
  timber:
    max-blocks: 150
    cooldown: 3
  
  magnetic:
    toggle-enabled: true
    collection-window: 5000
  
  explosive:
    fortune-compatible: true
    silk-touch-compatible: true
    collect-all-xp: true
```

### Système de Ranks

```yaml
ranks:
  list:
    ADMIN:
      suffix: "<dark_red><bold>[ADMIN]</bold></dark_red>"
      priority: 100
    VIP:
      suffix: "<gold><bold>[VIP]</bold></gold>"
      priority: 40
```

## 🎯 Commandes

### Commande principale
```
/ubu help          - Affiche l'aide
/ubu reload        - Recharge la configuration
/ubu modules       - Liste des modules et statuts
/ubu info          - Informations sur le plugin
/ubu debug         - Active/désactive le mode debug
```

### Enchantements (OP uniquement)
```
/timber give <joueur>
/magnetic give <joueur>
/experience give <joueur>
/explosive give <joueur>
/dash give <joueur> [niveau]
/soulbound give <joueur>
/autorepair give <joueur> [niveau]
/beaconator give <joueur> [niveau]
```

### Ranks (OP uniquement)
```
/rank <joueur> <rank>           - Définir un rank
/prefix <joueur> <texte>        - Définir un prefix
/mute <joueur> [durée] [raison] - Mute un joueur
/unmute <joueur>                - Unmute un joueur
```

### Earth Tools
```
/gps tominecraft <lat> <lon>    - GPS → Minecraft
/gps togps <x> <z>              - Minecraft → GPS
/country                        - Affiche le pays actuel
/tpr                            - Téléportation aléatoire
/uptime <joueur>                - Temps de jeu
/uptime leaderboard             - Classement
/countrylist                    - Affiche la liste
/setcountrylist <texte>         - Modifie la liste (OP)
```

### Lobby
```
/lobby, /hub                    - Retour au spawn
```

## 🔑 Permissions

```yaml
ubuplugin.*                     - Toutes les permissions
ubuplugin.admin                 - Accès administrateur
ubuplugin.command.reload        - Recharger la config
ubuplugin.module.*              - Accès à tous les modules
```

## 🌐 Traductions

Le plugin supporte **Français** et **Anglais** avec détection automatique de la langue du client.

Fichiers de traduction :
- `messages_fr.yml` - Traductions françaises
- `messages_en.yml` - Traductions anglaises

## 🗃️ Base de données

### SQLite (Par défaut)
- Fichier : `plugins/UbuPlugin/ubuplugin.db`
- Pool de connexions : HikariCP
- WAL mode activé pour performances
- Migration automatique depuis YAML

### Structure
```sql
players                 - Joueurs (ranks, prefixes, mute)
enchant_cooldowns       - Cooldowns des enchantements
magnetic_toggles        - États Magnetic par joueur
country_cache           - Cache des pays
```

## 📊 Performances

### Optimisations
- ✅ **Cache Caffeine** - TTL configurable par type
- ✅ **HikariCP** - Pool de connexions optimisé
- ✅ **Requêtes asynchrones** - Aucun lag
- ✅ **Batch processing** - Pour migrations et insertions
- ✅ **Thread-safe** - ConcurrentHashMap partout

### Tests de charge
- ✅ **10 joueurs** - 0ms de lag
- ✅ **30 joueurs** - <1ms de lag (testé)
- ✅ **50+ joueurs** - Architecture scalable

## 🔧 Développement

### Compiler depuis les sources

```bash
# Cloner le repository
git clone https://github.com/tannoxx/UbuPlugin.git
cd UbuPlugin

# Compiler
mvn clean package

# Le .jar sera dans target/
```

### Structure du projet

```
UbuPlugin/
├── src/main/java/
│   └── fr/tannoxx/ubuplugin/
│       ├── UbuPlugin.java              # Classe principale
│       ├── common/                     # Classes communes
│       │   ├── config/                 # Gestion config
│       │   ├── database/               # Gestion BDD
│       │   ├── i18n/                   # Traductions
│       │   └── module/                 # Système modulaire
│       ├── commands/                   # Commandes
│       └── modules/                    # Modules
│           ├── enchants/               # Enchantements
│           ├── ranks/                  # Système de ranks
│           ├── earthtools/             # Outils Earth
│           ├── lobbychat/              # Lobby & Chat
│           └── anvil/                  # Module Anvil
└── src/main/resources/
    ├── plugin.yml                      # Configuration plugin
    ├── config.yml                      # Configuration principale
    ├── messages_fr.yml                 # Traductions FR
    └── messages_en.yml                 # Traductions EN
```

### Ajouter un nouveau module

```java
public class MyModule extends Module {
    
    @Override
    public void onEnable() {
        // Initialisation
        info("Mon module activé");
    }
    
    @Override
    public void onDisable() {
        // Nettoyage
    }
    
    @NotNull
    @Override
    public String getName() {
        return "MyModule";
    }
    
    @NotNull
    @Override
    public String getDescription() {
        return "Description de mon module";
    }
}
```

## 🐛 Débogage

### Activer le mode debug
```yaml
general:
  debug: true
```

Ou en jeu :
```
/ubu debug
```

### Logs
Les logs sont dans `logs/latest.log` avec le format :
```
[HH:MM:SS INFO]: [UbuPlugin] Message
```

### Problèmes courants

**Le plugin ne démarre pas**
- Vérifiez Java 21+
- Vérifiez Paper/Purpur 1.21.4+
- Consultez les logs

**Les enchantements ne fonctionnent pas**
- Vérifiez que le datapack est installé
- Rechargez avec `/reload`
- Vérifiez les logs pour "✓ Timber chargé"

**Migration YAML échoue**
- Un backup est créé dans `plugins/UbuPlugin/backups/`
- Supprimez `.migration_completed` pour réessayer
- Vérifiez les permissions du dossier

## 📝 Changelog

### v2.0.0 (2024-12-07)
- ✨ Refonte complète du plugin
- ✨ Architecture modulaire
- ✨ Base de données SQLite avec HikariCP
- ✨ Cache Caffeine pour performances
- ✨ Support i18n (FR/EN)
- ✨ Nouveau module Anvil
- ✨ Magnetic toggle (Shift + Clic Droit)
- ✨ Explosive amélioré (Fortune/Silk Touch)
- ✨ Commande /ubu centralisée
- ✨ Migration automatique depuis YAML
- 🐛 Corrections de bugs majeurs
- ⚡ Performances optimisées

### v1.0.0
- Version initiale

## 🤝 Contribution

Ce projet est privé et développé pour **UbuEarth SMP**.

## 📄 License

Copyright © 2024 Tannoxx - Tous droits réservés

---

## 📞 Support

**Serveur** : play.ubuearth.fr  
**Auteur** : Tannoxx  
**Version** : 2.0.0  
**Minecraft** : 1.21.4

---

<div align="center">
  <strong>Fait avec ❤️ pour UbuEarth SMP</strong>
</div>