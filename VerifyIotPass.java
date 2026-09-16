import java.sql.*;
public class VerifyIotPass {
  public static void main(String[] a) throws Exception{
    String url=System.getenv("DATABASE_URL");
    String user=System.getenv("DATABASE_USERNAME");
    String pass=System.getenv("DATABASE_PASSWORD");
    if(url==null) url="jdbc:postgresql://ep-shiny-salad-aygxj11y-pooler.c-5.us-east-2.aws.neon.tech/neondb?sslmode=require&channelBinding=require";
    if(user==null) user="neondb_owner";
    if(pass==null) pass="npg_q1UKOdLTQG0S";
    try(Connection c=DriverManager.getConnection(url,user,pass)){
      try(Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT username, password, must_change_password FROM users WHERE username='hutsan_admin'")){
        if(r.next()){
          String u=r.getString(1);
          String hash=r.getString(2);
          System.out.println("user: "+u);
          System.out.println("hash: "+hash);
          System.out.println("hash_prefix: "+hash.substring(0, Math.min(15, hash.length())));
          System.out.println("must_change: "+r.getBoolean(3));
          // try verify with BCrypt via java without spring - just check if hash starts with $2a$
          System.out.println("is BCrypt: "+hash.startsWith("$2a$"));
        } else System.out.println("user not found");
      }
    }
  }
}
