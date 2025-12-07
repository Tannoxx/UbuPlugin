package fr.tannoxx.ubuplugin.modules.earthtools.commands;

import fr.tannoxx.ubuplugin.UbuPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;

public record SetCountryListCommand(UbuPlugin plugin) implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {

        if (!(sender instanceof Player)) {
            sender.sendMessage("⓪ §cCommande réservée aux joueurs / §rⓧ §cThis command is for players only");
            return true;
        }

        if (!sender.isOp()) {
            sender.sendMessage("⓪ §cVous devez être OP pour utiliser cette commande / §rⓧ §cYou must be OP to use this command");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§cUsage: /setcountrylist <text>");
            sender.sendMessage("§7Utilisez \\n pour créer une nouvelle ligne");
            sender.sendMessage("§7Exemple: /setcountrylist France\\n-Admin\\nJapon\\n-Player");
            return true;
        }

        String text = String.join(" ", Arrays.asList(args));
        text = text.replace("\\n", "\n");

        // Validation : vérifier que le texte n'est pas trop long
        if (text.length() > 10000) {
            sender.sendMessage("⓪ §cLe texte est trop long ! (max 10000 caractères) / §rⓧ §cText is too long! (max 10000 characters)");
            return true;
        }

        // Validation : vérifier qu'il n'y a pas de caractères dangereux
        if (text.contains("\0") || text.contains("\r")) {
            sender.sendMessage("⓪ §cCaractères interdits détectés ! / §rⓧ §cForbidden characters detected!");
            return true;
        }

        try (FileWriter writer = new FileWriter(plugin.getDataFolder() + "/countrylist.txt", false)) {
            writer.write(text);
            sender.sendMessage("⓪ §aListe des pays mise à jour ! / §rⓧ §aCountry list updated!");
            sender.sendMessage("§7" + text.split("\n").length + " lignes écrites");
        } catch (IOException e) {
            sender.sendMessage("⓪ §cErreur lors de l'écriture du fichier / §rⓧ §cFailed to write file");
            plugin.getLogger().warning("Erreur lors de l'écriture de countrylist.txt: " + e.getMessage());
        }

        return true;
    }
}