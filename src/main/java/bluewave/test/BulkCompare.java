package bluewave.test;

import bluewave.Config;
import bluewave.app.Document;
import bluewave.utils.Python;
import static bluewave.utils.Python.getScripts;
import bluewave.utils.StatusLogger;
import bluewave.web.services.DocumentService;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javaxt.express.utils.DbUtils;
import javaxt.io.Directory;
import javaxt.io.File;
import javaxt.io.Jar;
import javaxt.json.JSONObject;
import javaxt.sql.Connection;
import javaxt.sql.Database;
import static javaxt.utils.Console.*;
import javaxt.utils.ThreadPool;
import java.sql.SQLException;
import javaxt.sql.Recordset;

public class BulkCompare {

    public static String scriptVersion;

    public static void benchmark(HashMap<String, String> args) throws Exception {
        Config.initDatabase();

        // Check if should update schema
        updateSchema(Config.getDatabase());

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
        try{
            conn = Config.getDatabase().getConnection();
            for (String table : new String[]{
                "APPLICATION.DOCUMENT_COMPARISON_STATS",
                "APPLICATION.DOCUMENT_COMPARISON_TEST",
                "APPLICATION.DOCUMENT_COMPARISON"
            }){
                conn.execute("delete from " + table);
            }
            conn.close();
        }
        catch(Exception e){
            if (conn!=null) conn.close();
            e.printStackTrace();
        }



        //Delete json sidecar files
        final Directory documentDirectory = new Directory(dir);
        List<Object> docs = documentDirectory.getChildren(true, "*.jsoncached");
        File file = null;
        for (Object obj : docs) {
            if (obj instanceof File) {
                file = (File) obj;
                file.delete();
            }
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

        Config.initDatabase();

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

        final Directory documentDirectory = new Directory(dir);
        List<String> documents = getDocumentListSorted(documentDirectory);

        // Create docIds i.e., bluewave.app.Document
        List<String> docIds = getOrCreateDocumentIds(
                documents.toArray(new String[]{}), Config.getDatabase());

        int n = docIds.size();
        long proposedNumComparisons = ((n * n) - n) / 2;

        AtomicLong docCounter = new AtomicLong(0);
        StatusLogger statusLogger = new StatusLogger(docCounter, new AtomicLong(proposedNumComparisons));

        List<String> reversed = new ArrayList<>();
        reversed.addAll(docIds);
        Collections.reverse(reversed);

        LocalTime start = LocalTime.now();


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



                    LocalTime pairStartTime = LocalTime.now();
                    JSONObject result = DocumentService.getSimilarity(
                            new String[]{a, b},
                            getConnection());
                    LocalTime pairEndTime = LocalTime.now();


                    Recordset rs = getRecordset();
                    rs.addNew();
                    rs.setValue("TEST_ID", documentComparisonTestID);
                    rs.setValue("A_ID", docs[0]);
                    rs.setValue("B_ID", docs[1]);
                    rs.setValue("T", ChronoUnit.SECONDS.between(pairStartTime, pairEndTime));
                    rs.setValue("A_SIZE", Document.get("id=",docs[0]).getFile().getSize());
                    rs.setValue("B_SIZE", Document.get("id=",docs[1]).getFile().getSize());
                    //rs.setValue("INFO", result);
                    rs.update();


                } catch (Exception e) {
                    e.printStackTrace();
                }
                docCounter.incrementAndGet();
            }

            private Recordset getRecordset() throws SQLException{
                Recordset rs = (Recordset) get("rs");
                if (rs==null){
                    rs = new javaxt.sql.Recordset();
                    rs.open("SELECT * FROM APPLICATION.DOCUMENT_COMPARISON_STATS where id=-1", getConnection2(), false);
                    rs.setBatchSize(100);
                    set("rs", rs);
                }
                return rs;
            }

            private Connection getConnection() throws SQLException{
                Connection conn = (Connection) get("conn");
                if (conn==null){
                    conn = Config.getDatabase().getConnection();
                    set("conn", conn);
                }
                return conn;
            }

            private Connection getConnection2() throws SQLException{
                Connection conn = (Connection) get("c2");
                if (conn==null){
                    conn = Config.getDatabase().getConnection();
                    set("c2", conn);
                }
                return conn;
            }

            public void exit(){
                Recordset rs = (Recordset) get("rs");
                if (rs!=null) rs.close();

                Connection conn = (Connection) get("conn");
                if (conn!=null) conn.close();

                conn = (Connection) get("c2");
                if (conn!=null) conn.close();
            }


        }.start();




      //Create entry in DOCUMENT_COMPARISON_TEST
        Long documentComparisonTestID = null;
        javaxt.sql.Connection conn = null;
        try{
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
        }
        catch(Exception e){
            if (conn!=null) conn.close();
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

        LocalTime end = LocalTime.now();
        long hours = ChronoUnit.HOURS.between(start, end);
        long minutes = ChronoUnit.MINUTES.between(start, end) % 60;
        long seconds = ChronoUnit.SECONDS.between(start, end) % 60;
        p("\n");
        p("Total Documents Processed: " + docIds.size());
        p("Total Comparisons: " + comparisons);
        p("Total Time Elapsed: " + hours + " hours " + minutes
                + " minutes " + seconds + " seconds.");

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

    private static String rtrim(String s) {
        int i = s.length() - 1;
        while (i >= 0 && Character.isWhitespace(s.charAt(i))) {
            i--;
        }
        return s.substring(0, i + 1);
    }

    private static void updateSchema(Database database) throws Exception {

        Connection conn = null;
        try {
            conn = database.getConnection();
            
            Boolean exists = false;
            for (javaxt.sql.Table table : Database.getTables(conn)){
                String tableName = table.getName().toUpperCase();
                if (tableName.endsWith("DOCUMENT_COMPARISON_STATS")){
                    exists = true;
                    break;
                }
            }


            if (!exists) {
                p("Schema additions do not exist, updating ..");
                // Get updates from file
                StringBuilder sb = new StringBuilder();
                InputStream in = BulkCompare.class.getResourceAsStream("/benchmark.sql");

                BufferedReader br = new BufferedReader(new InputStreamReader(in));

                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line + System.lineSeparator());
                }


                //Split updates into individual statements
                ArrayList<String> statements = new ArrayList<String>();
                for (String s : sb.toString().split(";")) {

                    StringBuffer str = new StringBuffer();
                    for (String i : s.split("\r\n")) {
                        if (!i.trim().startsWith("--") && !i.trim().startsWith("COMMENT ")) {
                            str.append(i + "\r\n");
                        }
                    }

                    String cmd = str.toString().trim();
                    if (cmd.length() > 0) {
                        statements.add(rtrim(str.toString()) + ";");
                    }
                }

                // Update database schema
                java.sql.Statement stmt = conn.getConnection().createStatement();
                for (String cmd : statements) {
                    stmt.execute(cmd);
                }
                p("Schema updated.");
                stmt.close();
            } 
            
            conn.close();
        } 
        catch (Exception e) {
            if (conn!=null) conn.close();
            throw e;
        }
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