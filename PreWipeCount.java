import java.sql.*;
public class PreWipeCount {
  public static void main(String[] a) throws Exception{
    String url=System.getenv("DATABASE_URL");
    String user=System.getenv("DATABASE_USERNAME");
    String pass=System.getenv("DATABASE_PASSWORD");
    if(url==null) url="jdbc:postgresql://ep-shiny-salad-aygxj11y-pooler.c-5.us-east-2.aws.neon.tech/neondb?sslmode=require&channelBinding=require";
    if(user==null) user="neondb_owner";
    if(pass==null) pass="npg_q1UKOdLTQG0S";
    try(Connection c=DriverManager.getConnection(url,user,pass)){
      String[] tables = {"organisations","users","roles","permissions","role_permissions","configuration_definitions","configurations","integrations","integration_sync","canonical_evidence","files","complaint","investigation","investigation_evidence","investigation_note","investigation_check","investigation_decision","investigation_final_result","investigation_finding","investigation_finding_evidence","investigation_finding_check","ingested_source_record"};
      for(String t: tables){
        try(Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT count(*) FROM "+t)){
          r.next(); System.out.println(t+": "+r.getLong(1));
        } catch(Exception e){ System.out.println(t+": ERROR "+e.getMessage().split("\n")[0]); }
      }
    }
  }
}
