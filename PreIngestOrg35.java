import java.sql.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;

public class PreIngestOrg35 {
  static Map<String,String> aliasToNorm = new HashMap<>();
  static {
    for(String a: List.of("batch_id","batchid","batch_number","batchnumber","batch_ref","batchref","batch_reference","batchreference","lot_id","lotid","lot_number","lotnumber","batch","lot")) aliasToNorm.put(a, "BATCH");
    for(String a: List.of("machine_id","machineid","equipment_id","equipmentid","machine","equipment")) aliasToNorm.put(a, "MACHINE");
    for(String a: List.of("supplier_id","supplierid","vendor_id","vendorid","supplier","vendor")) aliasToNorm.put(a, "SUPPLIER");
    for(String a: List.of("product_id","productid","sku","product_code","productcode","product")) aliasToNorm.put(a, "PRODUCT");
    for(String a: List.of("order_id","orderid","order_number","ordernumber","sales_order","salesorder","order")) aliasToNorm.put(a, "ORDER");
    for(String a: List.of("complaint_id","complaintid","case_id","caseid","casenumber","ticket_id","ticketid","report_id","reportid","shipment_id","shipmentid","log_id","logid","doc_id","docid","record_id","recordid","external_id","externalid","id")) aliasToNorm.put(a, "EXTERNAL");
  }
  static String normField(String raw){ return raw.trim().toLowerCase().replaceAll("[_\\s]+",""); }
  static String map(String field){ return aliasToNorm.get(normField(field)); }
  static String inferType(String name){
    String l=name.toLowerCase();
    if(l.contains("crm")||l.contains("complaint")) return "CRM";
    if(l.contains("mes")||l.contains("production")) return "MES";
    if(l.contains("lims")||l.contains("qa")||l.contains("inspection")) return "LIMS";
    if(l.contains("cmms")||l.contains("maintenance")) return "CMMS";
    if(l.contains("shipment")||l.contains("distribution")) return "SHIPMENT";
    if(l.contains("warehouse")||l.contains("temperature")) return "WAREHOUSE";
    if(l.contains("sop")||l.contains("audit")) return "SOP";
    if(l.contains("supplier")||l.contains("erp")) return "ERP";
    return "OTHER";
  }
  static List<String> splitCsv(String line){
    List<String> out=new ArrayList<>(); StringBuilder cur=new StringBuilder(); boolean in=false;
    for(int i=0;i<line.length();i++){ char c=line.charAt(i); if(c=='"'){ if(in && i+1<line.length() && line.charAt(i+1)=='"'){cur.append('"');i++;}else in=!in;} else if(c==','&&!in){out.add(cur.toString());cur.setLength(0);} else cur.append(c); }
    out.add(cur.toString()); return out;
  }
  public static void main(String[] a) throws Exception{
    String url=System.getenv("DATABASE_URL");
    String user=System.getenv("DATABASE_USERNAME");
    String pass=System.getenv("DATABASE_PASSWORD");
    if(url==null) url="jdbc:postgresql://ep-shiny-salad-aygxj11y-pooler.c-5.us-east-2.aws.neon.tech/neondb?sslmode=require&channelBinding=require";
    if(user==null) user="neondb_owner";
    ObjectMapper om=new ObjectMapper();
    try(Connection c=DriverManager.getConnection(url,user,pass)){
      // get files for org 35
      List<Map<String,Object>> files=new ArrayList<>();
      try(PreparedStatement ps=c.prepareStatement("SELECT id, original_name, storage_key FROM files WHERE org_id=35")){
        try(ResultSet r=ps.executeQuery()){
          while(r.next()){
            Map<String,Object> m=new HashMap<>();
            m.put("id",r.getLong(1));
            m.put("original_name",r.getString(2));
            m.put("storage_key",r.getString(3));
            files.add(m);
          }
        }
      }
      System.out.println("files for org35: "+files.size());
      for(Map<String,Object> f: files){
        long fid=(Long)f.get("id");
        String on=(String)f.get("original_name");
        String sk=(String)f.get("storage_key");
        String st=inferType(on);
        System.out.println("ingesting "+on+" type "+st+" id "+fid);
        Path p=Paths.get(sk);
        if(!Files.exists(p)) p=Paths.get(sk.replace("./","").replace(".\\",""));
        if(!Files.exists(p)) { System.out.println("  not found "+sk); continue; }
        List<String> lines=Files.readAllLines(p, StandardCharsets.UTF_8);
        if(lines.isEmpty()) continue;
        String header=null; int hidx=0;
        for(int i=0;i<lines.size();i++) if(!lines.get(i).trim().isEmpty()){header=lines.get(i);hidx=i;break;}
        if(header==null) continue;
        List<String> headers=splitCsv(header);
        int created=0,reused=0;
        for(int i=hidx+1;i<lines.size();i++){
          String line=lines.get(i);
          if(line.trim().isEmpty()) continue;
          List<String> vals=splitCsv(line);
          Map<String,String> row=new LinkedHashMap<>();
          for(int col=0;col<headers.size();col++){
            String h=headers.get(col).trim();
            String v=col<vals.size()?vals.get(col):"";
            row.put(h, v.trim());
          }
          boolean allEmpty=row.values().stream().allMatch(v->v==null||v.trim().isEmpty());
          if(allEmpty) continue;
          Map<String,String> norm=new HashMap<>();
          for(Map.Entry<String,String> e: row.entrySet()){
            String n=map(e.getKey());
            if(n!=null && e.getValue()!=null && !e.getValue().trim().isEmpty()){
              norm.putIfAbsent(n, e.getValue().trim());
            }
          }
          String batch=norm.get("BATCH");
          String machine=norm.get("MACHINE");
          String supplier=norm.get("SUPPLIER");
          String product=norm.get("PRODUCT");
          String order=norm.get("ORDER");
          String external=norm.get("EXTERNAL");
          String recId=external;
          if(recId==null) recId=batch;
          if(recId==null){
            for(String k: List.of("report_id","shipment_id","log_id","doc_id","id")){
              for(Map.Entry<String,String> e: row.entrySet()) if(e.getKey().equalsIgnoreCase(k) && e.getValue()!=null && !e.getValue().trim().isEmpty()){recId=e.getValue().trim();break;}
              if(recId!=null) break;
            }
          }
          if(recId==null) recId="ROW-"+Math.abs(String.join("|",row.values()).hashCode());
          recId=recId.trim();
          // check exists
          try(PreparedStatement ps=c.prepareStatement("SELECT id FROM ingested_source_record WHERE org_id=35 AND source_type=? AND source_record_id=?")){
            ps.setString(1,st); ps.setString(2,recId);
            try(ResultSet r=ps.executeQuery()){ if(r.next()){reused++; continue;}}
          }
          String payload=om.writeValueAsString(row);
          try(PreparedStatement ps=c.prepareStatement("INSERT INTO ingested_source_record (org_id, file_id, source_type, source_record_id, batch_reference, machine_reference, supplier_reference, product_reference, order_reference, external_reference, payload) VALUES (?,?,?,?,?,?,?,?,?,?,?::jsonb)")){
            ps.setLong(1,35); ps.setLong(2,fid); ps.setString(3,st); ps.setString(4,recId); ps.setString(5,batch); ps.setString(6,machine); ps.setString(7,supplier); ps.setString(8,product); ps.setString(9,order); ps.setString(10,external!=null?external:recId); ps.setString(11,payload);
            ps.executeUpdate(); created++;
          }catch(Exception e){ System.out.println("  insert fail "+recId+" "+e.getMessage().split("\n")[0]); }
        }
        System.out.println("  created "+created+" reused "+reused);
      }
      try(Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT source_type, count(*) FROM ingested_source_record WHERE org_id=35 GROUP BY source_type")){
        while(r.next()) System.out.println("type "+r.getString(1)+" "+r.getLong(2));
      }
    }
  }
}
