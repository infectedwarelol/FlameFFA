package dev.flamesentinell.flameffacore.module;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Collection;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public final class ModuleCommand implements BasicCommand {
   private static final Component NO_PERMISSION = Component.text(
      "I'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.",
      NamedTextColor.RED
   );
   private final String name;
   private final String permission;
   private final String usage;
   private final ModuleCommand.Executor executor;
   private final ModuleCommand.Completer completer;

   private ModuleCommand(String name, String permission, String usage, ModuleCommand.Executor executor, ModuleCommand.Completer completer) {
      this.name = name;
      this.permission = permission;
      this.usage = usage;
      this.executor = executor;
      this.completer = completer;
   }

   public static void register(
      Commands commands,
      String name,
      String description,
      List<String> aliases,
      String permission,
      String usage,
      ModuleCommand.Executor executor,
      ModuleCommand.Completer completer
   ) {
      commands.register(name, description, aliases, new ModuleCommand(name, permission, usage, executor, completer));
   }

   public void execute(@NotNull CommandSourceStack source, @NotNull String[] args) {
      CommandSender sender = source.getSender();
      if (this.permission != null && !sender.hasPermission(this.permission)) {
         sender.sendMessage(NO_PERMISSION);
      } else if (!this.executor.run(sender, this.name, args) && this.usage != null) {
         sender.sendMessage(Component.text(this.usage.replace("<command>", this.name)));
      }
   }

   public @NotNull Collection<String> suggest(@NotNull CommandSourceStack source, @NotNull String[] args) {
      if (this.completer == null || this.permission != null && !source.getSender().hasPermission(this.permission)) {
         return List.of();
      } else {
         List<String> found = this.completer.complete(source.getSender(), args.length == 0 ? new String[]{""} : args);
         return found == null ? List.of() : found;
      }
   }

   public String permission() {
      return this.permission;
   }

   @FunctionalInterface
   public interface Completer {
      List<String> complete(CommandSender sender, String[] args);
   }

   @FunctionalInterface
   public interface Executor {
      boolean run(CommandSender sender, String label, String[] args);
   }
}
