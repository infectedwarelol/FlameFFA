package dev.flamesentinell.flameffacore.weapon;

import dev.flamesentinell.flameffacore.FlameFFACore;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.Particle.DustOptions;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class SaturationCandle implements CustomWeapon {
   public static final String ID = "saturation-candle";
   private static final DustOptions WAX = new DustOptions(Color.fromRGB(255, 154, 46), 1.0F);

   @Override
   public String id() {
      return "saturation-candle";
   }

   @Override
   public CustomWeapon.Trigger trigger() {
      return CustomWeapon.Trigger.CLICK;
   }

   @Override
   public ItemStack build(FlameFFACore plugin) {
      return ItemStack.of(Material.ORANGE_CANDLE);
   }

   @Override
   public List<String> check(ItemStack built) {
      List<String> bad = new ArrayList<>();
      if (built.getType() != Material.ORANGE_CANDLE) {
         bad.add("saturation candle is not an orange candle");
      }

      return bad;
   }

   @Override
   public boolean onAbility(FlameFFACore plugin, Player player, ItemStack held) {
      FileConfiguration cfg = plugin.configs().main();
      int seconds = Math.max(1, cfg.getInt(this.path() + ".duration-seconds", 6));
      int level = Math.max(1, cfg.getInt(this.path() + ".level", 2));
      player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, seconds * 20, level - 1, false, true, true));
      Location at = player.getEyeLocation();
      World world = at.getWorld();
      Location flame = at.clone().add(at.getDirection().multiply(0.6)).add(0.0, -0.2, 0.0);
      world.spawnParticle(Particle.FLAME, flame, 6, 0.08, 0.1, 0.08, 0.01);
      world.spawnParticle(Particle.SMALL_FLAME, flame, 10, 0.15, 0.2, 0.15, 0.01);
      world.spawnParticle(Particle.DUST, flame, 12, 0.25, 0.25, 0.25, 0.0, WAX);
      world.playSound(at, Sound.ITEM_FLINTANDSTEEL_USE, 0.8F, 1.3F);
      world.playSound(at, Sound.BLOCK_CANDLE_AMBIENT, 1.0F, 1.0F);
      world.playSound(at, Sound.ENTITY_PLAYER_BURP, 0.5F, 1.4F);
      return true;
   }
}
