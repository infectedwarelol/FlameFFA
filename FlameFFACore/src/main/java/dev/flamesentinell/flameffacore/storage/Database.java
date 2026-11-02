package dev.flamesentinell.flameffacore.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.flamesentinell.flameffacore.FlameFFACore;
import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;

public final class Database {
   private final FlameFFACore plugin;
   private HikariDataSource source;
   private boolean embedded;

   public Database(FlameFFACore plugin) {
      this.plugin = plugin;
   }

   public void connect() {
      ConfigurationSection s = this.plugin.configs().main().getConfigurationSection("storage");
      if (s == null) {
         throw new IllegalStateException("storage section missing from config.yml");
      } else {
         String type = s.getString("type", "h2").toLowerCase(Locale.ROOT);
         this.embedded = type.equals("h2") || type.equals("file") || type.equals("embedded");
         String driver;
         String url;
         String user;
         String pass;
         if (this.embedded) {
            driver = "org.h2.Driver";
            File file = new File(this.plugin.getDataFolder(), s.getString("file", "data"));
            url = "jdbc:h2:file:" + file.getAbsolutePath() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE";
            user = "sa";
            pass = "";
         } else {
            driver = s.getString("driver", "org.mariadb.jdbc.Driver");
            url = "jdbc:mariadb://"
               + s.getString("host", "127.0.0.1")
               + ":"
               + s.getInt("port", 3306)
               + "/"
               + s.getString("database", "flameffacore")
               + "?"
               + s.getString("properties", "");
            user = s.getString("username", "root");
            pass = s.getString("password", "");
         }

         try {
            Class.forName(driver, true, this.getClass().getClassLoader());
         } catch (ClassNotFoundException var8) {
            throw new IllegalStateException("jdbc driver " + driver + " is not on the classpath", var8);
         }

         HikariConfig cfg = new HikariConfig();
         cfg.setPoolName("FlameFFACore");
         cfg.setDriverClassName(driver);
         cfg.setJdbcUrl(url);
         cfg.setUsername(user);
         cfg.setPassword(pass);
         cfg.setMaximumPoolSize(this.embedded ? 4 : s.getInt("pool-size", 10));
         cfg.setMinimumIdle(2);
         cfg.setConnectionTimeout(10000L);
         cfg.setIdleTimeout(300000L);
         cfg.setMaxLifetime(1800000L);
         cfg.setLeakDetectionThreshold(30000L);
         if (!this.embedded) {
            cfg.addDataSourceProperty("cachePrepStmts", "true");
            cfg.addDataSourceProperty("prepStmtCacheSize", "250");
         }

         this.source = new HikariDataSource(cfg);
         Schema.apply(this);
         this.plugin.getSLF4JLogger().info("database ready ({})", this.embedded ? "embedded h2 file" : "mariadb");
      }
   }

   public boolean embedded() {
      return this.embedded;
   }

   public Connection connection() throws SQLException {
      if (this.source == null) {
         throw new SQLException("database not connected");
      } else {
         return this.source.getConnection();
      }
   }

   public void close() {
      if (this.source != null) {
         this.source.close();
         this.source = null;
      }
   }
}
