import java.sql.*;
public class FreshLaunchReset{
  public static void main(String[] a) throws Exception{
    String url=System.getenv("DATABASE_URL");
    String user=System.getenv("DATABASE_USERNAME");
    String pass=System.getenv("DATABASE_PASSWORD");
    if(url==null) url="jdbc:postgresql://ep-shiny-salad-aygxj11y-pooler.c-5.us-east-2.aws.neon.tech/neondb?sslmode=require&channelBinding=require";
    if(user==null) user="neondb_owner";
    System.out.println("Connecting to PostgreSQL...");
    try(Connection c=DriverManager.getConnection(url,user,pass)){
      c.setAutoCommit(false);
      System.out.println("Connected, starting transactional delete...");
      String[] tablesInOrder = {
        "investigation_final_result",
        "investigation_decision",
        "investigation_check",
        "investigation_note",
        "investigation_evidence",
        "complaint",
        "investigation",
        "canonical_evidence",
        "files",
        "integration_sync",
        "integrations",
        "configurations",
        "users",
        "role_permissions",
        "roles",
        "organisations"
      };
      for(String t: tablesInOrder){
        try(Statement s=c.createStatement()){
          int cnt=s.executeUpdate("DELETE FROM "+t);
          System.out.println("DELETE "+t+": "+cnt);
        } catch(Exception e){
          System.out.println("DELETE "+t+" error: "+e.getMessage().split("\n")[0]);
          // try truncate cascade as fallback
          try(Statement s2=c.createStatement()){
            int cnt2=s2.executeUpdate("TRUNCATE "+t+" CASCADE");
            System.out.println("TRUNCATE "+t+" CASCADE: "+cnt2);
          } catch(Exception e2){
            System.out.println("TRUNCATE "+t+" failed: "+e2.getMessage().split("\n")[0]);
          }
        }
      }
      c.commit();
      System.out.println("Transaction committed.");
      // Verify counts
      String[] verifyTables = {"organisations","users","roles","permissions","role_permissions","configuration_definitions","configurations","integrations","integration_sync","canonical_evidence","files","complaint","investigation","investigation_evidence","investigation_note","investigation_check","investigation_decision","investigation_final_result"};
      System.out.println("=== Verification ===");
      for(String t: verifyTables){
        try(Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT count(*) FROM "+t)){
          r.next(); System.out.println(t+": "+r.getLong(1));
        } catch(Exception e){ System.out.println(t+": error "+e.getMessage().split("\n")[0]); }
      }
    }
    System.out.println("PostgreSQL reset done.");
  }
}
