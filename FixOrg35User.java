import java.sql.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
public class FixOrg35User {
  public static void main(String[] a) throws Exception{
    String url=System.getenv("DATABASE_URL");
    String user=System.getenv("DATABASE_USERNAME");
    String pass=System.getenv("DATABASE_PASSWORD");
    if(url==null) url="jdbc:postgresql://ep-shiny-salad-aygxj11y-pooler.c-5.us-east-2.aws.neon.tech/neondb?sslmode=require&channelBinding=require";
    if(user==null) user="neondb_owner";
    BCryptPasswordEncoder enc=new BCryptPasswordEncoder();
    String hashed=enc.encode("Hutsan123!");
    System.out.println("hashed "+hashed.substring(0,20));
    try(Connection c=DriverManager.getConnection(url,user,pass)){
      try(Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT user_id, username, email FROM users WHERE org_id=35")){
        while(r.next()) System.out.println("USER "+r.getLong(1)+" username="+r.getString(2)+" email="+r.getString(3));
      }
      // pick first user for org 35
      try(PreparedStatement ps=c.prepareStatement("SELECT user_id FROM users WHERE org_id=35 LIMIT 1")){
        try(ResultSet r=ps.executeQuery()){
          if(r.next()){
            long uid=r.getLong(1);
            System.out.println("updating uid "+uid);
            try(PreparedStatement upd=c.prepareStatement("UPDATE users SET password=?, must_change_password=false, status='ACTIVE' WHERE user_id=?")){
              upd.setString(1, hashed);
              upd.setLong(2, uid);
              int cnt=upd.executeUpdate();
              System.out.println("updated "+cnt);
            }
            // get username
            try(PreparedStatement ps2=c.prepareStatement("SELECT username FROM users WHERE user_id=?")){
              ps2.setLong(1, uid);
              try(ResultSet r2=ps2.executeQuery()){
                if(r2.next()) System.out.println("username for login: "+r2.getString(1));
              }
            }
          }
        }
      }
    }
  }
}
