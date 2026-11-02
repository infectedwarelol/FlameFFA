package dev.flamesentinell.flameffacore.villager;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;

public final class SkinFetcher {
   private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
   private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8L)).build();

   private SkinFetcher() {
   }

   public static SkinFetcher.Skin fetch(String name) {
      if (name == null || !NAME.matcher(name).matches()) {
         return null;
      }

      try {
         String head = get("https://api.mojang.com/users/profiles/minecraft/" + name);
         String id = find(head, "\"id\"\\s*:\\s*\"([0-9a-fA-F]{32})\"");
         String real = find(head, "\"name\"\\s*:\\s*\"([^\"]+)\"");
         if (id == null) {
            return null;
         } else {
            UUID uuid = UUID.fromString(id.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5"));
            String body = get("https://sessionserver.mojang.com/session/minecraft/profile/" + id + "?unsigned=false");
            String value = find(body, "\"value\"\\s*:\\s*\"([A-Za-z0-9+/=]+)\"");
            String signature = find(body, "\"signature\"\\s*:\\s*\"([A-Za-z0-9+/=]+)\"");
            return value == null ? null : new SkinFetcher.Skin(uuid, real == null ? name : real, value, signature);
         }
      } catch (Exception var8) {
         return null;
      }
   }

   public static ResolvableProfile profile(SkinFetcher.Skin skin) {
      PlayerProfile p = Bukkit.createProfile(skin.uuid(), skin.name());
      p.setProperty(new ProfileProperty("textures", skin.value(), skin.signature()));
      return ResolvableProfile.resolvableProfile(p);
   }

   private static String get(String url) throws Exception {
      HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8L)).header("accept", "application/json").GET().build();
      HttpResponse<String> response = CLIENT.send(request, BodyHandlers.ofString());
      return response.statusCode() == 200 ? response.body() : "";
   }

   private static String find(String body, String pattern) {
      Matcher m = Pattern.compile(pattern).matcher(body);
      return m.find() ? m.group(1) : null;
   }

   public record Skin(UUID uuid, String name, String value, String signature) {
   }
}
