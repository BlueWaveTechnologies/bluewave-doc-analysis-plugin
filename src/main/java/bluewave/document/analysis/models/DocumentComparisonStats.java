package bluewave.document.analysis.models;
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
    private Long t;
    private Long a_size;
    private Long b_size;
    private Double pyTotalDec;
    private Double pyPagesPerSec;
    private Double pyReadPdf;
    private Double pyPostProc;
    private Double pyElapsedTime;
    private JSONObject info;


  //**************************************************************************
  //** Constructor
  //**************************************************************************
    public DocumentComparisonStats(){
        super("application.document_comparison_stats", java.util.Map.ofEntries(

            java.util.Map.entry("test", "test_id"),
            java.util.Map.entry("a", "a_id"),
            java.util.Map.entry("b", "b_id"),
            java.util.Map.entry("t", "t"),
            java.util.Map.entry("a_size", "a_size"),
            java.util.Map.entry("b_size", "b_size"),
            java.util.Map.entry("pyTotalDec", "py_total_dec"),
            java.util.Map.entry("pyPagesPerSec", "py_pages_per_sec"),
            java.util.Map.entry("pyReadPdf", "py_read_pdf"),
            java.util.Map.entry("pyPostProc", "py_post_proc"),
            java.util.Map.entry("pyElapsedTime", "py_elapsed_time"),
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
            Long testID = getValue(rs, "test_id").toLong();
            Long aID = getValue(rs, "a_id").toLong();
            Long bID = getValue(rs, "b_id").toLong();
            this.t = getValue(rs, "t").toLong();
            this.a_size = getValue(rs, "a_size").toLong();
            this.b_size = getValue(rs, "b_size").toLong();
            this.pyTotalDec = getValue(rs, "py_total_dec").toDouble();
            this.pyPagesPerSec = getValue(rs, "py_pages_per_sec").toDouble();
            this.pyReadPdf = getValue(rs, "py_read_pdf").toDouble();
            this.pyPostProc = getValue(rs, "py_post_proc").toDouble();
            this.pyElapsedTime = getValue(rs, "py_elapsed_time").toDouble();
            this.info = new JSONObject(getValue(rs, "info").toString());



          //Set test
            if (testID!=null) test = new DocumentComparisonTest(testID);


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
        if (json.has("test")){
            test = new DocumentComparisonTest(json.get("test").toJSONObject());
        }
        else if (json.has("testID")){
            try{
                test = new DocumentComparisonTest(json.get("testID").toLong());
            }
            catch(Exception e){}
        }
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
        this.t = json.get("t").toLong();
        this.a_size = json.get("a_size").toLong();
        this.b_size = json.get("b_size").toLong();
        this.pyTotalDec = json.get("pyTotalDec").toDouble();
        this.pyPagesPerSec = json.get("pyPagesPerSec").toDouble();
        this.pyReadPdf = json.get("pyReadPdf").toDouble();
        this.pyPostProc = json.get("pyPostProc").toDouble();
        this.pyElapsedTime = json.get("pyElapsedTime").toDouble();
        this.info = json.get("info").toJSONObject();
    }


    public DocumentComparisonTest getTest(){
        return test;
    }

    public void setTest(DocumentComparisonTest test){
        this.test = test;
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

    public Long getT(){
        return t;
    }

    public void setT(Long t){
        this.t = t;
    }

    public Long getA_size(){
        return a_size;
    }

    public void setA_size(Long a_size){
        this.a_size = a_size;
    }

    public Long getB_size(){
        return b_size;
    }

    public void setB_size(Long b_size){
        this.b_size = b_size;
    }

    public Double getPyTotalDec(){
        return pyTotalDec;
    }

    public void setPyTotalDec(Double pyTotalDec){
        this.pyTotalDec = pyTotalDec;
    }

    public Double getPyPagesPerSec(){
        return pyPagesPerSec;
    }

    public void setPyPagesPerSec(Double pyPagesPerSec){
        this.pyPagesPerSec = pyPagesPerSec;
    }

    public Double getPyReadPdf(){
        return pyReadPdf;
    }

    public void setPyReadPdf(Double pyReadPdf){
        this.pyReadPdf = pyReadPdf;
    }

    public Double getPyPostProc(){
        return pyPostProc;
    }

    public void setPyPostProc(Double pyPostProc){
        this.pyPostProc = pyPostProc;
    }

    public Double getPyElapsedTime(){
        return pyElapsedTime;
    }

    public void setPyElapsedTime(Double pyElapsedTime){
        this.pyElapsedTime = pyElapsedTime;
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
   *  DocumentComparisonStats obj = DocumentComparisonStats.get("test_id=", test_id);
   */
    public static DocumentComparisonStats get(Object...args) throws SQLException {
        Object obj = _get(DocumentComparisonStats.class, args);
        return obj==null ? null : (DocumentComparisonStats) obj;
    }


  //**************************************************************************
  //** find
  //**************************************************************************
  /** Used to find DocumentComparisonStatss using a given set of constraints.
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