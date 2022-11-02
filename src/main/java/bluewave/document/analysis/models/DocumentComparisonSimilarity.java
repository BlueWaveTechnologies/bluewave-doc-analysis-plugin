package bluewave.document.analysis.models;

import java.sql.SQLException;

//******************************************************************************
//**  DocumentComparisonSimilarity Class
//******************************************************************************
/**
 * Used to represent a DocumentComparisonSimilarity
 *
 ******************************************************************************/

public class DocumentComparisonSimilarity extends javaxt.sql.Model {

    private String type;
    private Integer a_page;
    private Integer b_page;
    private Integer importance;
    private DocumentComparison comparison;

    // **************************************************************************
    // ** Constructor
    // **************************************************************************
    public DocumentComparisonSimilarity() {
        super("application.document_comparison_similarity", java.util.Map.ofEntries(

                java.util.Map.entry("type", "type"), java.util.Map.entry("a_page", "a_page"),
                java.util.Map.entry("b_page", "b_page"), java.util.Map.entry("importance", "importance"),
                java.util.Map.entry("comparison", "comparison_id")

        ));

    }

    // **************************************************************************
    // ** Constructor
    // **************************************************************************
    /**
     * Creates a new instance of this class using a record ID in the database.
     */
    public DocumentComparisonSimilarity(long id) throws SQLException {
        this();
        init(id);
    }

    // **************************************************************************
    // ** Constructor
    // **************************************************************************
    /**
     * Creates a new instance of this class using a JSON representation of a
     * DocumentComparisonSimilarity.
     */
    public DocumentComparisonSimilarity(JSONObject json) {
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
            this.type = getValue(rs, "type").toString();
            this.a_page = getValue(rs, "a_page").toInteger();
            this.b_page = getValue(rs, "b_page").toInteger();
            this.importance = getValue(rs, "importance").toInteger();
            final Long comparisonID = getValue(rs, "comparison_id").toLong();

            // Set comparison
            if (comparisonID != null) {
                comparison = new DocumentComparison(comparisonID);
            }

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
     * DocumentComparisonSimilarity.
     */
    public void update(JSONObject json) {

        final Long id = json.get("id").toLong();
        if (id != null && id > 0) {
            this.id = id;
        }
        this.type = json.get("type").toString();
        this.a_page = json.get("a_page").toInteger();
        this.b_page = json.get("b_page").toInteger();
        this.importance = json.get("importance").toInteger();
        if (json.has("comparison")) {
            comparison = new DocumentComparison(json.get("comparison").toJSONObject());
        } else if (json.has("comparisonID")) {
            try {
                comparison = new DocumentComparison(json.get("comparisonID").toLong());
            } catch (final Exception e) {
            }
        }
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getA_page() {
        return a_page;
    }

    public void setA_page(Integer a_page) {
        this.a_page = a_page;
    }

    public Integer getB_page() {
        return b_page;
    }

    public void setB_page(Integer b_page) {
        this.b_page = b_page;
    }

    public Integer getImportance() {
        return importance;
    }

    public void setImportance(Integer importance) {
        this.importance = importance;
    }

    public DocumentComparison getComparison() {
        return comparison;
    }

    public void setComparison(DocumentComparison comparison) {
        this.comparison = comparison;
    }

    // **************************************************************************
    // ** get
    // **************************************************************************
    /**
     * Used to find a DocumentComparisonSimilarity using a given set of constraints.
     * Example: DocumentComparisonSimilarity obj =
     * DocumentComparisonSimilarity.get("type=", type);
     */
    public static DocumentComparisonSimilarity get(Object... args) throws SQLException {
        final Object obj = _get(DocumentComparisonSimilarity.class, args);
        return obj == null ? null : (DocumentComparisonSimilarity) obj;
    }

    // **************************************************************************
    // ** find
    // **************************************************************************
    /**
     * Used to find DocumentComparisonSimilaritys using a given set of constraints.
     */
    public static DocumentComparisonSimilarity[] find(Object... args) throws SQLException {
        final Object[] obj = _find(DocumentComparisonSimilarity.class, args);
        final DocumentComparisonSimilarity[] arr = new DocumentComparisonSimilarity[obj.length];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = (DocumentComparisonSimilarity) obj[i];
        }
        return arr;
    }
}