package dev.flamesentinell.flameffacore.crate;

import org.bukkit.Material;

public record Crate(String id, String display, int tokenPrice, Material voucher) {
}
