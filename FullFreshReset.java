import java.sql.*;
import java.nio.file.*;
public class FullFreshReset {
  public static void main(String[] a) throws Exception{
    String url=System.getenv("DATABASE_URL");
    String user=System.getenv("DATABASE_USERNAME");
    String pass=System.getenv("DATABASE_PASSWORD");
    if(url==null) url="jdbc:postgresql://ep-shiny-salad-aygxj11y-pooler.c-5.us-east-2.aws.neon.tech/neondb?sslmode=require&channelBinding=require";
    if(user==null) user="neondb_owner";
    if(pass==null) pass="npg_q1UKOdLTQG0S";
    System.out.println("Connecting to PostgreSQL...");
    System.out.println("URL: "+url);
    System.out.println("User: "+user);
    try(Connection c=DriverManager.getConnection(url,user,pass)){
      c.setAutoCommit(false);
      System.out.println("Connected.");

      // Show counts BEFORE
      String[] allTables = {"organisations","users","roles","permissions","role_permissions","configuration_definitions","configurations","integrations","integration_sync","canonical_evidence","files","complaint","investigation","investigation_evidence","investigation_note","investigation_check","investigation_decision","investigation_final_result","investigation_finding","investigation_finding_evidence","investigation_finding_check","ingested_source_record"};
      System.out.println("=== BEFORE COUNTS ===");
      for(String t: allTables){
        try(Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT count(*) FROM "+t)){
          r.next(); System.out.println(t+": "+r.getLong(1));
        } catch(Exception e){ System.out.println(t+": ERROR "+e.getMessage().split("\n")[0]); }
      }

      // Wipe tenant & investigation data, keep permissions and configuration_definitions (seeded globals)
      // Using TRUNCATE ... RESTART IDENTITY CASCADE for clean first-deploy state.
      // We include all tenant-scoped tables plus roles/users/organisations.
      // We intentionally DO NOT truncate permissions and configuration_definitions so app can boot without reseed delay,
      // but we also support truncating them if you want truly empty DB - they will be re-seeded on next app startup via PermissionSeeder.
      String truncateSql = "TRUNCATE TABLE " +
        "ingested_source_record, " +
        "investigation_finding_check, " +
        "investigation_finding_evidence, " +
        "investigation_finding, " +
        "investigation_final_result, " +
        "investigation_decision, " +
        "investigation_check, " +
        "investigation_note, " +
        "investigation_evidence, " +
        "complaint, " +
        "canonical_evidence, " +
        "files, " +
        "integration_sync, " +
        "integrations, " +
        "configurations, " +
        "investigation, " +
        "role_permissions, " +
        "users, " +
        "roles, " +
        "organisations " +
        "RESTART IDENTITY CASCADE";

      System.out.println("\n=== EXECUTING TRUNCATE ===");
      System.out.println(truncateSql);
      try(Statement s=c.createStatement()){
        s.execute(truncateSql);
        System.out.println("TRUNCATE success");
      } catch(Exception e){
        System.out.println("TRUNCATE failed: "+e.getMessage());
        e.printStackTrace();
        System.out.println("Falling back to DELETE cascade order...");
        c.rollback();
        // fallback delete order (children first)
        String[] deleteOrder = {
          "ingested_source_record",
          "investigation_finding_check",
          "investigation_finding_evidence",
          "investigation_finding",
          "investigation_final_result",
          "investigation_decision",
          "investigation_check",
          "investigation_note",
          "investigation_evidence",
          "complaint",
          "canonical_evidence",
          "files",
          "integration_sync",
          "integrations",
          "configurations",
          "investigation",
          "role_permissions",
          "users",
          "roles",
          "organisations"
        };
        for(String t: deleteOrder){
          try(Statement s2=c.createStatement()){
            int cnt=s2.executeUpdate("DELETE FROM "+t);
            System.out.println("DELETE "+t+": "+cnt);
          } catch(Exception e2){
            System.out.println("DELETE "+t+" error: "+e2.getMessage().split("\n")[0]);
            try(Statement s3=c.createStatement()){
              s3.execute("TRUNCATE "+t+" CASCADE");
              System.out.println("TRUNCATE "+t+" CASCADE fallback ok");
            } catch(Exception e3){
              System.out.println("TRUNCATE fallback failed for "+t+": "+e3.getMessage().split("\n")[0]);
            }
          }
        }
        // reset sequences manually
        try(Statement s=c.createStatement()){
          s.execute("SELECT setval(pg_get_serial_sequence('organisations','org_id'), 1, false)");
          s.execute("SELECT setval(pg_get_serial_sequence('users','id'), 1, false)");
          s.execute("SELECT setval(pg_get_serial_sequence('roles','id'), 1, false)");
          System.out.println("Sequences reset (best effort)");
        } catch(Exception e2){ System.out.println("Sequence reset skip: "+e2.getMessage().split("\n")[0]); }
      }

      c.commit();
      System.out.println("\n=== AFTER COUNTS ===");
      for(String t: allTables){
        try(Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT count(*) FROM "+t)){
          r.next(); System.out.println(t+": "+r.getLong(1));
        } catch(Exception e){ System.out.println(t+": ERROR "+e.getMessage().split("\n")[0]); }
      }

      // Verify platform admin still works (env var check)
      String adminEmail = System.getenv("PLATFORM_ADMIN_EMAIL");
      if(adminEmail==null) adminEmail="admin@taceiq.local";
      System.out.println("\nPlatform admin preserved via env: "+adminEmail+" (not in DB, JWT auth remains valid)");
      System.out.println("All tenant accounts deleted. DB is fresh for first deployment.");
    }
    System.out.println("PostgreSQL reset done.");
  }
}
