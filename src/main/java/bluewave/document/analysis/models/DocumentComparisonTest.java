package bluewave.document.analysis.models;

import java.sql.SQLException;

//******************************************************************************
//**  DocumentComparisonTest Class
//******************************************************************************
/**
 * Used to represent a DocumentComparisonTest
 *
 ******************************************************************************/

public class DocumentComparisonTest extends javaxt.sql.Model {

    private Integer numThreads;
    private String scriptVersion;
    private String host;
    private JSONObject info;

    // **************************************************************************
    // ** Constructor
    // **************************************************************************
    public DocumentComparisonTest() {
        super("application.document_comparison_test", java.util.Map.ofEntries(

                java.util.Map.entry("numThreads", "num_threads"),
                java.util.Map.entry("scriptVersion", "script_version"), java.util.Map.entry("host", "host"),
                java.util.Map.entry("info", "info")

        ));

    }

    // **************************************************************************
    // ** Constructor
    // **************************************************************************
    /**
     * Creates a new instance of this class using a record ID in the database.
     */
    public DocumentComparisonTest(long id) throws SQLException {
        this();
        init(id);
    }

    // **************************************************************************
    // ** Constructor
    // **************************************************************************
    /**
     * Creates a new instance of this class using a JSON representation of a
     * DocumentComparisonTest.
     */
    public DocumentComparisonTest(JSONObject json) {
        this();
        update(json);
    }

    // **************************************************************************
    // ** update
    // **************************************************************************
    /**
     * Used to update attributes using a record in the database.
     */
    protected void update(Object rs) throws SQLException {

        try {
            this.id = getValue(rs, "id").toLong();
            this.numThreads = getValue(rs, "num_threads").toInteger();
            this.scriptVersion = getValue(rs, "script_version").toString();
            this.host = getValue(rs, "host").toString();
            this.info = new JSONObject(getValue(rs, "info").toString());

        } catch (final Exception e) {
            if (e instanceof SQLException) {
                throw (SQLException) e;
            }
            throw new SQLException(e.getMessage());
        }
    }

    // **************************************************************************
    // ** update
    // **************************************************************************
    /**
     * Used to update attributes with attributes from another
     * DocumentComparisonTest.
     */
    public void update(JSONObject json) {

        final Long id = json.get("id").toLong();
        if (id != null && id > 0) {
            this.id = id;
        }
        this.numThreads = json.get("numThreads").toInteger();
        this.scriptVersion = json.get("scriptVersion").toString();
        this.host = json.get("host").toString();
        this.info = json.get("info").toJSONObject();
    }

    public Integer getNumThreads() {
        return numThreads;
    }

    public void setNumThreads(Integer numThreads) {
        this.numThreads = numThreads;
    }

    public String getScriptVersion() {
        return scriptVersion;
    }

    public void setScriptVersion(String scriptVersion) {
        this.scriptVersion = scriptVersion;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public JSONObject getInfo() {
        return info;
    }

    public void setInfo(JSONObject info) {
        this.info = info;
    }

    // **************************************************************************
    // ** get
    // **************************************************************************
    /**
     * Used to find a DocumentComparisonTest using a given set of constraints.
     * Example: DocumentComparisonTest obj =
     * DocumentComparisonTest.get("num_threads=", num_threads);
     */
    public static DocumentComparisonTest get(Object... args) throws SQLException {
        final Object obj = _get(DocumentComparisonTest.class, args);
        return obj == null ? null : (DocumentComparisonTest) obj;
    }

    // **************************************************************************
    // ** find
    // **************************************************************************
    /**
     * Used to find DocumentComparisonTests using a given set of constraints.
     */
    public static DocumentComparisonTest[] find(Object... args) throws SQLException {
        final Object[] obj = _find(DocumentComparisonTest.class, args);
        final DocumentComparisonTest[] arr = new DocumentComparisonTest[obj.length];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = (DocumentComparisonTest) obj[i];
        }
        return arr;
    }
}