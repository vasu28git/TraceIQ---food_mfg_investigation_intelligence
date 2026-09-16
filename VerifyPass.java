import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
public class VerifyPass {
  public static void main(String[] a){
    BCryptPasswordEncoder e=new BCryptPasswordEncoder();
    String hash="$2a$10$2xsbRNfu5.ySLwFPWVvfUuIdwgVsARmLu3TsDL9JX/CYte4rc8gpe";
    System.out.println(e.matches("Hutsan123!", hash));
    System.out.println(e.matches("Hutsan123", hash));
    System.out.println(e.matches("ChangeMe123!", hash));
    System.out.println(e.encode("Hutsan123!"));
  }
}
