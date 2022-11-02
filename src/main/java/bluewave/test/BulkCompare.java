package bluewave.test;

import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import sun.nio.ch.ThreadPool;
import web.services.DocumentService;

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
		} catch (final Exception e) {
			e.printStackTrace();
			return;
		}

		if (threadsNum == -1 || dir == null) {
			p("params needed: -threads & -dir");
			return;
		}

		// Clean up comparison tables
		javaxt.sql.Connection conn = null;
		try {
			conn = Config.getDatabase().getConnection();
			for (final String table : new String[] { "APPLICATION.DOCUMENT_COMPARISON_SIMILARITY",
					"APPLICATION.DOCUMENT_COMPARISON_STATS", "APPLICATION.DOCUMENT_COMPARISON_TEST",
					"APPLICATION.DOCUMENT_COMPARISON" }) {
				conn.execute("delete from " + table);
			}
			conn.close();
		} catch (final Exception e) {
			if (conn != null) {
				conn.close();
			}
			e.printStackTrace();
		}

		boolean deleteCached = false;
		try {
			deleteCached = Boolean.valueOf(args.get("-deleteCached"));
		} catch (final Exception e) {
			e.printStackTrace();
		}

		p("deleteCached: " + deleteCached);
		if (deleteCached) {
			// Delete json sidecar files
			final Directory documentDirectory = new Directory(dir);
			List<Object> docs = documentDirectory.getChildren(true, "*.jsoncached");
			p("cached docs found: " + docs.size());
			File file = null;
			for (final Object obj : docs) {
				if (obj instanceof File) {
					file = (File) obj;
					file.delete();
				}
			}
			p("Cache deleted.");
			// Get python script
			final String scriptName = "compare_pdfs.py";
			final javaxt.io.File[] pyFiles = getScripts(scriptName);
			if (pyFiles.length == 0) {
				p("Script not found: " + scriptName);
				return;
			}

			// Create jsoncached files
			final javaxt.io.File script = pyFiles[0];

			// Create thread pool used to create jsoncached files
			final ThreadPool pool = new ThreadPool(threadsNum) {
				public void process(Object obj) {
					// Compile command line options
					final ArrayList<String> params = new ArrayList<>();
					params.add("--sidecar_only");
					params.add("-f");
					params.add(((File) obj).toString());
					try {
						executeScript(script, params);
					} catch (final Exception e) {
						e.printStackTrace();
					}

				}
			}.start();
			final java.time.Instant start = java.time.Instant.now();

			// Add docs to pool
			file = null;
			docs = documentDirectory.getChildren(true, "*.pdf");
			for (final Object obj : docs) {
				if (obj instanceof File) {
					file = (File) obj;
					pool.add(file);
				}
			}

			pool.done();

			try {
				pool.join();
			} catch (final Exception e) {
				e.printStackTrace();
			}

			final java.time.Instant end = java.time.Instant.now();

			final Duration duration = Duration.between(start, end);
			final long days = duration.toDaysPart();
			final long hours = duration.toHoursPart();
			final long minutes = duration.toMinutesPart();
			final long seconds = duration.toSecondsPart();
			final long millis = duration.toMillisPart();
			p("\n\n\r");
			p("Threads: " + threadsNum);
			p("Total Documents Processed: " + docs.size());
			p("Total Sidecar Creation Time Elapsed:  " + days + " days, " + hours + " hours, " + minutes + " minutes, "
					+ seconds + " seconds, " + millis + " milliseconds.");
			p("\n\r");
		}

		// Run comparison
		compare(args);
	}

	/**
	 * Example: java -jar target/bluewave-dev.jar -config config.json -compare
	 * Compare -threads 4 -dir "/Users/share/docs"
	 *
	 * @param args HashMap of values, '-threads' & '-dir' args must have 2 keys
	 *             -threads - the number of threads in pool; -dir - documents
	 *             storage directory;
	 *
	 */
	public static void compare(HashMap<String, String> args) throws Exception {

		// Config.initDatabase();
		// Utils.initModels(Config.getDatabase());
		final int threadsNum;
		final String scriptVer = getScriptVersion();

		String dir = null;
		final String host;
		final String docIdSource;
		try {
			threadsNum = Integer.parseInt(args.get("-threads"));
			dir = args.get("-dir");
			host = args.get("-host");
			docIdSource = args.get("-source");
		} catch (final Exception e) {
			e.printStackTrace();
			return;
		}

		if (threadsNum == -1 || dir == null || docIdSource == null) {
			p("params needed: -threads & -dir & -source");
			return;
		}

		List<String> docIds = null;

		final Directory documentDirectory = new Directory(dir);
		final List<String> documents = getDocumentListSorted(documentDirectory);

		if (docIdSource.equals("fs")) {
			/**
			 * Create docIds from filesystem
			 */
			docIds = getOrCreateDocumentIds(documents.toArray(new String[] {}), Config.getDatabase());
		} else if (docIdSource.equals("db")) {
			/**
			 * Get docIds from db *
			 */
			docIds = getDocumentIds();
		} else {
			p(" param '-source' not found, value: fs | db");
			return;
		}

		final int n = docIds.size();
		final long proposedNumComparisons = (n * n - n) / 2;

		final AtomicLong docCounter = new AtomicLong(0);
		final StatusLogger statusLogger = new StatusLogger(docCounter, new AtomicLong(proposedNumComparisons));

		final List<String> reversed = new ArrayList<>();
		reversed.addAll(docIds);
		Collections.reverse(reversed);

		final java.time.Instant start = java.time.Instant.now();

		// Create thread pool used to run comparisons and record runtimes
		final ThreadPool pool = new ThreadPool(threadsNum) {
			public void process(Object obj) {
				final Object[] arr = (Object[]) obj;
				try {
					final Long documentComparisonTestID = (Long) arr[0];
					final String a = (String) arr[1];
					final String b = (String) arr[2];

					// Get Documents
					final String[] docs = new String[] { a, b };

					final java.time.Instant pairStartTime = java.time.Instant.now();
					final JSONObject result = DocumentService.getSimilarity(new String[] { a, b }, getConnection());
					final java.time.Instant pairEndTime = java.time.Instant.now();

					final Recordset rs = getRecordset();
					rs.addNew();
					rs.setValue("TEST_ID", documentComparisonTestID);
					rs.setValue("A_ID", docs[0]);
					rs.setValue("B_ID", docs[1]);
					rs.setValue("T", Duration.between(pairStartTime, pairEndTime).toSeconds());
					rs.setValue("A_SIZE", Document.get("id=", docs[0]).getFile().getSize());
					rs.setValue("B_SIZE", Document.get("id=", docs[1]).getFile().getSize());
					// rs.setValue("INFO", result);
					final JSONObject elapsedTimeObj = result.get("elapsed_time_sec").toJSONObject();
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
					final JSONArray files = result.get("files").toJSONArray();
					final Long documentComparisonId = getDocumentComparisonId(files.get(0).get("document_id").toLong(),
							files.get(1).get("document_id").toLong());

					final JSONArray suspiciousPairs = result.get("suspicious_pairs").toJSONArray();
					final Iterator pairIterator = suspiciousPairs.iterator();
					while (pairIterator.hasNext()) {
						final JSONObject pair = (JSONObject) pairIterator.next();
						if (pair.has("pages")) {
							final JSONArray pages = pair.get("pages").toJSONArray();
							if (pages.length() >= 2) {
								final JSONObject pageA = pages.get(0).toJSONObject();
								final JSONObject pageB = pages.get(1).toJSONObject();
								final Recordset rss = getRecordsetSimilarities();
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

				} catch (final Exception e) {
					e.printStackTrace();
				}
				docCounter.incrementAndGet();
			}

			private Long getDocumentComparisonId(Long aId, Long bId) {
				if (aId != null && bId != null) {
					final String cacheQuery = "select ID from APPLICATION.DOCUMENT_COMPARISON " + "where (A_ID=" + aId
							+ " AND B_ID=" + bId + ") " + "OR (A_ID=" + bId + " AND B_ID=" + aId + ")";
					try {
						final Recordset rs = getRecordsetDC();
						rs.open(cacheQuery, getConnection4());
						while (rs.hasNext()) {
							final Long id = rs.getValue("ID").toLong();
							return id;
						}
					} catch (final Exception e) {
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
					rs.open("SELECT * FROM APPLICATION.DOCUMENT_COMPARISON_SIMILARITY where id=-1", getConnection3(),
							false);
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
					Connection conn1;
					try {
						conn1 = Config.getDatabase().getConnection();
					} finally {
						conn.close();
					}
					set("conn", conn1);
					return conn1;
				}
				return conn;
			}

			private Connection getConnection2() throws SQLException {
				Connection conn = (Connection) get("c2");
				if (conn == null) {
					Connection conn2;
					try {
						conn2 = Config.getDatabase().getConnection();
					} finally {
						conn.close();
					}
					set("c2", conn2);

					return conn2;
				}
				return conn;
			}

			private Connection getConnection3() throws SQLException {
				Connection conn = (Connection) get("c3");
				if (conn == null) {
					Connection conn3;
					try {
						conn3 = Config.getDatabase().getConnection();
					} finally {
						conn.close();
					}
					set("c3", conn3);

					return conn3;
				}
				return conn;
			}

			private Connection getConnection4() throws SQLException {
				Connection conn = (Connection) get("c4");
				if (conn == null) {
					Connecion conn4;
					try {
						conn4 = Config.getDatabase().getConnection();
					} finally {
						conn.close();
					}
					set("c4", conn4);

					return conn4;
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

		// Create entry in DOCUMENT_COMPARISON_TEST
		Long documentComparisonTestID = null;
		javaxt.sql.Connection conn = null;
		try {
			conn = Config.getDatabase().getConnection();
			final javaxt.sql.Recordset rs = new javaxt.sql.Recordset();
			rs.open("SELECT * FROM APPLICATION.DOCUMENT_COMPARISON_TEST where id=-1", conn, false); // Set "ReadOnly"
																									// flag to false
			rs.addNew();
			rs.setValue("NUM_THREADS", threadsNum);
			rs.setValue("SCRIPT_VERSION", scriptVer);
			rs.setValue("HOST", host);
			rs.update();
			documentComparisonTestID = rs.getGeneratedKey().toLong();
			rs.close();
			conn.close();
		} catch (final Exception e) {
			if (conn != null) {
				conn.close();
			}
			e.printStackTrace();
		}

		// Populate thread pool and run comparisons
		int comparisons = 0;
		for (final String itemA : docIds) {
			reversed.remove(reversed.size() - 1);
			for (final String itemZ : reversed) {
				comparisons++;
				pool.add(new Object[] { documentComparisonTestID, itemA, itemZ });
			}
		}

		pool.done();

		try {
			pool.join();
			statusLogger.shutdown();
		} catch (final Exception e) {
			e.printStackTrace();
		}

		final java.time.Instant end = java.time.Instant.now();

		final Duration duration = Duration.between(start, end);
		final long days = duration.toDaysPart();
		final long hours = duration.toHoursPart();
		final long minutes = duration.toMinutesPart();
		final long seconds = duration.toSecondsPart();
		final long millis = duration.toMillisPart();

		p("\n");
		p("Threads: " + threadsNum);
		p("Total Documents Processed: " + docIds.size());
		p("Total Comparisons: " + comparisons);
		p("Total Time Elapsed:  " + days + " days, " + hours + " hours, " + minutes + " minutes, " + seconds
				+ " seconds, " + millis + " milliseconds.");

	}

	static List<String> getDocumentListSorted(Directory uploadDirectory) {
		final List<String> documents = new ArrayList<>();
		final List<Object> docs = uploadDirectory.getChildren(true, "*.pdf");
		final Iterator<Object> it = docs.iterator();
		File file = null;
		while (it.hasNext()) {
			final Object obj = it.next();
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
		final List<bluewave.app.File> files = new ArrayList<>();
		for (final String doc : documents) {
			files.add(DocumentService.getOrCreateFile(new javaxt.io.File(doc),
					DocumentService.getOrCreatePath(new Directory(new javaxt.io.File(doc).getPath()))));
		}

		final List<bluewave.app.Document> bDocs = new ArrayList<>();
		for (final bluewave.app.File file : files) {
			bDocs.add(DocumentService.getOrCreateDocument(file));
		}

		final List<String> docIds = new ArrayList<>();
		for (final bluewave.app.Document tempDoc : bDocs) {
			docIds.add(tempDoc.getID().toString());
		}

		return docIds;
	}

	private static List<String> getDocumentIds() {
		final List<String> docIds = new ArrayList<>();
//        String query = "select ID from APPLICATION.DOCUMENT";
		final String query = "SELECT DOCUMENT.ID FROM APPLICATION.DOCUMENT JOIN APPLICATION.FILE "
				+ "ON APPLICATION.DOCUMENT.FILE_ID=APPLICATION.FILE.ID where size<(200*1024*1024)";

		try {
			final Connection conn = Config.getDatabase().getConnection();
			final Recordset rs = new javaxt.sql.Recordset();
			rs.open(query, conn);
			while (rs.hasNext()) {
				docIds.add(rs.getValue("ID").toString());
				rs.moveNext();
			}
		} catch (final Exception e) {
			e.printStackTrace();
		}
		p("documents size: " + docIds.size());
		return docIds;
	}

	private static String getScriptVersion() {
		if (scriptVersion == null) {
			// Get python script
			final String scriptName = "compare_pdfs.py";
			final javaxt.io.File[] pyFiles = getScripts(scriptName);
			if (pyFiles.length == 0) {
				return "n/a";
			}
			final javaxt.io.File script = pyFiles[0];
			try {
				scriptVersion = Python.getScriptVersion(script);
			} catch (final Exception e) {
				e.printStackTrace();
				return "n/a";
			}
		}
		return scriptVersion;
	}

	/**
	 * @param args
	 * @throws Exception
	 */
	public static void exportReport(HashMap<String, String> args) throws Exception {

		final javaxt.io.Directory dir = new javaxt.io.Directory("temp/report/");
		final String reportName = "similarity_report_" + new javaxt.utils.Date() + ".csv";
		final javaxt.io.File file = new javaxt.io.File(dir, reportName.toLowerCase().replaceAll(" ", "_"));
		file.create();
		final java.io.BufferedWriter out = file.getBufferedWriter("UTF-8");

		Config.initDatabase();
		Connection conn = null;

		try {
			conn = Config.getDatabase().getConnection();
			final Recordset rs = DocumentService.getSimilarityRecordSet(conn);

			boolean addHeader = true;
			while (rs.hasNext()) {
				final Field[] fields = rs.getFields();
				if (addHeader) {
					for (int i = 0; i < fields.length; i++) {
						if (i > 0) {
							out.write(",");
						}
						out.write(fields[i].getName());
					}
					addHeader = false;
				}

				out.write("\n");
				for (int i = 0; i < fields.length; i++) {
					if (i > 0) {
						out.write(",");
					}
					String val = fields[i].getValue().toString();
					if (val != null) {
						if (val.contains(",")) {
							val = "\"" + val + "\"";
						}
						out.write(val);
					}
				}

				rs.moveNext();
			}
		} catch (final Exception e) {
			e.printStackTrace();
		} finally {

			try {
				if (file != null) {
					p("File: " + file.getPath() + file.getName());
				}
				if (conn != null) {
					conn.close();
				}
				if (out != null) {
					out.close();
				}
			} catch (final Exception e) {
				e.printStackTrace();
			}
		}
	}

}
