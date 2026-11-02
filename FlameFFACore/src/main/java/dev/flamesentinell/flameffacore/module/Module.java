package dev.flamesentinell.flameffacore.module;

import io.papermc.paper.command.brigadier.Commands;

public interface Module {
   String id();

   void enable();

   void disable();

   default void commands(Commands commands) {
   }
}
