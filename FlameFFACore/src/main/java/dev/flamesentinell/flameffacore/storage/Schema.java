package dev.flamesentinell.flameffacore.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

final class Schema {
   private static final String ENGINE = "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
   private static final String[] TABLES = new String[]{
      "CREATE TABLE IF NOT EXISTS ffa_players (\n  uuid        BINARY(16)  NOT NULL PRIMARY KEY,\n  name        VARCHAR(16) NOT NULL,\n  first_join  BIGINT      NOT NULL,\n  last_join   BIGINT      NOT NULL,\n  kills       INT         NOT NULL DEFAULT 0,\n  deaths      INT         NOT NULL DEFAULT 0,\n  best_streak INT         NOT NULL DEFAULT 0,\n  last_kit    VARCHAR(32) NULL,\n  INDEX idx_players_name (name)\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4\n",
      "CREATE TABLE IF NOT EXISTS ffa_keys (\n  uuid     BINARY(16)  NOT NULL,\n  crate_id VARCHAR(32) NOT NULL,\n  amount   INT         NOT NULL DEFAULT 0,\n  PRIMARY KEY (uuid, crate_id)\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4\n",
      "CREATE TABLE IF NOT EXISTS ffa_key_ledger (\n  id            BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,\n  uuid          BINARY(16)  NOT NULL,\n  crate_id      VARCHAR(32) NOT NULL,\n  delta         INT         NOT NULL,\n  balance_after INT         NOT NULL,\n  reason        VARCHAR(32) NOT NULL,\n  actor         VARCHAR(64) NOT NULL,\n  txn           VARCHAR(64) NULL,\n  created_at    BIGINT      NOT NULL,\n  UNIQUE KEY uq_ledger_txn (txn),\n  INDEX idx_ledger_uuid (uuid, created_at)\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4\n",
      "CREATE TABLE IF NOT EXISTS ffa_txn (\n  txn        VARCHAR(64)  NOT NULL PRIMARY KEY,\n  kind       VARCHAR(24)  NOT NULL,\n  target     BINARY(16)   NULL,\n  payload    VARCHAR(255) NOT NULL,\n  created_at BIGINT       NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4\n",
      "CREATE TABLE IF NOT EXISTS ffa_pending (\n  id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,\n  uuid         BINARY(16)   NOT NULL,\n  kind         VARCHAR(24)  NOT NULL,\n  payload      VARCHAR(255) NOT NULL,\n  created_at   BIGINT       NOT NULL,\n  delivered_at BIGINT       NULL,\n  INDEX idx_pending_open (uuid, delivered_at)\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4\n",
      "CREATE TABLE IF NOT EXISTS ffa_staff_log (\n  id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,\n  actor      BINARY(16)   NULL,\n  actor_name VARCHAR(48)  NOT NULL,\n  action     VARCHAR(32)  NOT NULL,\n  target     BINARY(16)   NULL,\n  target_name VARCHAR(48) NULL,\n  detail     VARCHAR(255) NOT NULL,\n  created_at BIGINT       NOT NULL,\n  INDEX idx_staff_created (created_at),\n  INDEX idx_staff_actor (actor)\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4\n",
      "CREATE TABLE IF NOT EXISTS ffa_blobs (\n  kind     VARCHAR(16) NOT NULL,\n  blob_id  VARCHAR(32) NOT NULL,\n  payload  MEDIUMTEXT  NOT NULL,\n  set_by   VARCHAR(48) NOT NULL,\n  set_at   BIGINT      NOT NULL,\n  PRIMARY KEY (kind, blob_id)\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4\n",
      "CREATE TABLE IF NOT EXISTS ffa_crate_blocks (\n  world     VARCHAR(64) NOT NULL,\n  x         INT         NOT NULL,\n  y         INT         NOT NULL,\n  z         INT         NOT NULL,\n  crate_id  VARCHAR(32) NOT NULL,\n  placed_by VARCHAR(48) NOT NULL,\n  placed_at BIGINT      NOT NULL,\n  PRIMARY KEY (world, x, y, z),\n  INDEX idx_crate_blocks_id (crate_id)\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4\n",
      "CREATE TABLE IF NOT EXISTS ffa_villagers (\n  id        BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,\n  entity    BINARY(16)  NULL,\n  preset    VARCHAR(32) NOT NULL,\n  world     VARCHAR(64) NOT NULL,\n  x         DOUBLE      NOT NULL,\n  y         DOUBLE      NOT NULL,\n  z         DOUBLE      NOT NULL,\n  yaw       FLOAT       NOT NULL DEFAULT 0,\n  placed_by VARCHAR(48) NOT NULL,\n  placed_at BIGINT      NOT NULL,\n  INDEX idx_villagers_preset (preset)\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4\n",
      "CREATE TABLE IF NOT EXISTS ffa_cosmetics (\n  uuid   BINARY(16)  NOT NULL,\n  slot   VARCHAR(16) NOT NULL,\n  choice VARCHAR(48) NOT NULL,\n  PRIMARY KEY (uuid, slot)\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4\n",
      "CREATE TABLE IF NOT EXISTS ffa_kit_cooldowns (\n  uuid    BINARY(16)  NOT NULL,\n  rank_id VARCHAR(32) NOT NULL,\n  until   BIGINT      NOT NULL,\n  PRIMARY KEY (uuid, rank_id)\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4\n"
   };
   private static final String[] MIGRATIONS = new String[]{
      "ALTER TABLE ffa_staff_log MODIFY actor_name VARCHAR(48) NOT NULL",
      "ALTER TABLE ffa_staff_log MODIFY target_name VARCHAR(48) NULL",
      "ALTER TABLE ffa_players ADD COLUMN playtime BIGINT NOT NULL DEFAULT 0",
      "ALTER TABLE ffa_villagers ADD COLUMN skin_uuid BINARY(16) NULL",
      "ALTER TABLE ffa_villagers ADD COLUMN skin_name VARCHAR(16) NULL",
      "ALTER TABLE ffa_villagers ADD COLUMN skin_value VARCHAR(2048) NULL",
      "ALTER TABLE ffa_villagers ADD COLUMN skin_sig VARCHAR(1024) NULL"
   };

   private Schema() {
   }

   static void apply(Database db) {
      try {
         try (
            Connection c = db.connection();
            Statement st = c.createStatement();
         ) {
            for (String ddl : TABLES) {
               st.executeUpdate(db.embedded() ? ddl.replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", "") : ddl);
            }

            for (String ddl : MIGRATIONS) {
               try {
                  st.executeUpdate(ddl);
               } catch (SQLException var10) {
               }
            }
         }
      } catch (SQLException var13) {
         throw new IllegalStateException("schema migration failed", var13);
      }
   }
}
