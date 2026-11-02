package dev.flamesentinell.flameffacore.module;

import dev.flamesentinell.flameffacore.FlameFFACore;
import io.papermc.paper.command.brigadier.Commands;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ModuleManager {
   private final FlameFFACore plugin;
   private final List<Module> modules = new ArrayList<>();
   private final List<Module> active = new CopyOnWriteArrayList<>();

   public ModuleManager(FlameFFACore plugin) {
      this.plugin = plugin;
   }

   public void add(Module module) {
      this.modules.add(module);
   }

   public boolean configured(String id) {
      return this.plugin.configs().main().getBoolean("modules." + id + ".enabled", true);
   }

   public boolean active(String id) {
      for (Module module : this.active) {
         if (module.id().equals(id)) {
            return true;
         }
      }

      return false;
   }

   public void enableAll() {
      for (Module module : this.modules) {
         if (!this.configured(module.id())) {
            this.plugin.getSLF4JLogger().info("module {} is turned off in config.yml", module.id());
         } else {
            try {
               module.enable();
               this.active.add(module);
               this.plugin.getSLF4JLogger().info("module {} enabled", module.id());
            } catch (RuntimeException failure) {
               this.plugin.getSLF4JLogger().error("module {} failed to enable, skipping it", module.id(), failure);
               try {
                  module.disable();
               } catch (RuntimeException cleanup) {
                  this.plugin.getSLF4JLogger().warn("module {} also failed to clean up", module.id(), cleanup);
               }
            }
         }
      }
   }

   public void disableAll() {
      List<Module> running = new ArrayList<>(this.active);

      for (int index = running.size() - 1; index >= 0; index--) {
         Module module = running.get(index);
         try {
            module.disable();
         } catch (RuntimeException failure) {
            this.plugin.getSLF4JLogger().error("module {} failed to disable cleanly", module.id(), failure);
         }
      }

      this.active.clear();
   }

   public void registerCommands(Commands commands) {
      for (Module module : this.active) {
         try {
            module.commands(commands);
         } catch (RuntimeException failure) {
            this.plugin.getSLF4JLogger().error("module {} could not register its commands", module.id(), failure);
         }
      }
   }
}
