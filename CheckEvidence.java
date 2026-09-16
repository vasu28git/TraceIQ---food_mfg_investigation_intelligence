import java.sql.*;
public class CheckEvidence {
  public static void main(String[] a) throws Exception{
    String url=System.getenv("DATABASE_URL");
    String user=System.getenv("DATABASE_USERNAME");
    String pass=System.getenv("DATABASE_PASSWORD");
    if(url==null) url="jdbc:postgresql://ep-shiny-salad-aygxj11y-pooler.c-5.us-east-2.aws.neon.tech/neondb?sslmode=require&channelBinding=require";
    if(user==null) user="neondb_owner";
    try(Connection c=DriverManager.getConnection(url,user,pass)){
      try(Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT count(*) FROM canonical_evidence WHERE org_id=36")){
        r.next(); System.out.println("canonical 36 total "+r.getLong(1));
      }
      try(Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT source_type, count(*) FROM canonical_evidence WHERE org_id=36 GROUP BY source_type")){
        while(r.next()) System.out.println(r.getString(1)+" "+r.getLong(2));
      }
      try(Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT count(*) FROM ingested_source_record WHERE org_id=36")){
        r.next(); System.out.println("source 36 "+r.getLong(1));
      }
      try(Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT external_id, source_type, title FROM canonical_evidence WHERE org_id=36 AND external_id LIKE 'SRC_%' ORDER BY external_id LIMIT 10")){
        while(r.next()) System.out.println(r.getString(1)+" "+r.getString(2)+" "+r.getString(3));
      }
      // Check incident 13
      try(PreparedStatement ps=c.prepareStatement("SELECT investigation_key, batch_reference, status FROM investigation WHERE id=?")){
        ps.setLong(1,13);
        try(ResultSet r=ps.executeQuery()){
          if(r.next()) System.out.println("incident 13 key="+r.getString(1)+" batch="+r.getString(2)+" status="+r.getString(3));
        }
      }
    }
  }
}
