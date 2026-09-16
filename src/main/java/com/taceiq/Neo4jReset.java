package com.taceiq;
import org.neo4j.driver.*;
public class Neo4jReset {
  public static void main(String[] args) throws Exception {
    String uri = System.getenv("NEO4J_URI");
    String user = System.getenv("NEO4J_USERNAME");
    String pass = System.getenv("NEO4J_PASSWORD");
    String db = System.getenv("NEO4J_DATABASE");
    if(uri==null) uri="neo4j+s://3b42b7a0.databases.neo4j.io";
    if(user==null) user="3b42b7a0";
    if(db==null) db="neo4j";
    System.out.println("Connecting to Neo4j "+uri+" db "+db);
    Driver driver = null;
    try {
      driver = GraphDatabase.driver(uri, AuthTokens.basic(user, pass));
      System.out.println("driver created, verifying connectivity...");
      driver.verifyConnectivity();
      System.out.println("connectivity ok");
      Session session = null;
      try {
        session = driver.session(SessionConfig.forDatabase(db));
        Result r = session.run("MATCH (n) RETURN count(n) as c");
        long c1 = r.single().get("c").asLong();
        System.out.println("count before: "+c1);
        session.run("MATCH (n) DETACH DELETE n").consume();
        System.out.println("deleted");
        Result r2 = session.run("MATCH (n) RETURN count(n) as c");
        long c2 = r2.single().get("c").asLong();
        System.out.println("count after: "+c2);
        session.close();
      } catch (Throwable inner) {
        System.out.println("with db failed: "+inner.getMessage().split("\n")[0]+", trying without db");
        inner.printStackTrace();
        if(session!=null) try{ session.close(); }catch(Exception ignored){}
        try(Session s2 = driver.session()){
          Result r = s2.run("MATCH (n) RETURN count(n) as c");
          long c1 = r.single().get("c").asLong();
          System.out.println("count before (no db): "+c1);
          s2.run("MATCH (n) DETACH DELETE n").consume();
          System.out.println("deleted (no db)");
          Result r2 = s2.run("MATCH (n) RETURN count(n) as c");
          System.out.println("count after (no db): "+r2.single().get("c").asLong());
        } catch (Throwable e2){
          System.out.println("without db also failed: "+e2.getMessage().split("\n")[0]);
          e2.printStackTrace();
        }
      }
    } catch (Throwable e) {
      System.out.println("outer failed: "+e.getMessage().split("\n")[0]);
      e.printStackTrace();
    } finally { if(driver!=null) driver.close(); }
  }
}
