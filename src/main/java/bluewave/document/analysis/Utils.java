package bluewave.document.analysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import document.analysis.models.DocumentComparison;

public class Utils {
	/*
	 * This is a class comment
	 */

	// **************************************************************************
	// ** test
	// **************************************************************************
	public static void test(Map<String, String> args) throws Exception {

		// console.log(args);

		Config.initDatabase();
		final Database database = Config.getDatabase();
		initModels(database);

	}

	// **************************************************************************
	// ** initModels
	// **************************************************************************
	public static void initModels(Database database) throws Exception {

		// Get jar file and schema
		final Jar jar = new Jar(Utils.class);
		final javaxt.io.File jarFile = new javaxt.io.File(jar.getFile());

		// Get schema
		final javaxt.io.Directory models = new javaxt.io.Directory(jarFile.getParentDirectory() + "models");
		if (!models.exists()) {
			throw new Exception("Models directory not found");
		}
		final javaxt.io.File schemaFile = new javaxt.io.File(models + "schema.sql");
		if (!schemaFile.exists()) {
			throw new Exception("Schema not found");
		}

		final String schema = schemaFile.getText();
		// console.log(schemaFile);

		// Split schema into individual statements
		final ArrayList<String> statements = new ArrayList<>();
		for (final String s : schema.split(";")) {

			final StringBuffer str = new StringBuffer();
			for (final String i : s.split("\r\n")) {
				if (!i.trim().startsWith("--") && !i.trim().startsWith("COMMENT ")) {
					str.append(i + "\r\n");
				}
			}

			final String cmd = str.toString().trim();
			if (cmd.length() > 0) {
				statements.add(rtrim(str.toString()) + ";");
			}
		}
		// console.log(statements);

		// Initialize schema (create tables, indexes, etc)
		Connection conn = null;
		try {
			conn = database.getConnection();
			java.sql.Statement stmt = conn.getConnection().createStatement();
			for (final String cmd : statements) {
				try {
					stmt.execute(cmd);
				} catch (final Exception e) {
					console.log(e.getMessage());
					// System.out.println(cmd);
					// throw e;
				}
			}
			stmt.close();
			conn.close();
		} catch (final Exception e) {
			if (conn != null) {
				conn.close();
			}
		}

		// Initialize models
		for (final Class c : jar.getClasses()) {
			if (c.getPackageName().equals("bluewave.document.analysis.models")) {
				// if (c.getPackage().equals(DocumentComparison.class.getPackage())){
				if (javaxt.sql.Model.class.isAssignableFrom(c)) {
					Model.init(c, database.getConnectionPool());
					// console.log(c);
				}
			}
		}

	}

	// **************************************************************************
	// ** deleteDocumentComparison
	// **************************************************************************
	public static void deleteDocumentComparison(HashMap<String, String> args) throws Exception {

		Config.initDatabase();
		for (final DocumentComparison dc : DocumentComparison.find()) {
			dc.delete();
		}
	}

	// **************************************************************************
	// ** deleteDocumentIndex
	// **************************************************************************
	public static void deleteDocumentIndex(HashMap<String, String> args) throws Exception {

		Config.initDatabase();
		for (final DocumentComparison dc : DocumentComparison.find()) {
			dc.delete();
		}
		for (final bluewave.app.Document d : bluewave.app.Document.find()) {
			d.delete();
		}
		for (final bluewave.app.File f : bluewave.app.File.find()) {
			f.delete();
		}

		final javaxt.io.Directory dir = Config.getIndexDir();
		final boolean deletedDir = dir.delete();
		if (deletedDir) {
			System.out.println("Sucessfully deleted index: " + dir);
		} else {
			System.out.println("Failed to delete index: " + dir);
		}

	}

	private static String rtrim(String s) {
		int i = s.length() - 1;
		while ((i >= 0) && Character.isWhitespace(s.charAt(i))) {
			i--;
		}
		return s.substring(0, i + 1);
	}
}