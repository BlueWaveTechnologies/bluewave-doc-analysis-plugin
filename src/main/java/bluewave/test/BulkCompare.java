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
import java.sql.ResultSet;
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
import javaxt.sql.Model;
import static javaxt.utils.Console.*;
import javaxt.utils.ThreadPool;

public class BulkCompare {

    public static String scriptVersion;

    public static void benchmark(HashMap<String, String> args) throws Exception {
        Config.initDatabase();

        // Check if should update schema
        updateSchema(Config.getDatabase());

        updateModel(Config.getDatabase());

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

        //Delete records from APPLICATION.DOCUMENT_COMPARISON_STATS
        for (bluewave.model.DocumentComparisonStats dc : bluewave.model.DocumentComparisonStats.find()) {
            dc.delete();
        }

        //Delete records from APPLICATION.DOCUMENT_COMPARISON_TEST
        for (bluewave.model.DocumentComparisonTest dc : bluewave.model.DocumentComparisonTest.find()) {
            dc.delete();
        }

        //Delete records from APPLICATION.DOCUMENT_COMPARISON
        for (bluewave.app.DocumentComparison dc : bluewave.app.DocumentComparison.find()) {
            dc.delete();
        }

//        //Delete records from APPLICATION.DOCUMENT
//        for (bluewave.app.Document d : bluewave.app.Document.find()) {
//            d.delete();
//        }
//
//        //Delete records from APPLICATION.File
//        for (bluewave.app.File f : bluewave.app.File.find()) {
//            f.delete();
//        }
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
        ThreadPool pool = new ThreadPool(threadsNum) {
            public void process(Object obj) {
                try {
                    
                    // Get Documents
                    String[] docs = (String[]) obj;

                    javaxt.sql.Connection conn = Config.getDatabase().getConnection();
                    javaxt.sql.Recordset rs = new javaxt.sql.Recordset();
                    rs.open("SELECT * FROM APPLICATION.DOCUMENT_COMPARISON_TEST where id=-1", conn, false); //Set "ReadOnly" flag to false
                    rs.addNew();
                    rs.setValue("NUM_THREADS", threadsNum);
                    rs.setValue("SCRIPT_VERSION", scriptVer);
                    rs.setValue("HOST", host);
                    rs.update();

                    long documentComparisonTestID = rs.getGeneratedKey().toLong();
                    rs.close();
                    
                    LocalTime pairStartTime = LocalTime.now();
                    JSONObject result = DocumentService.getSimilarity(
                            docs,
                            Config.getDatabase());
                    LocalTime pairEndTime = LocalTime.now();
                    
                    rs = new javaxt.sql.Recordset();
                    rs.open("SELECT * FROM APPLICATION.DOCUMENT_COMPARISON_STATS where id=-1", conn, false); //Set "ReadOnly" flag to false
                    rs.setBatchSize(100);
                    rs.addNew();
                    rs.setValue("TEST_ID", documentComparisonTestID);
                    rs.setValue("A_ID", docs[0]);
                    rs.setValue("B_ID", docs[1]);
                    rs.setValue("T", ChronoUnit.SECONDS.between(pairStartTime, pairEndTime));
                    rs.setValue("A_SIZE", Document.get("id=",docs[0]).getFile().getSize());
                    rs.setValue("B_SIZE", Document.get("id=",docs[1]).getFile().getSize());
                    rs.setValue("INFO", result);
                    rs.update();
                    rs.close();

                    conn.close();

                } catch (Exception e) {
                    e.printStackTrace();
                }
                docCounter.incrementAndGet();
            }
        }.start();

        int comparisons = 0;
        for (String itemA : docIds) {
            reversed.remove(reversed.size() - 1);
            for (String itemZ : reversed) {
                comparisons++;
                pool.add(new String[]{itemA, itemZ});
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

    private static void updateSchema(Database database) {

        Connection conn = null;
        Boolean exists = false;
        try {
            conn = database.getConnection();
            try ( java.sql.Statement stmt = conn.getConnection().createStatement()) {
                stmt.execute("SELECT count(*) FROM information_schema.tables WHERE table_name = 'APPLICATION.DOCUMENT_COMPARISON_TEST'");
                ResultSet result = stmt.getResultSet();
                while (result.next()) {
                    exists = true;
                }
                stmt.close();
                conn.close();
            } catch (java.sql.SQLException e) {
                e.printStackTrace();
            } finally {
                if (conn != null) {
                    conn.close();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (!exists) {
            p("Schema additions do not exist, updating ..");
            // Get updates from file
            StringBuilder sb = new StringBuilder();
            try ( InputStream in
                    = BulkCompare.class.getResourceAsStream("/benchmark.sql")) {

                BufferedReader br = new BufferedReader(new InputStreamReader(in));

                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line + System.lineSeparator());
                }
                p(sb.toString());
            } catch (Exception e) {
                e.printStackTrace();
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
            try {
                conn = database.getConnection();
                try {
                    java.sql.Statement stmt = conn.getConnection().createStatement();
                    for (String cmd : statements) {
                        stmt.execute(cmd);
                    }
                    p("Schema updated.");
                    stmt.close();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (conn != null) {
                        conn.close();
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            p("Schema additions already exist.");
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

    private static void updateModel(Database database) throws Exception {
        StringBuilder sb = new StringBuilder();
        try ( InputStream in
                = BulkCompare.class.getResourceAsStream("/benchmark.sql")) {

            BufferedReader br = new BufferedReader(new InputStreamReader(in));

            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line + System.lineSeparator());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        //Initialize schema (create tables, indexes, etc)
        DbUtils.initSchema(database, sb.toString(), null);

        //Inititalize connection pool
        database.initConnectionPool();

        //Initialize models
        javaxt.io.Jar jar = new Jar(BulkCompare.class);
        Model.init(jar, database.getConnectionPool());
    }
}
