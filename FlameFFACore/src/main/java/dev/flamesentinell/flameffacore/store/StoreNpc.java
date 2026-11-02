package dev.flamesentinell.flameffacore.store;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.flamesentinell.flameffacore.util.Tasks;
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
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.Display.Brightness;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
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
   private final String tag;
   private final String path;
   private final Consumer<Player> action;
   private final StoreModule store;
   private volatile Mannequin body;
   private volatile TextDisplay title;
   private volatile Location where;
   private volatile ResolvableProfile profile;
   private volatile Sound sound;

   StoreNpc(StoreModule store, String path, String tag, Consumer<Player> action) {
      this.store = store;
      this.path = path;
      this.tag = tag;
      this.action = action;
   }

   private FileConfiguration cfg() {
      return this.store.cfg();
   }

   void load() {
      ConfigurationSection spot = this.cfg().getConfigurationSection(this.path + ".location");
      Location loaded = null;
      if (spot != null) {
         World world = Bukkit.getWorld(spot.getString("world", "world"));
         if (world != null) {
            loaded = new Location(world, spot.getDouble("x"), spot.getDouble("y"), spot.getDouble("z"), (float)spot.getDouble("yaw"), 0.0F);
         }
      }

      this.where = loaded;
      String click = this.cfg().getString(this.path + ".click-sound", "");
      this.sound = click.isEmpty() ? null : Registry.SOUNDS.get(NamespacedKey.minecraft(click));
      ConfigurationSection skin = this.cfg().getConfigurationSection(this.path + ".skin");
      this.profile = null;
      if (skin != null && skin.getString("value") != null) {
         try {
            PlayerProfile owner = Bukkit.createProfile(UUID.fromString(skin.getString("uuid")), skin.getString("name", "store"));
            owner.setProperty(new ProfileProperty("textures", skin.getString("value"), skin.getString("signature")));
            this.profile = ResolvableProfile.resolvableProfile(owner);
         } catch (RuntimeException invalid) {
            this.store.plugin().getSLF4JLogger().warn("npc skin in store.yml is not valid  {}", invalid.toString());
         }
      }
   }

   void spawn() {
      Location at = this.where;
      this.despawn();
      if (at == null) {
         this.sweep();
      } else {
         this.store.plugin().tasks().runAt(at, () -> this.spawnNow(at));
      }
   }

   private void spawnNow(Location at) {
      if (at != this.where) {
         return;
      }

      this.despawnNow();
      this.sweep();
      if (this.cfg().getBoolean(this.path + ".enabled", true) && at.isChunkLoaded()) {
         World world = at.getWorld();
         double scale = this.cfg().getDouble(this.path + ".scale", 1.5);
         this.body = world.spawn(at, Mannequin.class, mannequin -> {
            mannequin.setDescription(Component.empty());
            mannequin.setCustomNameVisible(false);
            mannequin.setImmovable(true);
            mannequin.setGravity(false);
            mannequin.setSilent(true);
            mannequin.setPersistent(false);
            mannequin.setInvulnerable(true);
            mannequin.setCollidable(false);
            mannequin.addScoreboardTag(this.tag);
            AttributeInstance size = mannequin.getAttribute(Attribute.SCALE);
            if (size != null) {
               size.setBaseValue(scale);
            }

            ResolvableProfile skin = this.profile;
            if (skin != null) {
               mannequin.setProfile(skin);
            }

            String boots = this.cfg().getString(this.path + ".boots-color", "");
            if (!boots.isEmpty()) {
               try {
                  ItemStack shoes = new ItemStack(Material.LEATHER_BOOTS);
                  LeatherArmorMeta meta = (LeatherArmorMeta)shoes.getItemMeta();
                  meta.setColor(Color.fromRGB(Integer.parseInt(boots.replace("#", ""), 16)));
                  meta.setEnchantmentGlintOverride(true);
                  shoes.setItemMeta(meta);
                  mannequin.getEquipment().setBoots(shoes);
               } catch (IllegalArgumentException invalid) {
                  this.store.plugin().getSLF4JLogger().warn("bad boots-color {} for {}", boots, this.path);
               }
            }
         });
         float size = (float)this.cfg().getDouble(this.path + ".title-size", 2.0);
         this.title = world.spawn(at.clone().add(0.0, 2.0 * scale + 0.25, 0.0), TextDisplay.class, display -> {
            display.setBillboard(Billboard.CENTER);
            display.setAlignment(TextAlignment.CENTER);
            display.setShadowed(true);
            display.setPersistent(false);
            display.setLineWidth(1000);
            display.setBrightness(new Brightness(15, 15));
            display.addScoreboardTag(this.tag);
            display.text(MiniMessage.miniMessage().deserialize(this.cfg().getString(this.path + ".title", "<bold>STORE")));
            display.setTransformation(
               new Transformation(new Vector3f(), display.getTransformation().getLeftRotation(), new Vector3f(size, size, size), display.getTransformation().getRightRotation())
            );
         });
      }
   }

   void despawn() {
      for (Entity entity : new Entity[]{this.body, this.title}) {
         if (entity != null) {
            this.store.plugin().tasks().runOn(entity, () -> {
               if (entity.isValid()) {
                  entity.remove();
               }
            });
         }
      }

      this.body = null;
      this.title = null;
   }

   void despawnNow() {
      for (Entity entity : new Entity[]{this.body, this.title}) {
         if (entity != null && entity.isValid()) {
            entity.remove();
         }
      }

      this.body = null;
      this.title = null;
   }

   private void sweep() {
      if (!Tasks.FOLIA) {
         for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
               if (entity.getScoreboardTags().contains(this.tag)) {
                  entity.remove();
               }
            }
         }
      }
   }

   void place(Location at) {
      FileConfiguration cfg = this.cfg();
      cfg.set(this.path + ".location.world", at.getWorld().getName());
      cfg.set(this.path + ".location.x", at.getX());
      cfg.set(this.path + ".location.y", at.getY());
      cfg.set(this.path + ".location.z", at.getZ());
      cfg.set(this.path + ".location.yaw", at.getYaw());
      this.store.saveConfig();
      this.despawn();
      this.where = at.clone();
      this.spawn();
   }

   void remove() {
      this.despawn();
      this.sweep();
      this.where = null;
      this.cfg().set(this.path + ".location", null);
      this.store.saveConfig();
   }

   void refresh() {
      Location at = this.where;
      if (at != null && this.cfg().getBoolean(this.path + ".enabled", true) && this.missing()) {
         this.store.plugin().tasks().runAt(at, () -> {
            if (at == this.where && this.missing() && at.isChunkLoaded()) {
               this.spawnNow(at);
            }
         });
      }
   }

   private boolean missing() {
      Mannequin npc = this.body;
      TextDisplay label = this.title;
      return npc == null || !npc.isValid() || label == null || !label.isValid();
   }

   private boolean isNpc(Entity entity) {
      return entity != null && entity.getScoreboardTags().contains(this.tag);
   }

   private void click(Player player) {
      Sound chime = this.sound;
      if (chime != null) {
         player.playSound(player.getLocation(), chime, 0.7F, 1.4F);
      }

      this.action.accept(player);
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onRightClick(PlayerInteractEntityEvent event) {
      if (this.isNpc(event.getRightClicked())) {
         event.setCancelled(true);
         if (event.getHand() == EquipmentSlot.HAND) {
            this.click(event.getPlayer());
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onLeftClick(PrePlayerAttackEntityEvent event) {
      if (this.isNpc(event.getAttacked())) {
         event.setCancelled(true);
         this.click(event.getPlayer());
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onHurt(EntityDamageEvent event) {
      if (this.isNpc(event.getEntity())) {
         event.setCancelled(true);
      }
   }

   @EventHandler
   public void onChunk(ChunkLoadEvent event) {
      Location at = this.where;
      if (at != null && at.getWorld() == event.getWorld() && at.getBlockX() >> 4 == event.getChunk().getX() && at.getBlockZ() >> 4 == event.getChunk().getZ()) {
         this.store.plugin().tasks().region(at, this::refresh);
      }
   }
}
