package fr.tannoxx.ubuplugin.modules.earthtools.commands;

import fr.tannoxx.ubuplugin.modules.earthtools.EarthToolsModule;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;

public record SetCountryListCommand(EarthToolsModule module) implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String @NonNull [] args) {

        if (!(sender instanceof Player)) {
            module.getTranslationManager().send(sender, "errors.player-only");
            return true;
        }

        if (!sender.isOp()) {
            module.getTranslationManager().send(sender, "errors.op-only");
            return true;
        }

        if (args.length == 0) {
            module.getTranslationManager().send(sender, "earthtools.countrylist.usage");
            sender.sendMessage(module.getTranslationManager().get(sender,
                    "earthtools.countrylist.hint"));
            sender.sendMessage(module.getTranslationManager().get(sender,
                    "earthtools.countrylist.example"));
            return true;
        }

        String text = String.join(" ", Arrays.asList(args));
        text = text.replace("\\n", "\n");

        // Validation : vérifier que le texte n'est pas trop long
        if (text.length() > 10000) {
            module.getTranslationManager().send(sender, "earthtools.countrylist.too-long");
            return true;
        }

        // Validation : vérifier qu'il n'y a pas de caractères dangereux
        if (text.contains("\0") || text.contains("\r")) {
            module.getTranslationManager().send(sender, "earthtools.countrylist.forbidden-chars");
            return true;
        }

        try (FileWriter writer = new FileWriter(
                module.plugin.getDataFolder() + "/countrylist.txt", false)) {
            writer.write(text);

            module.getTranslationManager().send(sender, "earthtools.countrylist.set-success");
            sender.sendMessage(module.getTranslationManager().get(sender,
                    "earthtools.countrylist.lines-written",
                    String.valueOf(text.split("\n").length)));

        } catch (IOException e) {
            module.getTranslationManager().send(sender, "earthtools.countrylist.read-error");
            module.error("Erreur écriture countrylist.txt: " + e.getMessage());
        }

        return true;
    }
}