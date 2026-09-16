import java.sql.*;
public class GetIotCreds {
  public static void main(String[] a) throws Exception{
    String url=System.getenv("DATABASE_URL");
    String user=System.getenv("DATABASE_USERNAME");
    String pass=System.getenv("DATABASE_PASSWORD");
    if(url==null) url="jdbc:postgresql://ep-shiny-salad-aygxj11y-pooler.c-5.us-east-2.aws.neon.tech/neondb?sslmode=require&channelBinding=require";
    if(user==null) user="neondb_owner";
    if(pass==null) pass="npg_q1UKOdLTQG0S";
    try(Connection c=DriverManager.getConnection(url,user,pass)){
      System.out.println("=== ORGANISATIONS ===");
      try(Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT org_id, name, domain, status FROM organisations ORDER BY org_id DESC")){
        while(r.next()) System.out.println("ORG "+r.getLong(1)+" name="+r.getString(2)+" domain="+r.getString(3)+" status="+r.getString(4));
      }
      long latest=0;
      try(Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT org_id FROM organisations ORDER BY org_id DESC LIMIT 1")){
        if(r.next()) latest=r.getLong(1);
      }
      System.out.println("latest org_id="+latest);
      System.out.println("=== USERS latest org ===");
      try(PreparedStatement ps=c.prepareStatement("SELECT id, username, status, must_change_password, org_id, role_id FROM users WHERE org_id=? ORDER BY id DESC")){
        ps.setLong(1,latest);
        ResultSet r=ps.executeQuery();
        while(r.next()) System.out.println("USER id="+r.getLong(1)+" username="+r.getString(2)+" status="+r.getString(3)+" mustChange="+r.getBoolean(4)+" org="+r.getLong(5)+" role="+r.getLong(6));
      }
      System.out.println("=== ALL USERS ===");
      try(Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT id, username, status, must_change_password, org_id, role_id FROM users ORDER BY id DESC LIMIT 20")){
        while(r.next()) System.out.println("USER id="+r.getLong(1)+" username="+r.getString(2)+" org="+r.getLong(5));
      }
      System.out.println("=== ROLES ===");
      try(Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT id, name, org_id FROM roles ORDER BY id DESC")){
        while(r.next()) System.out.println("ROLE "+r.getLong(1)+" "+r.getString(2)+" org="+r.getLong(3));
      }
    }
  }
}
