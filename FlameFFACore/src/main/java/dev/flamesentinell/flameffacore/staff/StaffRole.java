package dev.flamesentinell.flameffacore.staff;

public enum StaffRole {
   NONE("none", 0),
   HELPER("helper", 10),
   SCREENSHARER("screensharer", 20),
   MODERATOR("mod", 30),
   ADMIN("admin", 40),
   OWNER("owner", 100);

   private final String id;
   private final int weight;

   private StaffRole(String id, int weight) {
      this.id = id;
      this.weight = weight;
   }

   public String id() {
      return this.id;
   }

   public int weight() {
      return this.weight;
   }

   public String permission() {
      return "flameffacore.staff." + this.id;
   }

   public boolean atLeast(StaffRole other) {
      return this.weight >= other.weight;
   }

   public static StaffRole byId(String id) {
      for (StaffRole role : values()) {
         if (role.id.equalsIgnoreCase(id)) {
            return role;
         }
      }

      return NONE;
   }
}
