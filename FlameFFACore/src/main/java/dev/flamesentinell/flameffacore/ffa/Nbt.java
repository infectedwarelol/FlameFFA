package dev.flamesentinell.flameffacore.ffa;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

final class Nbt {
   private Nbt() {
   }

   static Map<String, Object> readGzip(byte[] var0) throws IOException {
      Map var4;
      try (DataInputStream var1 = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(var0)))) {
         byte var2 = var1.readByte();
         if (var2 != 10) {
            throw new IOException("root is not a compound");
         }

         var1.readUTF();
         Map var3 = (Map)read(var1, (byte)10);
         var4 = var3;
      }

      return var4;
   }

   static byte[] writeGzip(Map<String, Object> var0) throws IOException {
      ByteArrayOutputStream var1 = new ByteArrayOutputStream();

      try (DataOutputStream var2 = new DataOutputStream(new GZIPOutputStream(var1))) {
         var2.writeByte(10);
         var2.writeUTF("");
         write(var2, var0);
      }

      return var1.toByteArray();
   }

   private static Object read(DataInputStream var0, byte var1) throws IOException {
      switch (var1) {
         case 1:
            return var0.readByte();
         case 2:
            return var0.readShort();
         case 3:
            return var0.readInt();
         case 4:
            return var0.readLong();
         case 5:
            return var0.readFloat();
         case 6:
            return var0.readDouble();
         case 7:
            byte[] var9 = new byte[var0.readInt()];
            var0.readFully(var9);
            return var9;
         case 8:
            return var0.readUTF();
         case 9:
            byte var8 = var0.readByte();
            int var12 = var0.readInt();
            Nbt.Tags var13 = new Nbt.Tags(var8);

            for (int var5 = 0; var5 < var12; var5++) {
               var13.add(read(var0, var8));
            }

            return var13;
         case 10:
            LinkedHashMap var7 = new LinkedHashMap();

            while (true) {
               byte var11 = var0.readByte();
               if (var11 == 0) {
                  return var7;
               }

               String var4 = var0.readUTF();
               var7.put(var4, read(var0, var11));
            }
         case 11:
            int[] var6 = new int[var0.readInt()];

            for (int var10 = 0; var10 < var6.length; var10++) {
               var6[var10] = var0.readInt();
            }

            return var6;
         case 12:
            long[] var2 = new long[var0.readInt()];

            for (int var3 = 0; var3 < var2.length; var3++) {
               var2[var3] = var0.readLong();
            }

            return var2;
         default:
            throw new IOException("bad tag " + var1);
      }
   }

   static byte type(Object var0) {
      if (var0 instanceof Byte) {
         return 1;
      } else if (var0 instanceof Short) {
         return 2;
      } else if (var0 instanceof Integer) {
         return 3;
      } else if (var0 instanceof Long) {
         return 4;
      } else if (var0 instanceof Float) {
         return 5;
      } else if (var0 instanceof Double) {
         return 6;
      } else if (var0 instanceof byte[]) {
         return 7;
      } else if (var0 instanceof String) {
         return 8;
      } else if (var0 instanceof Nbt.Tags) {
         return 9;
      } else if (var0 instanceof Map) {
         return 10;
      } else if (var0 instanceof int[]) {
         return 11;
      } else if (var0 instanceof long[]) {
         return 12;
      } else {
         throw new IllegalArgumentException("not an nbt value " + var0);
      }
   }

   private static void write(DataOutputStream var0, Object var1) throws IOException {
      switch (type(var1)) {
         case 1:
            var0.writeByte((Byte)var1);
            break;
         case 2:
            var0.writeShort((Short)var1);
            break;
         case 3:
            var0.writeInt((Integer)var1);
            break;
         case 4:
            var0.writeLong((Long)var1);
            break;
         case 5:
            var0.writeFloat((Float)var1);
            break;
         case 6:
            var0.writeDouble((Double)var1);
            break;
         case 7:
            byte[] var11 = (byte[])var1;
            var0.writeInt(var11.length);
            var0.write(var11);
            break;
         case 8:
            var0.writeUTF((String)var1);
            break;
         case 9:
            Nbt.Tags var10 = (Nbt.Tags)var1;
            byte var14 = var10.isEmpty() ? var10.type : type(var10.get(0));
            var0.writeByte(var10.isEmpty() && var14 == 0 ? 0 : var14);
            var0.writeInt(var10.size());

            for (Object var18 : var10) {
               write(var0, var18);
            }
            break;
         case 10:
            for (Entry<?, ?> var13 : ((Map<?, ?>)var1).entrySet()) {
               var0.writeByte(type(var13.getValue()));
               var0.writeUTF((String)var13.getKey());
               write(var0, var13.getValue());
            }

            var0.writeByte(0);
            break;
         case 11:
            int[] var8 = (int[])var1;
            var0.writeInt(var8.length);

            for (int var19 : var8) {
               var0.writeInt(var19);
            }
            break;
         case 12:
            long[] var2 = (long[])var1;
            var0.writeInt(var2.length);

            for (long var6 : var2) {
               var0.writeLong(var6);
            }
            break;
         default:
            throw new IOException("bad value");
      }
   }

   static List<Object> list(Object var0) {
      return var0 instanceof Nbt.Tags var1 ? var1 : null;
   }

   static final class Tags extends ArrayList<Object> {
      byte type;

      Tags(byte var1) {
         this.type = var1;
      }
   }
}
