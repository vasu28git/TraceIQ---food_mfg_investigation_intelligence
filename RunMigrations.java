import java.sql.*;
import java.nio.file.*;
public class RunMigrations {
  public static void main(String[] a) throws Exception{
    String url=System.getenv("DATABASE_URL");
    String user=System.getenv("DATABASE_USERNAME");
    String pass=System.getenv("DATABASE_PASSWORD");
    if(url==null) url="jdbc:postgresql://ep-shiny-salad-aygxj11y-pooler.c-5.us-east-2.aws.neon.tech/neondb?sslmode=require&channelBinding=require";
    if(user==null) user="neondb_owner";
    try(Connection c=DriverManager.getConnection(url,user,pass)){
      c.setAutoCommit(true);
      java.io.File dir=new java.io.File("src/main/resources/db/migration");
      java.io.File[] files=dir.listFiles((d,n)->n.endsWith(".sql"));
      java.util.Arrays.sort(files);
      for(java.io.File f: files){
        System.out.println("Running "+f.getName());
        String sql=Files.readString(f.toPath());
        // Split by ; but keep simple - execute whole via Statement
        try(Statement s=c.createStatement()){
          s.execute(sql);
          System.out.println(" OK "+f.getName());
        }catch(Exception e){
          System.out.println(" ERR "+f.getName()+": "+e.getMessage().split("\n")[0]);
          // try to continue
        }
      }
      System.out.println("Migrations done");
      // Verify investigation columns
      try(Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_name='investigation' ORDER BY ordinal_position")){
        System.out.println("investigation columns:");
        while(r.next()) System.out.println(" - "+r.getString(1));
      }
    }
  }
}
