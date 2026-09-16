import java.sql.*;
public class CopyPassword {
  public static void main(String[] a) throws Exception{
    String url=System.getenv("DATABASE_URL");
    String user=System.getenv("DATABASE_USERNAME");
    String pass=System.getenv("DATABASE_PASSWORD");
    if(url==null) url="jdbc:postgresql://ep-shiny-salad-aygxj11y-pooler.c-5.us-east-2.aws.neon.tech/neondb?sslmode=require&channelBinding=require";
    if(user==null) user="neondb_owner";
    try(Connection c=DriverManager.getConnection(url,user,pass)){
      String hash=null;
      try(PreparedStatement ps=c.prepareStatement("SELECT password FROM users WHERE username='smoke_org_org_smoke_20260913163119_admin'")){
        try(ResultSet r=ps.executeQuery()){
          if(r.next()) hash=r.getString(1);
        }
      }
      System.out.println("hash found len "+(hash!=null?hash.length():0));
      if(hash!=null){
        try(PreparedStatement ps=c.prepareStatement("UPDATE users SET password=?, must_change_password=false WHERE username='hutsan_admin'")){
          ps.setString(1,hash);
          int cnt=ps.executeUpdate();
          System.out.println("updated "+cnt);
        }
      }
      try(Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT username, must_change_password FROM users WHERE username='hutsan_admin'")){
        while(r.next()) System.out.println(r.getString(1)+" mustChange="+r.getString(2));
      }
    }
  }
}
