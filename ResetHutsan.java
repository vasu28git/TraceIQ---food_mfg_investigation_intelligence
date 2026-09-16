import java.sql.*;
public class ResetHutsan {
  public static void main(String[] a) throws Exception{
    String url=System.getenv("DATABASE_URL");
    String user=System.getenv("DATABASE_USERNAME");
    String pass=System.getenv("DATABASE_PASSWORD");
    if(url==null) url="jdbc:postgresql://ep-shiny-salad-aygxj11y-pooler.c-5.us-east-2.aws.neon.tech/neondb?sslmode=require&channelBinding=require";
    if(user==null) user="neondb_owner";
    String hash="$2a$10$xEF0lh9qbtgpK5hdN9o9AONVCo41nwQJczR90C/DAaSF5/2jHBf9C"; // Hutsan123!
    try(Connection c=DriverManager.getConnection(url,user,pass)){
      try(PreparedStatement ps=c.prepareStatement("UPDATE users SET password=?, must_change_password=false WHERE username='hutsan_admin'")){
        ps.setString(1, hash);
        System.out.println("updated "+ps.executeUpdate());
      }
      try(PreparedStatement ps=c.prepareStatement("UPDATE users SET password=?, must_change_password=false WHERE username='shyam'")){
        ps.setString(1, hash);
        System.out.println("shyam updated "+ps.executeUpdate());
      }
    }
  }
}
