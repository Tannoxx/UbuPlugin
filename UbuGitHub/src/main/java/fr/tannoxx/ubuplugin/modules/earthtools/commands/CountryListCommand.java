package fr.tannoxx.ubuplugin.modules.earthtools.commands;

import fr.tannoxx.ubuplugin.modules.earthtools.EarthToolsModule;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record CountryListCommand(EarthToolsModule module) implements CommandExecutor {

    private static final Pattern HEX_PATTERN = Pattern.compile("&(#[A-Fa-f0-9]{6})");

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        File file = new File(module.plugin.getDataFolder(), "countrylist.txt");

        if (!file.exists()) {
            module.getTranslationManager().send(sender, "earthtools.countrylist.not-found");
            return true;
        }

        try {
            String content = Files.readString(file.toPath());
            content = translateColorCodes(content);

            for (String line : content.split("\\n")) {
                sender.sendMessage(line);
            }
        } catch (IOException e) {
            module.getTranslationManager().send(sender, "earthtools.countrylist.read-error");
            module.error("Erreur lecture countrylist.txt: " + e.getMessage());
        }

        return true;
    }

    /**
     * Traduit les codes couleur (&a, &b, etc.) et les codes hexadécimaux (&#RRGGBB)
     * en codes couleur Minecraft (§)
     */
    private String translateColorCodes(String text) {
        // Convertir les codes hexadécimaux &#RRGGBB en §x§R§R§G§G§B§B
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuilder buffer = new StringBuilder();

        while (matcher.find()) {
            String hex = matcher.group(1); // #RRGGBB
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.substring(1).toCharArray()) {
                replacement.append("§").append(c);
            }
            matcher.appendReplacement(buffer, replacement.toString());
        }
        matcher.appendTail(buffer);

        // Convertir les codes couleur standards &0-9, &a-f, &k-o, &r
        return buffer.toString().replace('&', '§');
    }
}