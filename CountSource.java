import java.sql.*;
public class CountSource {
  public static void main(String[] a) throws Exception{
    String url=System.getenv("DATABASE_URL");
    String user=System.getenv("DATABASE_USERNAME");
    String pass=System.getenv("DATABASE_PASSWORD");
    try(Connection c=DriverManager.getConnection(url,user,pass)){
      try(Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT count(*) FROM ingested_source_record WHERE org_id=35")){
        r.next(); System.out.println("count org35="+r.getLong(1));
      }
      try(Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT source_type, count(*) FROM ingested_source_record WHERE org_id=35 GROUP BY source_type")){
        while(r.next()) System.out.println(r.getString(1)+" "+r.getLong(2));
      }
      try(Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT batch_reference, count(*) FROM ingested_source_record WHERE org_id=35 GROUP BY batch_reference LIMIT 10")){
        while(r.next()) System.out.println("batch "+r.getString(1)+" "+r.getLong(2));
      }
    }
  }
}
