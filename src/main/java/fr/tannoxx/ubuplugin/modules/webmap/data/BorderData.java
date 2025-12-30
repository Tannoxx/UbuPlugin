package fr.tannoxx.ubuplugin.modules.webmap.data;

import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Représente les frontières d'un pays
 *
 * @param countryName Nom du pays
 * @param coordinates Liste de listes de points (latitude, longitude)
 */
public record BorderData(
        @NotNull String countryName,
        @NotNull List<List<double[]>> coordinates
) {

    /**
     * Représente un point de frontière converti en coordonnées Minecraft
     */
    public record MinecraftPoint(int x, int z) {
        @Override
        public @NonNull String toString() {
            return String.format("(%d, %d)", x, z);
        }
    }
}