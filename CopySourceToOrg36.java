import java.sql.*;
public class CopySourceToOrg36 {
  public static void main(String[] a) throws Exception{
    String url=System.getenv("DATABASE_URL");
    String user=System.getenv("DATABASE_USERNAME");
    String pass=System.getenv("DATABASE_PASSWORD");
    if(url==null) url="jdbc:postgresql://ep-shiny-salad-aygxj11y-pooler.c-5.us-east-2.aws.neon.tech/neondb?sslmode=require&channelBinding=require";
    if(user==null) user="neondb_owner";
    try(Connection c=DriverManager.getConnection(url,user,pass)){
      c.setAutoCommit(false);
      // Delete existing for org 36
      try(Statement s=c.createStatement()){int del=s.executeUpdate("DELETE FROM ingested_source_record WHERE org_id=36"); System.out.println("deleted "+del);}
      // Copy from org 35 to 36, keep same source_type etc but set org_id 36, file_id to null (or keep original file_id but that file belongs to org 35, need to set to org36's file)
      // We'll copy with file_id null for simplicity
      try(Statement s=c.createStatement()){
        int ins=s.executeUpdate("INSERT INTO ingested_source_record (org_id, file_id, source_type, source_record_id, batch_reference, machine_reference, supplier_reference, product_reference, order_reference, external_reference, payload) SELECT 36, null, source_type, source_record_id, batch_reference, machine_reference, supplier_reference, product_reference, order_reference, external_reference, payload FROM ingested_source_record WHERE org_id=35");
        System.out.println("copied "+ins);
      }
      c.commit();
      // Verify
      try(Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT source_type, count(*) FROM ingested_source_record WHERE org_id=36 GROUP BY source_type")){
        while(r.next()) System.out.println(r.getString(1)+" "+r.getLong(2));
      }
      try(Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT count(*) FROM ingested_source_record WHERE org_id=36 AND batch_reference='BATCH-1010'")){
        r.next(); System.out.println("batch 1010 count "+r.getLong(1));
      }
      // Check machine
      try(Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT count(*) FROM ingested_source_record WHERE org_id=36 AND machine_reference='M-05'")){
        r.next(); System.out.println("machine M-05 count "+r.getLong(1));
      }
    }
  }
}
