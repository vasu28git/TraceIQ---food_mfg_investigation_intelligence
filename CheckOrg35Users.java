import java.sql.*;
public class CheckOrg35Users {
  public static void main(String[] a) throws Exception{
    String url=System.getenv("DATABASE_URL");
    String user=System.getenv("DATABASE_USERNAME");
    String pass=System.getenv("DATABASE_PASSWORD");
    if(url==null) url="jdbc:postgresql://ep-shiny-salad-aygxj11y-pooler.c-5.us-east-2.aws.neon.tech/neondb?sslmode=require&channelBinding=require";
    if(user==null) user="neondb_owner";
    try(Connection c=DriverManager.getConnection(url,user,pass)){
      try(Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_name='users'")){
        while(r.next()) System.out.println("COL "+r.getString(1));
      }
      try(Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT * FROM users WHERE org_id=35 LIMIT 5")){
        ResultSetMetaData md=r.getMetaData();
        while(r.next()){
          for(int i=1;i<=md.getColumnCount();i++) System.out.print(md.getColumnName(i)+"="+r.getString(i)+" ");
          System.out.println();
        }
      }
      try(Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT org_id, name FROM organisations WHERE org_id=35")){
        if(r.next()) System.out.println("ORG 35 name="+r.getString(2));
      }
    }
  }
}
