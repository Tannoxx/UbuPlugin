package fr.tannoxx.ubuplugin.modules.enchants.commands;

import fr.tannoxx.ubuplugin.modules.enchants.EnchantsModule;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Commande /toggle pour activer/désactiver les enchantements
 * Gère: Timber, Magnetic, Excavator (Explosive), Veinminer
 */
public record ToggleCommand(EnchantsModule module) implements CommandExecutor, TabCompleter {

    public ToggleCommand(@NotNull EnchantsModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String @NonNull [] args) {
        if (!(sender instanceof Player player)) {
            module.getTranslationManager().send(sender, "errors.player-only");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(module.getTranslationManager().getComponent(sender,
                    "<red>Usage: /toggle <timber|magnetic|excavator|veinminer></red>"));
            return true;
        }

        String enchantType = args[0].toLowerCase();
        UUID uuid = player.getUniqueId();

        switch (enchantType) {
            case "timber" -> toggleTimber(player, uuid);
            case "magnetic" -> toggleMagnetic(player, uuid);
            case "excavator", "explosive" -> toggleExcavator(player, uuid);
            case "veinminer" -> toggleVeinminer(player, uuid); // ✅ AJOUTÉ
            default -> sender.sendMessage(module.getTranslationManager().getComponent(sender,
                    "<red>Type invalide ! Utilisez: timber, magnetic, excavator, ou veinminer</red>"));
        }

        return true;
    }

    private void toggleTimber(@NotNull Player player, @NotNull UUID uuid) {
        Boolean currentState = module.getTimberToggles().getIfPresent(uuid);
        boolean newState = currentState == null || !currentState;

        module.getTimberToggles().put(uuid, newState);

        if (newState) {
            module.getTranslationManager().send(player, "enchants.toggle.timber.enabled");
        } else {
            module.getTranslationManager().send(player, "enchants.toggle.timber.disabled");
        }
    }

    private void toggleMagnetic(@NotNull Player player, @NotNull UUID uuid) {
        Boolean currentState = module.getMagneticToggles().getIfPresent(uuid);
        boolean newState = currentState == null || !currentState;

        module.getMagneticToggles().put(uuid, newState);

        if (newState) {
            module.getTranslationManager().send(player, "enchants.toggle.magnetic.enabled");
        } else {
            module.getTranslationManager().send(player, "enchants.toggle.magnetic.disabled");
        }
    }

    private void toggleExcavator(@NotNull Player player, @NotNull UUID uuid) {
        Boolean currentState = module.getExcavatorToggles().getIfPresent(uuid);
        boolean newState = currentState == null || !currentState;

        module.getExcavatorToggles().put(uuid, newState);

        if (newState) {
            module.getTranslationManager().send(player, "enchants.toggle.excavator.enabled");
        } else {
            module.getTranslationManager().send(player, "enchants.toggle.excavator.disabled");
        }
    }

    // ✅ AJOUTÉ: Toggle pour Veinminer
    private void toggleVeinminer(@NotNull Player player, @NotNull UUID uuid) {
        Boolean currentState = module.getVeinminerToggles().getIfPresent(uuid);
        boolean newState = currentState == null || !currentState;

        module.getVeinminerToggles().put(uuid, newState);

        if (newState) {
            module.getTranslationManager().send(player, "enchants.toggle.veinminer.enabled");
        } else {
            module.getTranslationManager().send(player, "enchants.toggle.veinminer.disabled");
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String @NonNull [] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("timber");
            completions.add("magnetic");
            completions.add("excavator");
            completions.add("veinminer"); // ✅ AJOUTÉ

            String input = args[0].toLowerCase();
            return completions.stream()
                    .filter(s -> s.startsWith(input))
                    .sorted()
                    .toList();
        }

        return completions;
    }
}