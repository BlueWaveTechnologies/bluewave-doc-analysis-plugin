package bluewave.model;
import bluewave.app.Document;
import javaxt.json.*;
import java.sql.SQLException;


//******************************************************************************
//**  DocumentComparisonStats Class
//******************************************************************************
/**
 *   Used to represent a DocumentComparisonStats
 *
 ******************************************************************************/

public class DocumentComparisonStats extends javaxt.sql.Model {

    private DocumentComparisonTest test;
    private Document a;
    private Document b;
    private JSONObject info;
    private Long time;
    private Long aSize;
    private Long bSize;


  //**************************************************************************
  //** Constructor
  //**************************************************************************
    public DocumentComparisonStats(){
        super("application.document_comparison_stats", java.util.Map.ofEntries(
            
            java.util.Map.entry("test", "test_id"),    
            java.util.Map.entry("a", "a_id"),
            java.util.Map.entry("b", "b_id"),
            java.util.Map.entry("time", "t"),
            java.util.Map.entry("aSize", "a_size"),
            java.util.Map.entry("bSize", "b_size"),
            java.util.Map.entry("info", "info")
            

        ));
        
    }


  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Creates a new instance of this class using a record ID in the database.
   */
    public DocumentComparisonStats(long id) throws SQLException {
        this();
        init(id);
    }


  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Creates a new instance of this class using a JSON representation of a
   *  DocumentComparisonStats.
   */
    public DocumentComparisonStats(JSONObject json){
        this();
        update(json);
    }


  //**************************************************************************
  //** update
  //**************************************************************************
  /** Used to update attributes using a record in the database.
   */
    protected void update(Object rs) throws SQLException {

        try{
            this.id = getValue(rs, "id").toLong();
            Long testId = getValue(rs, "test_id").toLong();
            this.aSize = getValue(rs, "a_size").toLong();
            this.bSize = getValue(rs, "b_size").toLong();
            this.time = getValue(rs, "t").toLong();
            Long aID = getValue(rs, "a_id").toLong();
            Long bID = getValue(rs, "b_id").toLong();
            this.info = new JSONObject(getValue(rs, "info").toString());

          // Set test
            if (testId!=null) test = new DocumentComparisonTest(testId);

          //Set a
            if (aID!=null) a = new Document(aID);


          //Set b
            if (bID!=null) b = new Document(bID);

        }
        catch(Exception e){
            if (e instanceof SQLException) throw (SQLException) e;
            else throw new SQLException(e.getMessage());
        }
    }


  //**************************************************************************
  //** update
  //**************************************************************************
  /** Used to update attributes with attributes from another DocumentComparisonStats.
   */
    public void update(JSONObject json){

        Long id = json.get("id").toLong();
        if (id!=null && id>0) this.id = id;
        if (json.has("a")){
            a = new Document(json.get("a").toJSONObject());
        }
        else if (json.has("aID")){
            try{
                a = new Document(json.get("aID").toLong());
            }
            catch(Exception e){}
        }
        if (json.has("b")){
            b = new Document(json.get("b").toJSONObject());
        }
        else if (json.has("bID")){
            try{
                b = new Document(json.get("bID").toLong());
            }
            catch(Exception e){}
        }
        if(json.has("test")){
            test = new DocumentComparisonTest(json.get("test").toJSONObject());
        } else if(json.has("testID")) {
            try{
                test = new DocumentComparisonTest(json.get("testID").toJSONObject());
            }catch(Exception e){}
        }
        this.aSize = json.get("aSize").toLong();
        this.bSize = json.get("bSize").toLong();
        this.time = json.get("time").toLong();
        this.info = json.get("info").toJSONObject();
        
    }

    public DocumentComparisonTest getTest() {
        return test;
    }

    public void setTest(DocumentComparisonTest test) {
        this.test = test;
    }

    public Long getTime() {
        return time;
    }

    public void setTime(Long time) {
        this.time = time;
    }

    public Long getaSize() {
        return aSize;
    }

    public void setaSize(Long aSize) {
        this.aSize = aSize;
    }

    public Long getbSize() {
        return bSize;
    }

    public void setbSize(Long bSize) {
        this.bSize = bSize;
    }


    public Document getA(){
        return a;
    }

    public void setA(Document a){
        this.a = a;
    }

    public Document getB(){
        return b;
    }

    public void setB(Document b){
        this.b = b;
    }

    public JSONObject getInfo(){
        return info;
    }

    public void setInfo(JSONObject info){
        this.info = info;
    }
    
    


  //**************************************************************************
  //** get
  //**************************************************************************
  /** Used to find a DocumentComparisonStats using a given set of constraints. Example:
   *  DocumentComparisonStats obj = DocumentComparisonStats.get("a_id=", a_id);
   */
    public static DocumentComparisonStats get(Object...args) throws SQLException {
        Object obj = _get(DocumentComparisonStats.class, args);
        return obj==null ? null : (DocumentComparisonStats) obj;
    }


  //**************************************************************************
  //** find
  //**************************************************************************
  /** Used to find DocumentComparisonStats using a given set of constraints.
   */
    public static DocumentComparisonStats[] find(Object...args) throws SQLException {
        Object[] obj = _find(DocumentComparisonStats.class, args);
        DocumentComparisonStats[] arr = new DocumentComparisonStats[obj.length];
        for (int i=0; i<arr.length; i++){
            arr[i] = (DocumentComparisonStats) obj[i];
        }
        return arr;
    }
}