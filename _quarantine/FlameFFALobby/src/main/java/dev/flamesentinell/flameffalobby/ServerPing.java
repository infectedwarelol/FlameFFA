package dev.flamesentinell.flameffalobby;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class ServerPing {
   private ServerPing() {
   }

   public static int online(String host, int port, int timeoutMillis) {
      try {
         int var14;
         try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMillis);
            socket.setSoTimeout(timeoutMillis);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());
            ByteArrayOutputStream hs = new ByteArrayOutputStream();
            DataOutputStream h = new DataOutputStream(hs);
            h.writeByte(0);
            writeVarInt(h, -1);
            byte[] hostBytes = host.getBytes(StandardCharsets.UTF_8);
            writeVarInt(h, hostBytes.length);
            h.write(hostBytes);
            h.writeShort(port);
            writeVarInt(h, 1);
            writeVarInt(out, hs.size());
            out.write(hs.toByteArray());
            writeVarInt(out, 1);
            out.writeByte(0);
            out.flush();
            readVarInt(in);
            int id = readVarInt(in);
            if (id != 0) {
               return -1;
            }

            int length = readVarInt(in);
            if (length <= 0 || length > 1048576) {
               return -1;
            }

            byte[] json = new byte[length];
            in.readFully(json);
            JsonObject root = JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject players = root.getAsJsonObject("players");
            var14 = players == null ? 0 : players.get("online").getAsInt();
         }

         return var14;
      } catch (RuntimeException | IOException var17) {
         return -1;
      }
   }

   private static void writeVarInt(DataOutputStream out, int value) throws IOException {
      while ((value & -128) != 0) {
         out.writeByte(value & 127 | 128);
         value >>>= 7;
      }

      out.writeByte(value);
   }

   private static int readVarInt(DataInputStream in) throws IOException {
      int value = 0;
      int shift = 0;

      do {
         byte b = in.readByte();
         value |= (b & 127) << shift;
         if ((b & 128) == 0) {
            return value;
         }

         shift += 7;
      } while (shift <= 35);

      throw new IOException("varint too long");
   }
}
