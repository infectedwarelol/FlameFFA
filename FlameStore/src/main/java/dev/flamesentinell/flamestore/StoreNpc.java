package dev.flamesentinell.flamestore;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.Display.Brightness;
import org.bukkit.entity.TextDisplay.TextAlignment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

final class StoreNpc implements Listener {
   private final String TAG;
   private final String path;
   private final Consumer<Player> action;
   private final FlameStore plugin;
   private Mannequin body;
   private TextDisplay title;
   private Location where;
   private ResolvableProfile profile;
   private Sound sound;

   StoreNpc(FlameStore var1, String var2, String var3, Consumer<Player> var4) {
      this.plugin = var1;
      this.path = var2;
      this.TAG = var3;
      this.action = var4;
   }

   private FileConfiguration cfg() {
      return this.plugin.getConfig();
   }

   void load() {
      ConfigurationSection var1 = this.cfg().getConfigurationSection(this.path + ".location");
      this.where = null;
      if (var1 != null && Bukkit.getWorld(var1.getString("world", "world")) != null) {
         this.where = new Location(
            Bukkit.getWorld(var1.getString("world", "world")),
            var1.getDouble("x"),
            var1.getDouble("y"),
            var1.getDouble("z"),
            (float)var1.getDouble("yaw"),
            0.0F
         );
      }

      String var2 = this.cfg().getString(this.path + ".click-sound", "");
      this.sound = var2.isEmpty() ? null : (Sound)Registry.SOUNDS.get(NamespacedKey.minecraft(var2));
      ConfigurationSection var3 = this.cfg().getConfigurationSection(this.path + ".skin");
      this.profile = null;
      if (var3 != null && var3.getString("value") != null) {
         try {
            PlayerProfile var4 = Bukkit.createProfile(UUID.fromString(var3.getString("uuid")), var3.getString("name", "store"));
            var4.setProperty(new ProfileProperty("textures", var3.getString("value"), var3.getString("signature")));
            this.profile = ResolvableProfile.resolvableProfile(var4);
         } catch (RuntimeException var5) {
            this.plugin.getLogger().warning("npc skin in config.yml is not valid  " + var5);
         }
      }
   }

   void spawn() {
      this.despawn();
      this.sweep();
      if (this.cfg().getBoolean(this.path + ".enabled", true) && this.where != null && this.where.isChunkLoaded()) {
         World var1 = this.where.getWorld();
         double var2 = this.cfg().getDouble(this.path + ".scale", 1.5);
         this.body = (Mannequin)var1.spawn(this.where, Mannequin.class, var3 -> {
            var3.setDescription(Component.empty());
            var3.setCustomNameVisible(false);
            var3.setImmovable(true);
            var3.setGravity(false);
            var3.setSilent(true);
            var3.setPersistent(false);
            var3.setInvulnerable(true);
            var3.setCollidable(false);
            var3.addScoreboardTag(this.TAG);
            AttributeInstance var4x = var3.getAttribute(Attribute.SCALE);
            if (var4x != null) {
               var4x.setBaseValue(var2);
            }

            if (this.profile != null) {
               var3.setProfile(this.profile);
            }

            String var5 = this.cfg().getString(this.path + ".boots-color", "");
            if (!var5.isEmpty()) {
               ItemStack var6 = new ItemStack(Material.LEATHER_BOOTS);
               LeatherArmorMeta var7 = (LeatherArmorMeta)var6.getItemMeta();
               var7.setColor(Color.fromRGB(Integer.parseInt(var5.replace("#", ""), 16)));
               var7.setEnchantmentGlintOverride(true);
               var6.setItemMeta(var7);
               var3.getEquipment().setBoots(var6);
            }
         });
         float var4 = (float)this.cfg().getDouble(this.path + ".title-size", 2.0);
         this.title = (TextDisplay)var1.spawn(
            this.where.clone().add(0.0, 2.0 * var2 + 0.25, 0.0),
            TextDisplay.class,
            var2x -> {
               var2x.setBillboard(Billboard.CENTER);
               var2x.setAlignment(TextAlignment.CENTER);
               var2x.setShadowed(true);
               var2x.setPersistent(false);
               var2x.setLineWidth(1000);
               var2x.setBrightness(new Brightness(15, 15));
               var2x.addScoreboardTag(this.TAG);
               var2x.text(MiniMessage.miniMessage().deserialize(this.cfg().getString(this.path + ".title", "<bold>STORE")));
               var2x.setTransformation(
                  new Transformation(
                     new Vector3f(), var2x.getTransformation().getLeftRotation(), new Vector3f(var4, var4, var4), var2x.getTransformation().getRightRotation()
                  )
               );
            }
         );
      }
   }

   void despawn() {
      for (Entity var4 : new Entity[]{this.body, this.title}) {
         if (var4 != null && var4.isValid()) {
            var4.remove();
         }
      }

      this.body = null;
      this.title = null;
   }

   private void sweep() {
      for (World var2 : Bukkit.getWorlds()) {
         for (Entity var4 : var2.getEntities()) {
            if (var4.getScoreboardTags().contains(this.TAG)) {
               var4.remove();
            }
         }
      }
   }

   void place(Location var1) {
      FileConfiguration var2 = this.cfg();
      var2.set(this.path + ".location.world", var1.getWorld().getName());
      var2.set(this.path + ".location.x", var1.getX());
      var2.set(this.path + ".location.y", var1.getY());
      var2.set(this.path + ".location.z", var1.getZ());
      var2.set(this.path + ".location.yaw", var1.getYaw());
      this.plugin.saveConfig();
      this.where = var1.clone();
      this.spawn();
   }

   void remove() {
      this.despawn();
      this.sweep();
      this.where = null;
      this.cfg().set(this.path + ".location", null);
      this.plugin.saveConfig();
   }

   void refresh() {
      if (this.where != null && this.cfg().getBoolean(this.path + ".enabled", true)) {
         if ((this.body == null || !this.body.isValid() || this.title == null || !this.title.isValid()) && this.where.isChunkLoaded()) {
            this.spawn();
         }
      }
   }

   private boolean isNpc(Entity var1) {
      return var1 != null && var1.getScoreboardTags().contains(this.TAG);
   }

   private void click(Player var1) {
      if (this.sound != null) {
         var1.playSound(var1.getLocation(), this.sound, 0.7F, 1.4F);
      }

      this.action.accept(var1);
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onRightClick(PlayerInteractEntityEvent var1) {
      if (this.isNpc(var1.getRightClicked())) {
         var1.setCancelled(true);
         if (var1.getHand() == EquipmentSlot.HAND) {
            this.click(var1.getPlayer());
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onLeftClick(PrePlayerAttackEntityEvent var1) {
      if (this.isNpc(var1.getAttacked())) {
         var1.setCancelled(true);
         this.click(var1.getPlayer());
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onHurt(EntityDamageEvent var1) {
      if (this.isNpc(var1.getEntity())) {
         var1.setCancelled(true);
      }
   }

   @EventHandler
   public void onChunk(ChunkLoadEvent var1) {
      if (this.where != null
         && this.where.getWorld() == var1.getWorld()
         && this.where.getBlockX() >> 4 == var1.getChunk().getX()
         && this.where.getBlockZ() >> 4 == var1.getChunk().getZ()) {
         Bukkit.getScheduler().runTask(this.plugin, this::refresh);
      }
   }
}
