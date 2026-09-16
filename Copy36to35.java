import java.sql.*;
public class Copy36to35 {
  public static void main(String[] a) throws Exception{
    String url=System.getenv("DATABASE_URL");
    String user=System.getenv("DATABASE_USERNAME");
    String pass=System.getenv("DATABASE_PASSWORD");
    if(url==null) url="jdbc:postgresql://ep-shiny-salad-aygxj11y-pooler.c-5.us-east-2.aws.neon.tech/neondb?sslmode=require&channelBinding=require";
    if(user==null) user="neondb_owner";
    try(Connection c=DriverManager.getConnection(url,user,pass)){
      c.setAutoCommit(false);
      try(Statement s=c.createStatement()){
        int ins=s.executeUpdate("INSERT INTO ingested_source_record (org_id, file_id, source_type, source_record_id, batch_reference, machine_reference, supplier_reference, product_reference, order_reference, external_reference, payload) SELECT 35, null, source_type, source_record_id, batch_reference, machine_reference, supplier_reference, product_reference, order_reference, external_reference, payload FROM ingested_source_record WHERE org_id=36 ON CONFLICT (org_id, source_type, source_record_id) DO NOTHING");
        System.out.println("copied to 35: "+ins);
      }
      c.commit();
      try(Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT count(*) FROM ingested_source_record WHERE org_id=35")){
        r.next(); System.out.println("35 count "+r.getLong(1));
      }
    }
  }
}
