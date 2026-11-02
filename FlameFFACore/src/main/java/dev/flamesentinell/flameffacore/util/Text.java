package dev.flamesentinell.flameffacore.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver.Builder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class Text {
   private static final MiniMessage MM = MiniMessage.miniMessage();
   private static Component prefix = Component.empty();

   private Text() {
   }

   public static void setPrefix(String var0) {
      prefix = MM.deserialize(var0);
   }

   public static Component parse(String var0, String var1, Component var2, String... var3) {
      Builder var4 = TagResolver.builder();
      var4.resolver(Placeholder.component("prefix", prefix));
      var4.resolver(Placeholder.component(var1, var2));

      for (byte var5 = 0; var5 + 1 < var3.length; var5 += 2) {
         var4.resolver(Placeholder.unparsed(var3[var5], var3[var5 + 1] == null ? "" : var3[var5 + 1]));
      }

      return MM.deserialize(var0, var4.build());
   }

   public static Component parse(String var0, String... var1) {
      Builder var2 = TagResolver.builder();
      var2.resolver(Placeholder.component("prefix", prefix));

      for (byte var3 = 0; var3 + 1 < var1.length; var3 += 2) {
         var2.resolver(Placeholder.unparsed(var1[var3], var1[var3 + 1] == null ? "" : var1[var3 + 1]));
      }

      return MM.deserialize(var0, var2.build());
   }

   public static List<Component> parseAll(List<String> var0, String... var1) {
      ArrayList var2 = new ArrayList(var0.size());

      for (String var4 : var0) {
         var2.add(parse(var4, var1));
      }

      return var2;
   }

   public static String plain(Component var0) {
      return PlainTextComponentSerializer.plainText().serialize(var0);
   }

   public static Component tag(String var0) {
      Component var1 = MM.deserialize(var0);
      String var2 = plain(var1).trim().toUpperCase(Locale.ROOT);
      if (var2.isEmpty()) {
         return Component.empty();
      } else if (!var0.contains("<gradient") && !var0.contains("<rainbow")) {
         TextColor var5 = firstColor(var1);
         return Component.text("[" + var2 + "]", (TextColor)(var5 == null ? NamedTextColor.GRAY : var5));
      } else {
         Matcher var3 = Pattern.compile("^((?:<[^/>][^>]*>)*)(.*?)((?:</[^>]*>)*)$", 32).matcher(var0.trim());
         if (var3.matches()) {
            return MM.deserialize(var3.group(1) + "[" + var2 + "]" + var3.group(3));
         } else {
            TextColor var4 = firstColor(var1);
            return Component.text("[" + var2 + "]", (TextColor)(var4 == null ? NamedTextColor.GRAY : var4));
         }
      }
   }

   private static TextColor firstColor(Component var0) {
      if (var0.color() != null) {
         return var0.color();
      } else {
         for (Component var2 : var0.children()) {
            TextColor var3 = firstColor(var2);
            if (var3 != null) {
               return var3;
            }
         }

         return null;
      }
   }
}
