import java.sql.*;
public class UpdateInc12 {
  public static void main(String[] a) throws Exception{
    String url=System.getenv("DATABASE_URL");
    String user=System.getenv("DATABASE_USERNAME");
    String pass=System.getenv("DATABASE_PASSWORD");
    try(Connection c=DriverManager.getConnection(url,user,pass)){
      try(PreparedStatement ps=c.prepareStatement("UPDATE investigation SET batch_reference='BATCH-1010', product_reference='PRD-106' WHERE id=12")){
        int cnt=ps.executeUpdate();
        System.out.println("updated "+cnt);
      }
      try(Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT id, investigation_key, batch_reference, product_reference FROM investigation WHERE id=12")){
        while(r.next()) System.out.println("id="+r.getLong(1)+" key="+r.getString(2)+" batch="+r.getString(3)+" prod="+r.getString(4));
      }
    }
  }
}
