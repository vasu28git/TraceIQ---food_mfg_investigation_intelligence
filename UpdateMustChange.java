import java.sql.*;
public class UpdateMustChange {
  public static void main(String[] a) throws Exception{
    String url=System.getenv("DATABASE_URL");
    String user=System.getenv("DATABASE_USERNAME");
    String pass=System.getenv("DATABASE_PASSWORD");
    if(url==null) url="jdbc:postgresql://ep-shiny-salad-aygxj11y-pooler.c-5.us-east-2.aws.neon.tech/neondb?sslmode=require&channelBinding=require";
    if(user==null) user="neondb_owner";
    try(Connection c=DriverManager.getConnection(url,user,pass)){
      try(PreparedStatement ps=c.prepareStatement("UPDATE users SET must_change_password=false WHERE username=?")){
        ps.setString(1,"phase13org181644_admin");
        int cnt=ps.executeUpdate();
        System.out.println("updated "+cnt);
      }
      try(PreparedStatement ps=c.prepareStatement("SELECT username, must_change_password FROM users WHERE username=?")){
        ps.setString(1,"phase13org181644_admin");
        try(ResultSet r=ps.executeQuery()){
          while(r.next()) System.out.println(r.getString(1)+" mustChange="+r.getBoolean(2));
        }
      }
    }
  }
}
