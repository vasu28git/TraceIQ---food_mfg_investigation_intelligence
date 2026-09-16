import java.sql.*;
public class ListOrgUsers {
  public static void main(String[] a) throws Exception{
    String url=System.getenv("DATABASE_URL");
    String user=System.getenv("DATABASE_USERNAME");
    String pass=System.getenv("DATABASE_PASSWORD");
    try(Connection c=DriverManager.getConnection(url,user,pass)){
      System.out.println("connected");
      try(Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT org_id, name, org_key FROM organisations")){
        while(r.next()) System.out.println("ORG "+r.getLong(1)+" "+r.getString(2)+" key="+r.getString(3));
      }
      try(Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT user_id, username, email, org_id, role_id FROM users LIMIT 20")){
        while(r.next()) System.out.println("USER "+r.getLong(1)+" "+r.getString(2)+" email="+r.getString(3)+" org="+r.getLong(4)+" role="+r.getLong(5));
      }
      try(Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT id, name FROM roles LIMIT 20")){
        while(r.next()) System.out.println("ROLE "+r.getLong(1)+" "+r.getString(2));
      }
    }
  }
}
