import java.sql.*;
public class ResetIngested {
  public static void main(String[] a) throws Exception{
    String url=System.getenv("DATABASE_URL");
    String user=System.getenv("DATABASE_USERNAME");
    String pass=System.getenv("DATABASE_PASSWORD");
    if(url==null) url="jdbc:postgresql://ep-shiny-salad-aygxj11y-pooler.c-5.us-east-2.aws.neon.tech/neondb?sslmode=require&channelBinding=require";
    if(user==null) user="neondb_owner";
    try(Connection c=DriverManager.getConnection(url,user,pass)){
      try(Statement s=c.createStatement()){
        int d1=s.executeUpdate("DELETE FROM ingested_source_record WHERE org_id=35");
        System.out.println("deleted 35: "+d1);
        int d2=s.executeUpdate("DELETE FROM ingested_source_record WHERE org_id=36");
        System.out.println("deleted 36: "+d2);
        // also delete canonical for SRC_ for those orgs to allow re-discovery
        int c1=s.executeUpdate("DELETE FROM canonical_evidence WHERE org_id=36 AND external_id LIKE 'SRC_%'");
        System.out.println("deleted canonical 36 SRC: "+c1);
        int c2=s.executeUpdate("DELETE FROM canonical_evidence WHERE org_id=35 AND external_id LIKE 'SRC_%'");
        System.out.println("deleted canonical 35 SRC: "+c2);
        // delete investigation_evidence links for those incidents? Keep but will be recreated
      }
    }
  }
}
