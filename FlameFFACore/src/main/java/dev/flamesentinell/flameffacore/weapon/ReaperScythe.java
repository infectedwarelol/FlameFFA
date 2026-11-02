package dev.flamesentinell.flameffacore.weapon;

import dev.flamesentinell.flameffacore.FlameFFACore;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.key.Key;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public final class ReaperScythe implements CustomWeapon, Listener {
    public static final String ID = "reaper-scythe";
    private static final Particle.DustOptions BLOOD = new Particle.DustOptions(Color.fromRGB(150, 12, 24), 1.1F);
    private final FlameFFACore plugin;
    private boolean cleaving;

    public ReaperScythe(FlameFFACore plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return ID;
    }

    private FileConfiguration cfg() {
        return plugin.configs().main();
    }

    @Override
    public ItemStack build(FlameFFACore plugin) {
        ItemStack mace = ItemStack.of(Material.MACE);
        mace.addUnsafeEnchantment(Enchantment.BREACH, 4);
        mace.setData(DataComponentTypes.ITEM_MODEL, Key.key(cfg().getString(path() + ".model", "flameffa:reaper_scythe")));
        return mace;
    }

    @Override
    public List<String> check(ItemStack built) {
        List<String> bad = new ArrayList<>();
        if (built.getType() != Material.MACE) bad.add("reaper scythe is not a mace");
        if (built.getEnchantmentLevel(Enchantment.BREACH) != 4) bad.add("reaper scythe is missing Breach IV");
        return bad;
    }

    private boolean holding(Player p) {
        return ID.equals(plugin.weapons().weaponId(p.getInventory().getItemInMainHand()));
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (cleaving || event.getCause() != DamageCause.ENTITY_ATTACK) return;
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof LivingEntity victim)) return;
        if (!holding(attacker) || event.getFinalDamage() <= 0) return;
        if (attacker.getFallDistance() <= 1.5F || attacker.isGliding()) return;
        double heal = cfg().getDouble(path() + ".lifesteal-hearts", 4.0) * 2.0;
        double before = attacker.getHealth();
        attacker.heal(heal, RegainReason.CUSTOM);
        if (attacker.getHealth() <= before) return;
        World world = attacker.getWorld();
        Location from = victim.getLocation().add(0, victim.getHeight() * 0.6, 0);
        Location to = attacker.getLocation().add(0, attacker.getHeight() * 0.6, 0);
        Vector step = to.toVector().subtract(from.toVector());
        int n = Math.max(4, (int) (step.length() * 3));
        step.multiply(1.0 / n);
        Location at = from.clone();
        for (int i = 0; i <= n; i++) {
            world.spawnParticle(Particle.DUST, at, 1, 0.04, 0.04, 0.04, 0.0, BLOOD);
            at.add(step);
        }
        world.spawnParticle(Particle.SOUL, from, 8, 0.25, 0.3, 0.25, 0.02);
        world.spawnParticle(Particle.HEART, to.clone().add(0, 0.8, 0), 3, 0.3, 0.2, 0.3, 0.0);
        world.playSound(victim.getLocation(), Sound.ENTITY_PHANTOM_BITE, 1.0F, 0.6F);
        world.playSound(attacker.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.0F, 1.2F);
    }


    private boolean hittable(LivingEntity e, Player owner) {
        if (e == owner || !e.isValid() || e.isDead() || e instanceof ArmorStand || e.isInvulnerable()) return false;
        if (e.getScoreboardTags().contains("flameffacore_ghost")) return false;
        if (e instanceof Player p && (p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.CREATIVE)) return false;
        return !plugin.safezone().contains(e.getLocation());
    }

        static double rawFor(LivingEntity e, double want, int breach) {
        double armor = attr(e, org.bukkit.attribute.Attribute.ARMOR), tough = attr(e, org.bukkit.attribute.Attribute.ARMOR_TOUGHNESS);
        int epf = 0;
        org.bukkit.inventory.EntityEquipment eq = e.getEquipment();
        if (eq != null) {
            for (ItemStack it : eq.getArmorContents()) {
                if (it != null) epf += it.getEnchantmentLevel(Enchantment.PROTECTION);
            }
        }
        double prot = 1.0 - Math.min(20, epf) / 25.0;
        org.bukkit.potion.PotionEffect res = e.getPotionEffect(org.bukkit.potion.PotionEffectType.RESISTANCE);
        double resist = res == null ? 1.0 : Math.max(0.0, 1.0 - 0.2 * (res.getAmplifier() + 1));
        if (resist <= 0.0) return 0.0;
        double lo = 0.0, hi = 400.0;
        for (int i = 0; i < 60; i++) {
            double mid = (lo + hi) / 2.0;
            if (after(mid, armor, tough, breach) * prot * resist < want) lo = mid; else hi = mid;
        }
        return hi;
    }

    private static double after(double d, double armor, double tough, int breach) {
        double g = Math.max(armor * 0.2, Math.min(20.0, armor - d / (2.0 + tough / 4.0)));
        double h = Math.max(0.0, Math.min(1.0, g / 25.0 - 0.15 * breach));
        return d * (1.0 - h);
    }

    private static double attr(LivingEntity e, org.bukkit.attribute.Attribute a) {
        org.bukkit.attribute.AttributeInstance i = e.getAttribute(a);
        return i == null ? 0.0 : i.getValue();
    }

    @Override
    public boolean onAbility(FlameFFACore plugin, Player player, ItemStack held) {
        if (plugin.safezone().contains(player.getLocation())) return false;
        FileConfiguration c = cfg();
        double range = c.getDouble(path() + ".range", 4.0);
        double half = Math.toRadians(c.getDouble(path() + ".arc-degrees", 120.0) / 2.0);
        double hearts = c.getDouble(path() + ".cleave-hearts", 4.0);
        double push = c.getDouble(path() + ".cleave-knockback", 0.6);
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection().setY(0);
        if (look.lengthSquared() < 1.0E-4) look = new Vector(-Math.sin(Math.toRadians(eye.getYaw())), 0, Math.cos(Math.toRadians(eye.getYaw())));
        look.normalize();
        List<LivingEntity> hit = new ArrayList<>();
        for (Entity e : player.getWorld().getNearbyEntities(eye, range + 1.5, range + 1.5, range + 1.5)) {
            if (!(e instanceof LivingEntity le) || !hittable(le, player)) continue;
            Location mid = le.getLocation().add(0, le.getHeight() * 0.5, 0);
            Vector to = mid.toVector().subtract(eye.toVector());
            double dist = to.length();
            if (dist - le.getWidth() * 0.5 > range || Math.abs(to.getY()) > 2.5) continue;
            Vector flat = to.clone().setY(0);
            if (flat.lengthSquared() > 0.04 && flat.normalize().angle(look) > half) continue;
            if (!player.hasLineOfSight(le)) continue;
            hit.add(le);
        }
        DamageSource src = DamageSource.builder(DamageType.PLAYER_ATTACK).withCausingEntity(player).withDirectEntity(player).build();
        cleaving = true;
        try {
            for (LivingEntity le : hit) {
                if (le instanceof Player p) plugin.antiCheat().open(p, 40);
                le.damage(rawFor(le, hearts * 2.0, player.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.BREACH)), src);
                Vector kb = le.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
                if (kb.lengthSquared() > 1.0E-4) le.setVelocity(le.getVelocity().add(kb.normalize().multiply(push).setY(0.25)));
            }
        } finally {
            cleaving = false;
        }
        player.swingMainHand();
        World world = player.getWorld();
        Location base = player.getLocation().add(0, 1.1, 0);
        for (int i = -3; i <= 3; i++) {
            Vector dir = look.clone().rotateAroundY(half * i / 3.0);
            Location p = base.clone().add(dir.clone().multiply(range * 0.7));
            world.spawnParticle(Particle.SWEEP_ATTACK, p, 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, base.clone().add(dir.multiply(range * 0.9)), 2, 0.05, 0.1, 0.05, 0.01);
        }
        world.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0F, 0.6F);
        world.playSound(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.35F, 1.7F);
        return true;
    }
}
