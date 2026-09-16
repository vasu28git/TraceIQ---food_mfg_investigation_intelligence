import java.sql.*;
public class CheckOrg36 {
  public static void main(String[] a) throws Exception{
    String url=System.getenv("DATABASE_URL");
    String user=System.getenv("DATABASE_USERNAME");
    String pass=System.getenv("DATABASE_PASSWORD");
    if(url==null) url="jdbc:postgresql://ep-shiny-salad-aygxj11y-pooler.c-5.us-east-2.aws.neon.tech/neondb?sslmode=require&channelBinding=require";
    if(user==null) user="neondb_owner";
    try(Connection c=DriverManager.getConnection(url,user,pass)){
      try(Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT org_id, name, domain, description FROM organisations WHERE org_id=36")){
        if(r.next()) System.out.println("ORG "+r.getLong(1)+" name="+r.getString(2)+" domain="+r.getString(3)+" desc="+r.getString(4));
      }
      try(Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT id, username, status, must_change_password, role_id FROM users WHERE org_id=36")){
        while(r.next()) System.out.println("USER id="+r.getLong(1)+" username="+r.getString(2)+" status="+r.getString(3)+" mustChange="+r.getString(4)+" role="+r.getLong(5));
      }
      try(Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT count(*) FROM ingested_source_record WHERE org_id=36")){
        r.next(); System.out.println("source_records 36="+r.getLong(1));
      }
      try(Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT source_type, count(*) FROM ingested_source_record WHERE org_id=36 GROUP BY source_type ORDER BY source_type")){
        while(r.next()) System.out.println("SRC "+r.getString(1)+" "+r.getLong(2));
      }
      try(Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT id, investigation_key, title, batch_reference, status FROM investigation WHERE org_id=36")){
        while(r.next()) System.out.println("INC id="+r.getLong(1)+" key="+r.getString(2)+" title="+r.getString(3)+" batch="+r.getString(4)+" status="+r.getString(5));
      }
      try(Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT count(*) FROM canonical_evidence WHERE org_id=36")){
        r.next(); System.out.println("canonical 36="+r.getLong(1));
      }
      try(Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT source_type, count(*) FROM canonical_evidence WHERE org_id=36 GROUP BY source_type ORDER BY source_type")){
        while(r.next()) System.out.println("CANON "+r.getString(1)+" "+r.getLong(2));
      }
    }
  }
}
