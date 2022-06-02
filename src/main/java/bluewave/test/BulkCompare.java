package bluewave.test;

import bluewave.Config;
import bluewave.utils.StatusLogger;
import bluewave.web.services.DocumentService;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javaxt.io.Directory;
import javaxt.io.File;
import static javaxt.utils.Console.*;
import javaxt.utils.ThreadPool;

public class BulkCompare {

    /**
     * Example:
     * java -jar target/bluewave-dev.jar -config config.json -compare Compare -threads 4 -dir "/Users/share/docs"
     *
     * @param args HashMap of values, '-threads' & '-dir'
     *  args must have 2 keys
     *      -threads -  the number of threads in pool
     *      -dir -  documents storage directory
     *
     */
    public static void compare(HashMap<String, String> args) throws Exception {

        Config.initDatabase();

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

        final Directory documentDirectory = new Directory(dir);
        List<String> documents = getDocumentListSorted(documentDirectory);
        int n = documents.size();
        long proposedNumComparisons = ((n * n) - n) / 2;

        AtomicLong docCounter = new AtomicLong(0);
        StatusLogger statusLogger = new StatusLogger(docCounter, new AtomicLong(proposedNumComparisons));

        List<String> reversed = new ArrayList<>();
        reversed.addAll(documents);
        Collections.reverse(reversed);

        LocalTime start = LocalTime.now();
        ThreadPool pool = new ThreadPool(threadsNum) {
            public void process(Object obj) {
                try {
                    String[] docs = (String[]) obj;
                    DocumentService.getSimilarity2(
                            docs,
                            Config.getDatabase());

                } catch (Exception e) {
                    e.printStackTrace();
                }
                docCounter.incrementAndGet();
            }
        }.start();

        int comparisons = 0;
        for (String itemA : documents) {
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
        p("Total Documents Processed: " + documents.size());
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
}
