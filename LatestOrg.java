import java.sql.*;
public class LatestOrg {
  public static void main(String[] a) throws Exception{
    String url=System.getenv("DATABASE_URL");
    String user=System.getenv("DATABASE_USERNAME");
    String pass=System.getenv("DATABASE_PASSWORD");
    try(Connection c=DriverManager.getConnection(url,user,pass)){
      try(Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT org_id, name FROM organisations ORDER BY org_id DESC LIMIT 5")){
        while(r.next()) System.out.println("ORG "+r.getLong(1)+" name="+r.getString(2));
      }
      // latest org id
      long latest=0;
      try(Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT org_id FROM organisations ORDER BY org_id DESC LIMIT 1")){
        if(r.next()) latest=r.getLong(1);
      }
      System.out.println("latest org_id="+latest);
      try(PreparedStatement ps=c.prepareStatement("SELECT user_id, username, email, status, must_change_password FROM users WHERE org_id=? ORDER BY user_id DESC LIMIT 5")){
        ps.setLong(1,latest);
        try(ResultSet r=ps.executeQuery()){
          while(r.next()) System.out.println("USER "+r.getLong(1)+" username="+r.getString(2)+" email="+r.getString(3)+" status="+r.getString(4)+" mustChange="+r.getString(5));
        }
      }
      // also list roles for latest org
      try(PreparedStatement ps=c.prepareStatement("SELECT id, name FROM roles WHERE org_id=?")){
        ps.setLong(1,latest);
        try(ResultSet r=ps.executeQuery()){
          while(r.next()) System.out.println("ROLE "+r.getLong(1)+" "+r.getString(2));
        }
      }
    }
  }
}
