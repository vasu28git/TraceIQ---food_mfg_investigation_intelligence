import java.sql.*;
public class TestNeonQuick {
  public static void main(String[] a) throws Exception {
    String url=System.getenv("DATABASE_URL");
    String user=System.getenv("DATABASE_USERNAME");
    String pass=System.getenv("DATABASE_PASSWORD");
    System.out.println("connecting...");
    try(Connection c=DriverManager.getConnection(url,user,pass)){
      try(Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT 1")){
        r.next(); System.out.println("SELECT 1 => "+r.getInt(1));
      }
      System.out.println("JDBC OK");
    }
  }
}
