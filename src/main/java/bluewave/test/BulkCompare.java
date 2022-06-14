package bluewave.test;

import bluewave.Config;
import bluewave.app.Document;
import bluewave.utils.Python;
import bluewave.utils.StatusLogger;
import bluewave.web.services.DocumentService;
import static bluewave.utils.Python.*;
import bluewave.document.analysis.Utils;
import bluewave.document.analysis.models.DocumentComparison;

import java.util.*;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import javaxt.io.Directory;
import javaxt.io.File;
import javaxt.json.*;
import javaxt.sql.*;
import static javaxt.utils.Console.*;
import javaxt.utils.ThreadPool;

public class BulkCompare {

    public static String scriptVersion;

    public static void benchmark(HashMap<String, String> args) throws Exception {

        Config.initDatabase();
        Utils.initModels(Config.getDatabase());

        int threadsNum = -1;
        String dir = null;
        try {
            threadsNum = Integer.parseInt(args.get("-threads"));
            dir = args.get("-dir");
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        if (threadsNum == -1 || dir == null) {
            p("params needed: -threads & -dir");
            return;
        }

        //Clean up comparison tables
        javaxt.sql.Connection conn = null;
        try {
            conn = Config.getDatabase().getConnection();
            for (String table : new String[]{
                "APPLICATION.DOCUMENT_COMPARISON_SIMILARITY",
                "APPLICATION.DOCUMENT_COMPARISON_STATS",
                "APPLICATION.DOCUMENT_COMPARISON_TEST",
                "APPLICATION.DOCUMENT_COMPARISON"
            }) {
                conn.execute("delete from " + table);
            }
            conn.close();
        } catch (Exception e) {
            if (conn != null) {
                conn.close();
            }
            e.printStackTrace();
        }

        boolean deleteCached = false;
        try {
            deleteCached = Boolean.valueOf(args.get("-deleteCached"));
        } catch (Exception e) {
            e.printStackTrace();
        }

        p("deleteCached: " + deleteCached);
        if (deleteCached) {
            //Delete json sidecar files
            final Directory documentDirectory = new Directory(dir);
            List<Object> docs = documentDirectory.getChildren(true, "*.jsoncached");
            p("cached docs found: " + docs.size());
            File file = null;
            for (Object obj : docs) {
                if (obj instanceof File) {
                    file = (File) obj;
                    file.delete();
                }
            }
            p("Cache deleted.");
            //Get python script
            String scriptName = "compare_pdfs.py";
            javaxt.io.File[] pyFiles = getScripts(scriptName);
            if (pyFiles.length == 0) {
                p("Script not found: " + scriptName);
                return;
            }

            //Create jsoncached files
            final javaxt.io.File script = pyFiles[0];

            //Create thread pool used to create jsoncached files
            ThreadPool pool = new ThreadPool(threadsNum) {
                public void process(Object obj) {
                    //Compile command line options
                    ArrayList<String> params = new ArrayList<>();
                    params.add("--sidecar_only");
                    params.add("-f");
                    params.add(((File) obj).toString());
                    try {
                        executeScript(script, params);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
            }.start();
            java.time.Instant start = java.time.Instant.now();

            // Add docs to pool
            file = null;
            docs = documentDirectory.getChildren(true, "*.pdf");
            for (Object obj : docs) {
                if (obj instanceof File) {
                    file = (File) obj;
                    pool.add(file);
                }
            }

            pool.done();

            try {
                pool.join();
            } catch (Exception e) {
                e.printStackTrace();
            }

            java.time.Instant end = java.time.Instant.now();

            Duration duration = Duration.between(start, end);
            long days = duration.toDaysPart();
            long hours = duration.toHoursPart();
            long minutes = duration.toMinutesPart();
            long seconds = duration.toSecondsPart();
            long millis = duration.toMillisPart();
            p("\n\n\r");
            p("Threads: " + threadsNum);
            p("Total Documents Processed: " + docs.size());
            p("Total Sidecar Creation Time Elapsed:  " + days + " days, " + hours + " hours, " + minutes
                    + " minutes, " + seconds + " seconds, " + millis + " milliseconds.");
            p("\n\r");
        }

        //Run comparison
        compare(args);
    }

    /**
     * Example: java -jar target/bluewave-dev.jar -config config.json -compare
     * Compare -threads 4 -dir "/Users/share/docs"
     *
     * @param args HashMap of values, '-threads' & '-dir' args must have 2 keys
     * -threads - the number of threads in pool; -dir - documents storage
     * directory;
     *
     */
    public static void compare(HashMap<String, String> args) throws Exception {

        //Config.initDatabase();
        //Utils.initModels(Config.getDatabase());
        final int threadsNum;
        final String scriptVer = getScriptVersion();

        String dir = null;
        final String host;
        try {
            threadsNum = Integer.parseInt(args.get("-threads"));
            dir = args.get("-dir");
            host = args.get("-host");
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        if (threadsNum == -1 || dir == null) {
            p("params needed: -threads & -dir");
            return;
        }

        /**
         * Replace this block w/db call  *
         */
//        final Directory documentDirectory = new Directory(dir);
//        List<String> documents = getDocumentListSorted(documentDirectory);
//
//        // Create docIds i.e., bluewave.app.Document
//        List<String> docIds = getOrCreateDocumentIds(
//                documents.toArray(new String[]{}), Config.getDatabase());
        /**
         * END *
         */
        
        List<String> docIds = getDocumentIds();

        int n = docIds.size();
        long proposedNumComparisons = ((n * n) - n) / 2;

        AtomicLong docCounter = new AtomicLong(0);
        StatusLogger statusLogger = new StatusLogger(docCounter, new AtomicLong(proposedNumComparisons));

        List<String> reversed = new ArrayList<>();
        reversed.addAll(docIds);
        Collections.reverse(reversed);

        java.time.Instant start = java.time.Instant.now();

        //Create thread pool used to run comparisons and record runtimes
        ThreadPool pool = new ThreadPool(threadsNum) {
            public void process(Object obj) {
                Object[] arr = (Object[]) obj;
                try {
                    Long documentComparisonTestID = (Long) arr[0];
                    String a = (String) arr[1];
                    String b = (String) arr[2];

                    // Get Documents
                    String[] docs = new String[]{a, b};

                    java.time.Instant pairStartTime = java.time.Instant.now();
                    JSONObject result = DocumentService.getSimilarity(
                            new String[]{a, b},
                            getConnection());
                    java.time.Instant pairEndTime = java.time.Instant.now();

                    Recordset rs = getRecordset();
                    rs.addNew();
                    rs.setValue("TEST_ID", documentComparisonTestID);
                    rs.setValue("A_ID", docs[0]);
                    rs.setValue("B_ID", docs[1]);
                    rs.setValue("T", Duration.between(pairStartTime, pairEndTime).toSeconds());
                    rs.setValue("A_SIZE", Document.get("id=", docs[0]).getFile().getSize());
                    rs.setValue("B_SIZE", Document.get("id=", docs[1]).getFile().getSize());
                    //rs.setValue("INFO", result);
                    JSONObject elapsedTimeObj = result.get("elapsed_time_sec").toJSONObject();
                    if (elapsedTimeObj != null) {
                        rs.setValue("PY_PAGES_PER_SEC", result.get("pages_per_second").toDouble());
                        rs.setValue("PY_POST_PROC", elapsedTimeObj.get("post_processing").toDouble());
                        rs.setValue("PY_TOTAL_SEC", elapsedTimeObj.get("total_sec").toDouble());
                        rs.setValue("PY_READ_PDF", elapsedTimeObj.get("read_pdf").toDouble());
                        rs.setValue("PY_IMPORTANCE_SCORE", elapsedTimeObj.get("importance_scoring").toDouble());
                    } else {
                        rs.setValue("PY_ELAPSED_TIME_SEC", result.get("elapsed_time_sec").toDouble());
                        rs.setValue("PY_PAGES_PER_SEC", result.get("pages_per_second").toDouble());
                    }
                    rs.update();

                    // Add to DOCUMENT_COMPARISON_SIMILARITY table
                    JSONArray files = result.get("files").toJSONArray();
                    Long documentComparisonId = getDocumentComparisonId(
                            files.get(0).get("document_id").toLong(),
                            files.get(1).get("document_id").toLong());

                    JSONArray suspiciousPairs = result.get("suspicious_pairs").toJSONArray();
                    Iterator pairIterator = suspiciousPairs.iterator();
                    while (pairIterator.hasNext()) {
                        JSONObject pair = (JSONObject) pairIterator.next();
                        if (pair.has("pages")) {
                            JSONArray pages = pair.get("pages").toJSONArray();
                            if (pages.length() >= 2) {
                                JSONObject pageA = pages.get(0).toJSONObject();
                                JSONObject pageB = pages.get(1).toJSONObject();
                                Recordset rss = getRecordsetSimilarities();
                                rss.addNew();
                                rss.setValue("TYPE", pair.get("type"));
                                rss.setValue("A_PAGE", pageA.get("page").toLong());
                                rss.setValue("B_PAGE", pageB.get("page").toLong());
                                rss.setValue("IMPORTANCE", pair.get("importance"));
                                rss.setValue("COMPARISON_ID", documentComparisonId);
                                rss.update();
                            }
                        }
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
                docCounter.incrementAndGet();
            }

            private Long getDocumentComparisonId(Long aId, Long bId) {
                if (aId != null && bId != null) {
                    String cacheQuery
                            = "select ID from APPLICATION.DOCUMENT_COMPARISON "
                            + "where (A_ID=" + aId + " AND B_ID=" + bId + ") "
                            + "OR (A_ID=" + bId + " AND B_ID=" + aId + ")";
                    try {
                        Recordset rs = getRecordsetDC();
                        rs.open(cacheQuery, getConnection4());
                        while (rs.hasNext()) {
                            Long id = rs.getValue("ID").toLong();
                            return id;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                return null;
            }

            private Recordset getRecordset() throws SQLException {
                Recordset rs = (Recordset) get("rs");
                if (rs == null) {
                    rs = new javaxt.sql.Recordset();
                    rs.open("SELECT * FROM APPLICATION.DOCUMENT_COMPARISON_STATS where id=-1", getConnection2(), false);
                    rs.setBatchSize(100);
                    set("rs", rs);
                }
                return rs;
            }

            private Recordset getRecordsetSimilarities() throws SQLException {
                Recordset rs = (Recordset) get("rsSimilarities");
                if (rs == null) {
                    rs = new javaxt.sql.Recordset();
                    rs.open("SELECT * FROM APPLICATION.DOCUMENT_COMPARISON_SIMILARITY where id=-1", getConnection3(), false);
                    rs.setBatchSize(100);
                    set("rsSimilarities", rs);
                }
                return rs;
            }

            private Recordset getRecordsetDC() throws SQLException {
                Recordset rs = (Recordset) get("rsDC");
                if (rs == null) {
                    rs = new javaxt.sql.Recordset();
                    rs.open("SELECT * FROM APPLICATION.DOCUMENT_COMPARISON where id=-1", getConnection3(), false);
                    rs.setBatchSize(100);
                    set("rsDC", rs);
                }
                return rs;
            }

            private Connection getConnection() throws SQLException {
                Connection conn = (Connection) get("conn");
                if (conn == null) {
                    conn = Config.getDatabase().getConnection();
                    set("conn", conn);
                }
                return conn;
            }

            private Connection getConnection2() throws SQLException {
                Connection conn = (Connection) get("c2");
                if (conn == null) {
                    conn = Config.getDatabase().getConnection();
                    set("c2", conn);
                }
                return conn;
            }

            private Connection getConnection3() throws SQLException {
                Connection conn = (Connection) get("c3");
                if (conn == null) {
                    conn = Config.getDatabase().getConnection();
                    set("c3", conn);
                }
                return conn;
            }

            private Connection getConnection4() throws SQLException {
                Connection conn = (Connection) get("c4");
                if (conn == null) {
                    conn = Config.getDatabase().getConnection();
                    set("c4", conn);
                }
                return conn;
            }

            public void exit() {
                Recordset rs = (Recordset) get("rs");
                if (rs != null) {
                    rs.close();
                }
                rs = (Recordset) get("rsSimilarities");
                if (rs != null) {
                    rs.close();
                }
                rs = (Recordset) get("rsDC");
                if (rs != null) {
                    rs.close();
                }
                Connection conn = (Connection) get("conn");
                if (conn != null) {
                    conn.close();
                }

                conn = (Connection) get("c2");
                if (conn != null) {
                    conn.close();
                }

                conn = (Connection) get("c3");
                if (conn != null) {
                    conn.close();
                }
                conn = (Connection) get("c4");
                if (conn != null) {
                    conn.close();
                }
            }

        }.start();

        //Create entry in DOCUMENT_COMPARISON_TEST
        Long documentComparisonTestID = null;
        javaxt.sql.Connection conn = null;
        try {
            conn = Config.getDatabase().getConnection();
            javaxt.sql.Recordset rs = new javaxt.sql.Recordset();
            rs.open("SELECT * FROM APPLICATION.DOCUMENT_COMPARISON_TEST where id=-1", conn, false); //Set "ReadOnly" flag to false
            rs.addNew();
            rs.setValue("NUM_THREADS", threadsNum);
            rs.setValue("SCRIPT_VERSION", scriptVer);
            rs.setValue("HOST", host);
            rs.update();
            documentComparisonTestID = rs.getGeneratedKey().toLong();
            rs.close();
            conn.close();
        } catch (Exception e) {
            if (conn != null) {
                conn.close();
            }
            e.printStackTrace();
        }

        //Populate thread pool and run comparisons
        int comparisons = 0;
        for (String itemA : docIds) {
            reversed.remove(reversed.size() - 1);
            for (String itemZ : reversed) {
                comparisons++;
                pool.add(new Object[]{documentComparisonTestID, itemA, itemZ});
            }
        }

        pool.done();

        try {
            pool.join();
            statusLogger.shutdown();
        } catch (Exception e) {
            e.printStackTrace();
        }

        java.time.Instant end = java.time.Instant.now();

        Duration duration = Duration.between(start, end);
        long days = duration.toDaysPart();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        long millis = duration.toMillisPart();
        p("\n");
        p("Threads: " + threadsNum);
        p("Total Documents Processed: " + docIds.size());
        p("Total Comparisons: " + comparisons);
        p("Total Time Elapsed:  " + days + " days, " + hours + " hours, " + minutes
                + " minutes, " + seconds + " seconds, " + millis + " milliseconds.");

    }

    static List<String> getDocumentListSorted(Directory uploadDirectory) {
        List<String> documents = new ArrayList<>();
        List<Object> docs = uploadDirectory.getChildren(true, "*.pdf");
        Iterator<Object> it = docs.iterator();
        File file = null;
        while (it.hasNext()) {
            Object obj = it.next();
            if (obj instanceof File) {
                file = (File) obj;
                documents.add(file.getPath() + file.getName(true));
            }
        }
        Collections.sort(documents);
        return documents;
    }

    static void p(String text) {
        console.log(text);
    }

    private static List<String> getOrCreateDocumentIds(String[] documents, Database database) {
        List<bluewave.app.File> files = new ArrayList<>();
        for (String doc : documents) {
            files.add(DocumentService.getOrCreateFile(
                    new javaxt.io.File(doc),
                    DocumentService.getOrCreatePath(
                            new Directory(new javaxt.io.File(doc).getPath()))));
        }

        List<bluewave.app.Document> bDocs = new ArrayList<>();
        for (bluewave.app.File file : files) {
            bDocs.add(DocumentService.getOrCreateDocument(file));
        }

        List<String> docIds = new ArrayList<>();
        for (bluewave.app.Document tempDoc : bDocs) {
            docIds.add(tempDoc.getID().toString());
        }

        return docIds;
    }

    private static List<String> getDocumentIds() {
        List<String> docIds = new ArrayList<>();
        String query = "select ID from APPLICATION.DOCUMENT";
        try {
            Connection conn = Config.getDatabase().getConnection();
            Recordset rs = new javaxt.sql.Recordset();
            rs.open(query, conn);
            while (rs.hasNext()) {
                docIds.add(rs.getValue("ID").toString());
                rs.moveNext();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        p("documents size: " + docIds.size());
        return docIds;
    }

    private static String getScriptVersion() {
        if (scriptVersion == null) {
            //Get python script
            String scriptName = "compare_pdfs.py";
            javaxt.io.File[] pyFiles = getScripts(scriptName);
            if (pyFiles.length == 0) {
                return "n/a";
            }
            javaxt.io.File script = pyFiles[0];
            try {
                scriptVersion = Python.getScriptVersion(script);
            } catch (Exception e) {
                e.printStackTrace();
                return "n/a";
            }
        }
        return scriptVersion;
    }

}
