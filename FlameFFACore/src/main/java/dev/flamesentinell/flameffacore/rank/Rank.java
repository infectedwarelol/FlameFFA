package dev.flamesentinell.flameffacore.rank;

import org.bukkit.Material;

public record Rank(String id, String group, int weight, String display, Material icon, int cooldownSeconds, boolean perk, String lockedHint) {
}
