package dev.flamesentinell.flameffacore.chat;

import java.util.concurrent.ConcurrentHashMap;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ChatFilter {
   private final Set<String> banned = ConcurrentHashMap.newKeySet();
   private final Set<String> allowed = ConcurrentHashMap.newKeySet();

   public void load(List<String> bannedRaw, List<String> allowedRaw) {
      this.banned.clear();
      this.allowed.clear();

      for (String term : bannedRaw) {
         String folded = fold(term);
         if (folded.length() >= 3) {
            this.banned.add(folded);
         }
      }

      for (String termx : allowedRaw) {
         String folded = fold(termx);
         if (!folded.isEmpty()) {
            this.allowed.add(folded);
         }
      }
   }

   public int size() {
      return this.banned.size();
   }

   public String scan(String message) {
      if (!this.banned.isEmpty() && message != null && !message.isEmpty()) {
         List<String> words = new ArrayList<>();
         StringBuilder joined = new StringBuilder();

         for (String raw : message.split("\\s+")) {
            String folded = fold(raw);
            if (!folded.isEmpty()) {
               words.add(folded);
               joined.append(folded);
            }
         }

         for (String word : words) {
            if (!this.allowed.contains(word)) {
               String hit = this.find(word, 5);
               if (hit != null) {
                  return hit;
               }
            }
         }

         String all = fold(joined.toString());

         for (String safe : this.allowed) {
            all = all.replace(safe, "");
         }

         return this.find(all, 6);
      } else {
         return null;
      }
   }

   private String find(String folded, int insideFrom) {
      for (String term : this.banned) {
         if (folded.equals(term) || term.length() >= insideFrom && folded.contains(term)) {
            return term;
         }
      }

      return null;
   }

   public static String fold(String raw) {
      String s = raw.toLowerCase(Locale.ROOT);
      s = s.replace("§", "");
      s = Normalizer.normalize(s, Form.NFKD);
      StringBuilder letters = new StringBuilder(s.length());

      for (int i = 0; i < s.length(); i++) {
         char c = s.charAt(i);
         if (Character.getType(c) != 6) {
            char mapped = lookalike(c);
            if (mapped >= 'a' && mapped <= 'z') {
               letters.append(mapped);
            }
         }
      }

      String merged = letters.toString().replace("vv", "w").replace("rn", "m").replace("ph", "f");
      StringBuilder same = new StringBuilder(merged.length());

      for (int ix = 0; ix < merged.length(); ix++) {
         char c = merged.charAt(ix);
         same.append(c == 'l' ? 'i' : (c == 'z' ? 's' : c));
      }

      letters = same;
      StringBuilder out = new StringBuilder(same.length());
      char last = 0;

      for (int ix = 0; ix < letters.length(); ix++) {
         char c = letters.charAt(ix);
         if (c != last) {
            out.append(c);
            last = c;
         }
      }

      return out.toString();
   }

   private static char lookalike(char c) {
      switch (c) {
         case '!':
         case '1':
         case '|':
         case '¡':
         case 'í':
         case 'ı':
         case 'і':
         case 'ї':
            return 'i';
         case '#':
         case 'η':
         case 'н':
            return 'h';
         case '$':
         case '5':
         case 'ș':
         case 'ѕ':
            return 's';
         case '(':
         case '<':
         case '[':
         case '{':
         case 'ç':
         case 'ć':
         case 'с':
            return 'c';
         case '*':
            return 'x';
         case '+':
         case '7':
         case 'τ':
         case 'т':
            return 't';
         case '0':
         case 'ø':
         case 'θ':
         case 'ο':
         case 'σ':
         case 'о':
         case 'ө':
            return 'o';
         case '2':
         case 'ᴢ':
            return 'z';
         case '3':
         case 'ε':
         case 'е':
         case 'э':
         case 'ё':
         case '€':
            return 'e';
         case '4':
         case '@':
         case 'ä':
         case 'α':
         case 'λ':
         case 'а':
            return 'a';
         case '6':
            return 'b';
         case '8':
            return 'b';
         case '9':
         case 'g':
         case 'ɢ':
            return 'g';
         case 'ł':
         case 'ʟ':
            return 'l';
         case 'ʀ':
         case 'г':
            return 'r';
         case 'β':
         case 'в':
            return 'b';
         case 'γ':
         case 'у':
         case 'ү':
            return 'y';
         case 'δ':
         case 'ԁ':
            return 'd';
         case 'κ':
         case 'к':
            return 'k';
         case 'μ':
         case 'м':
            return 'm';
         case 'ρ':
         case 'р':
            return 'p';
         case 'υ':
         case 'ц':
         case 'ᴜ':
            return 'u';
         case 'χ':
         case 'х':
            return 'x';
         case 'п':
            return 'n';
         case 'ф':
         case 'ғ':
            return 'f';
         case 'ո':
            return 'n';
         default:
            return c;
      }
   }
}
