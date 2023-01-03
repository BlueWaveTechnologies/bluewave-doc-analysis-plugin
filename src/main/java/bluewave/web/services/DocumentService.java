package bluewave.web.services;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import document.analysis.models.DocumentComparison;
import document.analysis.models.DocumentComparisonSimilarity;
import sun.nio.ch.ThreadPool;
import utils.FileIndex;

//******************************************************************************
//**  DocumentService
//******************************************************************************
/**
 * Used to upload, download, and analyze documents
 *
 ******************************************************************************/

public class DocumentService extends WebService {

    private final ThreadPool pool;
    private FileIndex index;
    private static ConcurrentHashMap<String, JSONObject> scripts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, WebSocketListener> listeners;
    private static AtomicLong webSocketID;

    // **************************************************************************
    // ** Constructor
    // **************************************************************************
    public DocumentService() {

        // Websocket stuff
        webSocketID = new AtomicLong(0);
        listeners = new ConcurrentHashMap<>();
        final DocumentService me = this;
        try {
            Utils.initModels(Config.getDatabase());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Start thread pool used to index files
        final int numThreads = 20;
        final int poolSize = 1000;
        pool = new ThreadPool(numThreads, poolSize) {
            public void process(Object obj) {
                try {
                    final Object[] arr = (Object[]) obj;
                    final javaxt.io.File file = (javaxt.io.Fsile) arr[0];
                    final bluewave.app.Path path = (bluewave.app.Path) arr[1];

                    final bluewave.app.File f = getOrCreateFile(file, path);
                    final bluewave.app.Document doc = getOrCreateDocument(f);
                    String indexStatus = doc.getIndexStatus();
                    if (indexStatus == null && index != null) {
                        try {
                            index.addDocument(doc, file);
                            indexStatus = "indexed";
                            me.notify("indexUpdate," + index.getSize() + "," + Config.getIndexDir().getSize());
                        } catch (Exception e) {
                            indexStatus = "failed";
                        }
                        doc.setIndexStatus(indexStatus);
                        doc.save();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.start();

        // Create index of existing files. Use separate thread so the server doesn't
        // hang
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    index = new FileIndex(Config.getIndexDir());
                    updateIndex();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();

    }

    // **************************************************************************
    // ** createWebSocket
    // **************************************************************************
    public void createWebSocket(HttpServletRequest request, HttpServletResponse response) throws IOException {

        new WebSocketListener(request, response) {
            private Long id;

            public void onConnect() {
                id = webSocketID.incrementAndGet();
                synchronized (listeners) {
                    listeners.put(id, this);
                }
            }

            public void onDisconnect(int statusCode, String reason) {
                synchronized (listeners) {
                    listeners.remove(id);
                }
            }
        };
    }

    // **************************************************************************
    // ** notify
    // **************************************************************************
    private void notify(String msg) {
        synchronized (listeners) {
            final Iterator<Long> it = listeners.keySet().iterator();
            while (it.hasNext()) {
                final Long id = it.next();
                final WebSocketListener ws = listeners.get(id);
                ws.send(msg);
            }
        }
    }

    // **************************************************************************
    // ** getServiceResponse
    // **************************************************************************
    public ServiceResponse getServiceResponse(ServiceRequest request, Database database) throws ServletException {

        String method = request.getMethod();
        if (!method.isBlank()) {
            return super.getServiceResponse(request, database);
        }
        method = request.getRequest().getMethod();
        final bluewave.app.User user = (bluewave.app.User) request.getUser();

        if ("GET".equals(method)) {
            if (request.hasParameter("id")) {
                return getFile(request, user);
            }
            return getDocuments(request, database);
        }
        if (!"POST".equals(method)) {
            return new ServiceResponse(501, "Not implemented");
        }
        final Boolean uploadEnabled = Config.get("webserver").get("uploadEnabled").toBoolean();
        if (uploadEnabled == true) {
            return uploadFile(request, user);
        }
        return new ServiceResponse(501, "Not implemented");
    }

    // **************************************************************************
    // ** getIndex
    // **************************************************************************
    /**
     * Returns index metadata
     */
    public ServiceResponse getIndex(ServiceRequest request, Database database) throws ServletException {
        final bluewave.app.User user = (bluewave.app.User) request.getUser();
        if (user.getAccessLevel() < 5) {
            return new ServiceResponse(401, "Not Authorized");
        }
        final javaxt.io.Directory dir = Config.getIndexDir();
        final JSONObject json = new JSONObject();
        json.set("path", dir.toString());
        json.set("size", dir.getSize());
        json.set("count", index.getSize());
        return new ServiceResponse(json);
    }

    // **************************************************************************
    // ** getDocuments
    // **************************************************************************
    /**
     * Returns a csv document with a list of documents in the database
     */
    private ServiceResponse getDocuments(ServiceRequest request, Database database) throws ServletException {

        // Parse request
        Long offset = request.getOffset();
        if (offset == null || offset < 0) {
            offset = 0L;
        }
        Long limit = request.getLimit();
        if (limit == null || limit < 1) {
            limit = 50L;
        }
        String orderBy = request.getParameter("orderby").toString();
        if (orderBy == null) {
            orderBy = "name";
        }
        final String[] q = request.getRequest().getParameterValues("q");
        Boolean remote = request.getParameter("remote").toBoolean();
        if (remote == null) {
            remote = false;
        }

        // Start compiling response
        final StringBuilder str = new StringBuilder();
        str.append("id,name,type,date,size");

        if (remote) {

            str.append(",info");

            String url = "https://i2ksearch-mig.fda.gov/query/i2k/?version=2.2&wt=json&json.nl=arrarr";
            for (final String s : q) {
                url += "&q=" + encode(s);
            }
            url += "&fq=folder_type:" + encode("(PMN)");
            // url += "&fq=!folder_type:" + encode("(DocMan)");
            url += "&start=" + offset + "&rows=" + limit + "&hl=true&hl.fl=pages&hl.fragsize=85&hl.snippets=4&"; // text
                                                                                                                    // highlighting

            final javaxt.http.Response response = getResponse(url);
            if (response.getStatus() != 200) {
                return new ServiceResponse(500, response.getText());
            }
            final JSONObject json = new JSONObject(response.getText());
            final JSONArray docs = json.get("response").get("docs").toJSONArray();
            for (int i = 0; i < docs.length(); i++) {
                final JSONObject doc = docs.get(i).toJSONObject();
                final String id = doc.get("id").toString();
                final String contentType = doc.get("a_content_type").toString();
                final String folderID = doc.get("folder_id").toString();
                String folderSubType = doc.get("folder_sub_type").toString();
                if (folderSubType == null) {
                    folderSubType = "UNKNOWN";
                }
                final String name = folderID + "/" + folderSubType + "/" + id + "." + contentType;

                final Long size = doc.get("contentSize").toLong();
                final String dt = doc.get("r_creation_date").toString();
                final JSONArray pages = doc.get("pages").toJSONArray();
                String highlightFragment = pages.length() == 0 ? null : pages.get(0).toString();
                for (int j = 0; j < pages.length(); j++) {
                    final String page = pages.get(j).toString();
                    if (page == null) {
                        continue;
                    }
                    boolean foundMatch = false;
                    final String p = page.toLowerCase(Locale.ROOT);
                    for (final String s : q) {
                        int idx = p.indexOf(s.toLowerCase(Locale.ROOT));
                        if (idx > -1) {

                            String a = page.substring(0, idx);
                            final String b = page.substring(idx, idx + s.length());
                            final String c = page.substring(idx + 1 + s.length());

                            idx = a.lastIndexOf(" ");
                            if (idx > -1) {
                                a = a.substring(idx);
                                if (a.length() > 10) {
                                    a = a.substring(a.length() - 10);
                                }
                            }

                            highlightFragment = a + "<b>" + b + "</b>" + c;
                            if (highlightFragment.length() > 400) {
                                highlightFragment = highlightFragment.substring(0, 400);
                            }

                            foundMatch = true;
                            break;
                        }
                    }
                    if (foundMatch) {
                        break;
                    }
                }

                final JSONObject searchMetadata = new JSONObject();
                // searchMetadata.set("score", score);
                // searchMetadata.set("frequency", frequency);
                searchMetadata.set("highlightFragment", highlightFragment);
                // searchMetadata.set("explainDetails", explainDetails);

                str.append("\n");
                str.append(id);
                str.append(",");
                str.append(name);
                str.append(",");
                str.append("Remote");
                str.append(",");
                str.append(dt);
                str.append(",");
                str.append(size);
                str.append(",");
                str.append(encode(searchMetadata));
            }
        } else {

            // Compile sql statement
            final StringBuilder sql = new StringBuilder();
            sql.append("select document.id, file.name, file.type, file.date, file.size ");
            sql.append("from APPLICATION.FILE JOIN APPLICATION.DOCUMENT ");
            sql.append("ON APPLICATION.FILE.ID=APPLICATION.DOCUMENT.FILE_ID ");
            final HashMap<Long, JSONObject> searchMetadata = new HashMap<>();
            if (q != null) {
                try {

                    final List<String> searchTerms = new ArrayList<>();
                    for (final String s : q) {
                        searchTerms.add(s);
                    }

                    final TreeMap<Float, ArrayList<bluewave.app.Document>> results = index.findDocuments(searchTerms,
                            Math.toIntExact(limit));

                    if (results.isEmpty()) {
                        return new ServiceResponse(str.toString());
                    }
                    String documentIDs = "";
                    final Iterator<Float> it = results.descendingKeySet().iterator();
                    while (it.hasNext()) {
                        final float score = it.next();
                        final ArrayList<bluewave.app.Document> documents = results.get(score);
                        for (final bluewave.app.Document document : documents) {
                            if (documentIDs.length() > 0) {
                                documentIDs += ",";
                            }
                            documentIDs += document.getID() + "";

                            final JSONObject info = document.getInfo();
                            if (info != null) {
                                final JSONObject md = info.get("searchMetadata").toJSONObject();
                                if (md != null) {
                                    searchMetadata.put(document.getID(), md);
                                }
                            }
                        }
                    }
                    sql.append("WHERE document.id in (");
                    sql.append(documentIDs);
                    sql.append(")");
                } catch (Exception e) {
                    e.printStackTrace();
                    return new ServiceResponse(e);
                }
            }

            if (orderBy != null) {
                sql.append(" ORDER BY " + orderBy);
            }
            if (offset != null) {
                sql.append(" OFFSET " + offset);
            }
            sql.append(" LIMIT " + limit);

            if (!searchMetadata.isEmpty()) {
                str.append(",info");
            }

            // Execute query and update response
            Connection conn = null;
            try {
                conn = database.getConnection();
                final Recordset rs = new Recordset();
                rs.open(sql.toString(), conn);
                while (rs.hasNext()) {
                    str.append("\n");
                    str.append(getString(rs));
                    if (!searchMetadata.isEmpty()) {
                        str.append(",");
                        final JSONObject md = searchMetadata.get(rs.getValue("id").toLong());
                        str.append(encode(md));
                    }
                    rs.moveNext();
                }
                rs.close();
                conn.close();
            } catch (Exception e) {
                if (conn != null) {
                    conn.close();
                }
                return new ServiceResponse(e);
            } finally {
                if (conn != null) {
                    conn.close();
                }
            }
        }

        return new ServiceResponse(str.toString());
    }

    private String getString(Recordset rs) {
        final Long id = rs.getValue("id").toLong();
        String name = rs.getValue("name").toString();
        // String type=rs.getValue("type").toString();
        final String type = "Local";
        final javaxt.utils.Date date = rs.getValue("date").toDate();
        final String dt = date == null ? "" : date.toISOString();
        final Long size = rs.getValue("size").toLong();
        if (name.contains(",")) {
            name = "\"" + name + "\"";
        }
        return id + "," + name + "," + type + "," + dt + "," + size;
    }

    // **************************************************************************
    // ** getFile
    // **************************************************************************
    /**
     * Returns a file from the database. Optionally, can be used to return a url to
     * a remote file
     */
    private ServiceResponse getFile(ServiceRequest request) throws ServletException {

        Boolean remote = request.getParameter("remote").toBoolean();
        if (remote == null) {
            remote = false;
        }

        if (remote) {
            final String id = request.getParameter("id").toString();
            final String url = "https://i2kplus.fda.gov/documentumservice/rest/view/" + id;
            return new ServiceResponse(url);
        }
        try {
            final Long documentID = request.getID();
            final bluewave.app.Document document = new bluewave.app.Document(documentID);
            final javaxt.io.File file = getFile(document);
            if (!file.exists()) {
                return new ServiceResponse(404);
            }
            return new ServiceResponse(file);
        } catch (Exception e) {
            return new ServiceResponse(e);
        }
    }

    // **************************************************************************
    // ** getFolder
    // **************************************************************************
    /**
     * Returns a PDF file containing all files in a document "folder"
     */
    public ServiceResponse getFolder(ServiceRequest request, Database database) throws ServletException {

        try {

            // Parse params
            String folderName = request.getParameter("name").toString();
            if (folderName == null) {
                return new ServiceResponse(400, "folder is required");
            }
            final int idx = folderName.indexOf(".");
            if (idx > 0) {
                folderName = folderName.substring(0, idx);
            }
            final String fileName = folderName + ".pdf";
            Boolean returnID = request.getParameter("returnID").toBoolean();
            if (returnID == null) {
                returnID = false;
            }

            // Find file in the database and return early if possible
            for (final bluewave.app.File f : bluewave.app.File.find("name=", fileName)) {
                final javaxt.io.File file = new javaxt.io.File(f.getPath().getDir() + f.getName());
                if (file.getName().equalsIgnoreCase(fileName) && file.exists()) {
                    if (isValidPDF(file)) {
                        notify("createFolder," + folderName + "," + 1 + "," + 1);
                        if (returnID) {
                            final bluewave.app.Document[] arr = bluewave.app.Document.find("FILE_ID=", f.getID());
                            return new ServiceResponse(arr[0].getID() + "");
                        }
                        return new ServiceResponse(file);
                    }
                }
            }

            final javaxt.io.Directory dir = new javaxt.io.Directory(getUploadDir().toString() + folderName);

            javaxt.io.File file = new javaxt.io.File(dir, fileName);
            if (file.exists() && isValidPDF(file)) {
                notify("createFolder," + folderName + "," + 1 + "," + 1);
                if (!returnID) {
                    return new ServiceResponse(file);
                }
            }

            // Get document IDs associated with the folder
            final TreeMap<Long, String> documentIDs = new TreeMap<>();
            final String url = "https://i2ksearch-mig.fda.gov/query/i2k/?version=2.2&wt=json&json.nl=arrarr"
                    + "&q=folder_id:" + folderName + "&fl=id,folder_id,r_creation_date,a_content_type" + "&start=0"
                    + "&rows=500";

            final javaxt.http.Response response = getResponse(url);
            if (response.getStatus() == 200) {
                final JSONObject json = new JSONObject(response.getText());
                final JSONArray docs = json.get("response").get("docs").toJSONArray();
                for (int i = 0; i < docs.length(); i++) {
                    final JSONObject doc = docs.get(i).toJSONObject();
                    final String id = doc.get("id").toString();
//                  final String folderID = doc.get("folder_id").toString();
                    final String dt = doc.get("r_creation_date").toString();
                    final String contentType = doc.get("a_content_type").toString();
                    if ("pdf".equalsIgnoreCase(contentType)) {
                        documentIDs.put(new javaxt.utils.Date(dt).getTime(), id);
                    }
                }
            }

            final int totalSteps = documentIDs.size() + 1;
            int step = 0;

            // Get component files
            final ArrayList<PDDocument> documents = new ArrayList<>();
            final ArrayList<javaxt.io.File> files = new ArrayList<>();
            final Iterator<Long> it = documentIDs.keySet().iterator();
            while (it.hasNext()) {
                final String id = documentIDs.get(it.next());

                final javaxt.io.File tempFile = new javaxt.io.File(dir, id);
                try {
                    final PDDocument document = downloadPDF(id, tempFile);
                    documents.add(document);
                    files.add(tempFile);
                } catch (Exception e) {

                    // Try downloading the file again
                    try {
                        tempFile.delete();
                        final PDDocument document = downloadPDF(id, tempFile);
                        documents.add(document);
                        files.add(tempFile);
                    } catch (Exception ex) {
                        console.log("Invalid PDF", tempFile);
                        tempFile.rename(tempFile.getName() + ".err");
                    }
                }
                step++;
                notify("createFolder," + folderName + "," + step + "," + totalSteps);
            }

            if (documents.isEmpty()) {
                return new ServiceResponse(500, "No files were downloaded");
            }

            // Merge files into a single PDF
            if (documents.size() == 1) {
                file = files.get(0);
            } else {

                final PDFMergerUtility pdfMergerUtility = new PDFMergerUtility();
                for (final PDDocument document : documents) {

                    try {

                        final PDAcroForm form = document.getDocumentCatalog().getAcroForm();
                        if (form != null) {
                            document.setAllSecurityToBeRemoved(true);
                            try {
                                form.flatten();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            if (form.hasXFA()) {
                                form.setXFA(null);
                            }
                        }

                        final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                        document.save(out);
                        InputStream is = new java.io.ByteArrayInputStream(out.toByteArray());
                        pdfMergerUtility.addSource(is);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                java.io.OutputStream out = file.getOutputStream();
                try {
                    pdfMergerUtility.setDestinationStream(out);
                    pdfMergerUtility.mergeDocuments(null);
                } finally {
                    // TODO: handle finally clause
                    out.close();
                }

            }

            step++;
            notify("createFolder," + folderName + "," + step + "," + totalSteps);

            if (!file.exists() || !isValidPDF(file)) {
                return new ServiceResponse(500, "Failed to merge PDF");
            }
            // Save combined file in the database
            final bluewave.app.Path path = getOrCreatePath(file.getDirectory());
            final bluewave.app.File f = getOrCreateFile(file, path);
            final bluewave.app.Document doc = getOrCreateDocument(f);
            doc.save();

            // Index the file
            synchronized (pool) {
                pool.add(new Object[] { file, path });
                pool.notifyAll();
            }

            // Return response
            if (returnID) {
                return new ServiceResponse(doc.getID() + "");
            }
            return new ServiceResponse(file);
        } catch (Exception e) {
            return new ServiceResponse(e);
        }
    }

    // **************************************************************************
    // ** downloadFile
    // **************************************************************************
    /**
     * Used to download and return a PDF document from a remote server
     */
    private PDDocument downloadPDF(String id, File file) throws Exception {

        if (!file.exists()) {
            file.create();

            final String url = "https://i2kplus.fda.gov/documentumservice/rest/view/" + id;
            final javaxt.http.Response response = getResponse(url);
            if (response.getStatus() == 200) {
                InputStream is = response.getInputStream();
                try {
                    saveFile(is, file);
                } finally {
                    // TODO: handle finally clause
                    is.close();
                }

            }
        }

        try {
            final PDDocument document = PDDocument.load(file.toFile());
            document.getVersion();
            return document;
        } catch (Exception e) {
            e.initCause("Invalid PDF " + file);
        }
    }

    // **************************************************************************
    // ** isValidPDF
    // **************************************************************************
    /**
     * Returns true if the given file is a valid PDF document
     */
    private boolean isValidPDF(javaxt.io.File file) {
        try {
            final PDDocument document = PDDocument.load(file.toFile());
            document.getVersion();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // **************************************************************************
    // ** uploadFile
    // **************************************************************************
    private ServiceResponse uploadFile(ServiceRequest request, bluewave.app.User user) throws ServletException {

        if (user == null || user.getAccessLevel() < 3 && user.getID() != null) {
            return new ServiceResponse(403, "Not Authorized");
        }

        try {
            final JSONArray results = new JSONArray();
            final Iterator<FormInput> it = request.getRequest().getFormInputs();
            while (it.hasNext()) {
                final FormInput input = it.next();
                final String name = input.getName();
                final FormValue value = input.getValue();
                if (input.isFile()) {

                    final JSONObject json = new JSONObject();
                    json.set("name", name);

                    try {
                        uploadFile(name, value.getInputStream(), user);
                        json.set("results", "uploaded");
                    } catch (Exception e) {
                        json.set("results", "error");
                    }

                    results.add(json);
                }
            }
            return new ServiceResponse(results);
        } catch (Exception e) {
            return new ServiceResponse(e);
        }
    }

    // **************************************************************************
    // ** saveFile
    // **************************************************************************
    private void saveFile(InputStream is, File tempFile) throws Exception {

        final int bufferSize = 2048;
        FileOutputStream output = new FileOutputStream(tempFile.toFile());
        ReadableByteChannel inputChannel = Channels.newChannel(is);
        WritableByteChannel outputChannel = Channels.newChannel(output);
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocateDirect(bufferSize);
        int ttl = 0;

        try {
            while (inputChannel.read(buffer) != -1) {
                buffer.flip();
                ttl += outputChannel.write(buffer);
                buffer.compact();
            }
            buffer.flip();
            while (buffer.hasRemaining()) {
                ttl += outputChannel.write(buffer);
            }
        } finally {
            // TODO: handle finally clause
            inputChannel.close();
            outputChannel.close();
            output.close();
        }

        // console.log(ttl);
    }

    // **************************************************************************
    // ** uploadFile
    // **************************************************************************
    private javaxt.io.File uploadFile(String name, InputStream is, bluewave.app.User user) throws Exception {

        // Create temp file
        final javaxt.utils.Date d = new javaxt.utils.Date();
        d.setTimeZone("UTC");
        final javaxt.io.File tempFile = new javaxt.io.File(
                getUploadDir().toString() + user.getID() + "/" + d.toString("yyyy/MM/dd") + "/" + d.getTime() + ".tmp");
        if (!tempFile.exists()) {
            tempFile.create();
        }

        // Save temp file
        saveFile(is, tempFile);

        // Check whether the file exists
        boolean fileExists = false;
        javaxt.io.File ret = null;
        final int bufferSize = 2048;
        final String hash = tempFile.getMD5(); // faster than getSHA1()
        for (final bluewave.app.File f : bluewave.app.File.find("hash=", hash)) {
            final javaxt.io.File file = new javaxt.io.File(f.getPath().getDir() + f.getName());
            if (file.getName().equalsIgnoreCase(name) && file.exists()) {
                BufferedInputStream file1Reader = new BufferedInputStream(file.getInputStream());
                BufferedInputStream file2Reader = new BufferedInputStream(tempFile.getInputStream());
                byte[] fileBytes1 = new byte[bufferSize];
                byte[] fileBytes2 = new byte[bufferSize];
                boolean fileContentDifferenceFound = false;
                try {
                    int readFile1 = file1Reader.read(fileBytes1, 0, bufferSize);
                    int readFile2 = file2Reader.read(fileBytes2, 0, bufferSize);
                    while (readFile1 != -1 && file1Reader.available() != 0 && readFile2 != -1
                            && file2Reader.available() != 0) {
                        if (!Arrays.equals(fileBytes1, fileBytes2)) {
                            fileContentDifferenceFound = true;
                            break;
                        }
                        readFile1 = file1Reader.read(fileBytes1, 0, bufferSize);
                        readFile2 = file2Reader.read(fileBytes2, 0, bufferSize);
                    }
                } finally {
                    file1Reader.close();
                    file2Reader.close();
                }

                fileExists = !fileContentDifferenceFound;

                if (fileExists) {
                    ret = file;
                }
            }
        }

        // Rename or delete the temp file
        if (fileExists) {
            tempFile.delete();
            return ret;
        }
        // Rename the temp file
        final javaxt.io.File file = tempFile.rename(name);

        // Save the file in the database
        final bluewave.app.Path path = getOrCreatePath(file.getDirectory());
        final bluewave.app.File f = getOrCreateFile(file, path);
        final bluewave.app.Document doc = getOrCreateDocument(f);
        doc.save();

        // Index the file
        synchronized (pool) {
            pool.add(new Object[] { file, path });
            pool.notifyAll();
        }

        // Return file
        return file;
    }

    // **************************************************************************
    // ** getThumbnail
    // **************************************************************************
    public ServiceResponse getThumbnail(ServiceRequest request, Database database) throws ServletException {

        // Get user
        final bluewave.app.User user = (bluewave.app.User) request.getUser();

        // Parse request
        final Long documentID = request.getParameter("documentID").toLong();
        if (documentID == null) {
            return new ServiceResponse(400, "documentID is required");
        }
        String pages = request.getParameter("pages").toString();
        if (pages == null || pages.isBlank()) {
            pages = request.getParameter("page").toString();
        }
        if (pages == null || pages.isBlank()) {
            return new ServiceResponse(400, "page or pages are required");
        }

        // Get file
        javaxt.io.File file;
        try {
            final bluewave.app.Document document = new bluewave.app.Document(documentID);
            file = getFile(document);
            if (!file.exists()) {
                return new ServiceResponse(404);
            }
        } catch (Exception e) {
            return new ServiceResponse(e);
        }

        // Set output directory
        final javaxt.io.Directory outputDir = new javaxt.io.Directory(file.getDirectory() + file.getName(false));
        if (!outputDir.exists()) {
            outputDir.create();
        }

        // Get script
        final javaxt.io.File[] scripts = getScripts("pdf_to_img.py");
        if (scripts.length == 0) {
            return new ServiceResponse(500, "Script not found");
        }

        // Compile command line options
        final ArrayList<String> params = new ArrayList<>();
        params.add("-f");
        params.add(file.toString());
        params.add("-p");
        params.add(pages);
        params.add("-o");
        params.add(outputDir.toString());

        // Execute script
        try {
            executeScript(scripts[0], params);
            final String[] arr = pages.split(",");
            final javaxt.io.File f = new javaxt.io.File(outputDir, arr[0] + ".png");
            return new ServiceResponse(f);
        } catch (Exception e) {
            return new ServiceResponse(e);
        }
    }

    // **************************************************************************
    // ** getSimilarity
    // **************************************************************************
    public ServiceResponse getSimilarity(ServiceRequest request, Database database) throws ServletException {

        final bluewave.app.User user = (bluewave.app.User) request.getUser();

        // Get requested document IDs
        final String[] documentIDs = request.getParameter("documents").toString().split(",");
        if (documentIDs == null || documentIDs.length < 2) {
            return new ServiceResponse(400, "At least 2 documents are required");
        }

        // Compare documents and return result
        Connection conn = null;
        try {
            conn = database.getConnection();
            final JSONObject result = getSimilarity(documentIDs, conn);
            conn.close();
            return new ServiceResponse(result);
        } catch (Exception e) {
            if (conn != null) {
                conn.close();
            }
            return new ServiceResponse(e);
        } finally {
            if (conn != null) {
                conn.close();
            }
        }
    }

    // **************************************************************************
    // ** getSimilarity
    // **************************************************************************
    public static JSONObject getSimilarity(String[] documentIDs, Connection conn) throws Exception {

        // Get python script
        final String scriptName = "compare_pdfs.py";
        final javaxt.io.File[] pyFiles = getScripts(scriptName);
        if (pyFiles.length == 0) {
            throw new Exception("Script not found");
        }
        final javaxt.io.File script = pyFiles[0];

        // Get script verion
        String scriptVersion = null;
        final long lastModified = script.getDate().getTime();
        synchronized (scripts) {
            try {
                JSONObject info = scripts.get(script.getName());
                if (info == null) {
                    info = new JSONObject();
                    info.set("lastModified", lastModified);
                    info.set("version", getScriptVersion(script));
                    scripts.put(scriptName, info);
                } else {
                    if (lastModified > info.get("lastModified").toLong()) {
                        info.set("lastModified", lastModified);
                        info.set("version", getScriptVersion(script));
                    }
                }

                scriptVersion = info.get("version").toString();
            } catch (Exception e) {
                e.printStackTrace();
                // Failed to get version
            }
            scripts.notifyAll();
        }

        // Check cache
        final ArrayList<DocumentComparison> docs = new ArrayList<>();
        if (documentIDs.length == 2) {
            final String cacheQuery = "select ID, INFO from APPLICATION.DOCUMENT_COMPARISON " + "where (A_ID="
                    + documentIDs[0] + " AND B_ID=" + documentIDs[1] + ") " + "OR (A_ID=" + documentIDs[1]
                    + " AND B_ID=" + documentIDs[0] + ")";

            try {

                // Execute query
                final HashMap<Long, String> results = new HashMap<>();

                final Recordset rs = new Recordset();
                rs.open(cacheQuery, conn);
                while (rs.hasNext()) {
                    final Long id = rs.getValue("ID").toLong();
                    final String info = rs.getValue("INFO").toString();
                    results.put(id, info);
                    rs.moveNext();
                }
                rs.close();

                // Parse json and return results if appropriate
                Iterator<Long> it = results.keySet().iterator();
                while (it.hasNext()) {
                    final Long id = it.next();
                    final String info = results.get(id);
                    if (info != null) {
                        final JSONObject result = new JSONObject(info);
                        final String version = result.get("version").toString();
                        if (version != null) {
                            if (version.equals(scriptVersion)) {
                                return result;
                            }
                        }
                    }
                }

                // Get current DocumentComparison from the database
                it = results.keySet().iterator();
                while (it.hasNext()) {
                    docs.add(new DocumentComparison(it.next()));
                }
            } catch (Exception e) {
                throw e;
            }
        }

        // Generate list of files and documents
        final ArrayList<javaxt.io.File> files = new ArrayList<>();
        final ArrayList<bluewave.app.Document> documents = new ArrayList<>();
        for (final String str : documentIDs) {
            try {
                final Long documentID = Long.parseLong(str);
                final bluewave.app.Document document = new bluewave.app.Document(documentID);
                final bluewave.app.File file = document.getFile();
                final bluewave.app.Path path = file.getPath();
                final javaxt.io.Directory dir = new javaxt.io.Directory(path.getDir());
                files.add(new javaxt.io.File(dir, file.getName()));
                documents.add(document);
            } catch (Exception e) {
                throw e;
            }
        }
        if (files.size() < 2) {
            throw new Exception("At least 2 documents are required");
        }

        // Compile command line options
        final ArrayList<String> params = new ArrayList<>();
        params.add("-f");
        for (final javaxt.io.File file : files) {
            params.add(file.toString());
        }

        // Execute script
        final JSONObject result = executeScript(script, params);

        // Replace file paths and insert documentID
        final JSONArray arr = result.get("files").toJSONArray();
        for (int i = 0; i < arr.length(); i++) {
            final JSONObject json = arr.get(i).toJSONObject();
            final String fileName = json.get("filename").toString();
            final String filePath = json.get("path_to_file").toString();
            final javaxt.io.File f = new javaxt.io.File(filePath, fileName);
            bluewave.app.Document document = null;
            for (int j = 0; j < files.size(); j++) {
                final javaxt.io.File file = files.get(j);
                if (file.toString().replace("\\", "/").equals(f.toString().replace("\\", "/"))) {
                    document = documents.get(j);
                    break;
                }
            }
            json.set("document_id", document.getID());
            json.remove("path_to_file");
        }

        // Cache the results
        if (documents.size() == 2) {

            if (docs.isEmpty()) {
                final DocumentComparison dc = new DocumentComparison();
                dc.setA(documents.get(0));
                dc.setB(documents.get(1));
                docs.add(dc);
            }

            for (final DocumentComparison dc : docs) {
                dc.setInfo(result);
                dc.save();
            }

            final JSONArray suspiciousPairs = result.get("suspicious_pairs").toJSONArray();
            final Iterator pairIterator = suspiciousPairs.iterator();
            while (pairIterator.hasNext()) {
                final JSONObject pair = (JSONObject) pairIterator.next();
                if (pair.has("pages")) {
                    final JSONArray pages = pair.get("pages").toJSONArray();
                    if (pages.length() >= 2) {
                        final JSONObject pageA = pages.get(0).toJSONObject();
                        final JSONObject pageB = pages.get(1).toJSONObject();
                        final DocumentComparisonSimilarity dss = new DocumentComparisonSimilarity();
                        dss.setType(pair.get("type").toString());
                        dss.setA_page(pageA.get("page").toInteger());
                        dss.setB_page(pageB.get("page").toInteger());
                        dss.setImportance(pair.get("importance").toInteger());
                        dss.setComparison(DocumentComparison.get("ID=", docs.get(0).getID()));
                        dss.save();
                    }
                }
            }
        }

        return result;
    }

    public static Recordset getSimilarityRecordSet(Connection conn) throws Exception {

        final String query = "select dcs.COMPARISON_ID, dcs.TYPE, dcs.A_PAGE, dcs.B_PAGE, dcs.IMPORTANCE, dc.A_ID, dc.B_ID  \n"
                + "from APPLICATION.DOCUMENT_COMPARISON_SIMILARITY as dcs \n"
                + "JOIN APPLICATION.DOCUMENT_COMPARISON as dc on dcs.COMPARISON_ID = dc.ID";
        final Recordset rs = new javaxt.sql.Recordset();
        try {
            rs.open(query, conn);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return rs;
    }

    public ServiceResponse getSimilarityResults(ServiceRequest request, Database database) throws ServletException {

        final StringBuilder out = new StringBuilder();
        Connection conn = null;

        try {
            conn = database.getConnection();
            final Recordset rs = getSimilarityRecordSet(conn);

            boolean addHeader = true;
            while (rs.hasNext()) {
                final Field[] fields = rs.getFields();
                if (addHeader) {
                    for (int i = 0; i < fields.length; i++) {
                        if (i > 0) {
                            out.append(",");
                        }
                        out.append(fields[i].getName());
                    }
                    addHeader = false;
                }

                out.append("\n");
                for (int i = 0; i < fields.length; i++) {
                    if (i > 0) {
                        out.append(",");
                    }
                    String val = fields[i].getValue().toString();
                    if (val != null) {
                        if (val.contains(",")) {
                            val = "\"" + val + "\"";
                        }
                        out.append(val);
                    }
                }

                rs.moveNext();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (conn != null) {
                    conn.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return new ServiceResponse(out.toString());
    }

    // **************************************************************************
    // ** getFile
    // **************************************************************************
    private static javaxt.io.File getFile(bluewave.app.Document document) {
        final bluewave.app.File f = document.getFile();
        final bluewave.app.Path path = f.getPath();
        final javaxt.io.Directory dir = new javaxt.io.Directory(path.getDir());
        return new javaxt.io.File(dir, f.getName());
    }

    // **************************************************************************
    // ** getUploadDir
    // **************************************************************************
    private static javaxt.io.Directory getUploadDir() {

        javaxt.io.Directory uploadDir = Config.getDirectory("webserver", "uploadDir");
        if (uploadDir == null) {
            final javaxt.io.Directory jobDir = Config.getDirectory("webserver", "jobDir");
            if (jobDir != null) {
                uploadDir = new javaxt.io.Directory(jobDir.toString() + "uploads");
                uploadDir.create();
            }
        } else {
            uploadDir.create();
        }

        if (uploadDir == null || !uploadDir.exists()) {
            throw new IllegalArgumentException(
                    "Invalid \"jobDir\" defined in the \"webserver\" section of the config file");
        }
        return uploadDir;
    }

    // **************************************************************************
    // ** getOrCreatePath
    // **************************************************************************
    public static bluewave.app.Path getOrCreatePath(javaxt.io.Directory dir) {
        final String p = dir.toString();

        try {
            return bluewave.app.Path.find("dir=", p)[0];
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            final bluewave.app.Path path = new bluewave.app.Path();
            path.setDir(p);
            path.save();
            path.getID();
            return path;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    // **************************************************************************
    // ** getOrCreateFile
    // **************************************************************************
    public static bluewave.app.File getOrCreateFile(javaxt.io.File file, bluewave.app.Path path) {
        try {
            return bluewave.app.File.find("name=", file.getName(), "path_id=", path.getID())[0];
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            final bluewave.app.File f = new bluewave.app.File();
            f.setName(file.getName());
            f.setSize(file.getSize());
            f.setDate(new javaxt.utils.Date(file.getDate()));
            f.setType(file.getContentType());
            f.setHash(file.getMD5());
            f.setPath(path);
            f.save();
            return f;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // **************************************************************************
    // ** getOrCreateDocument
    // **************************************************************************
    public static bluewave.app.Document getOrCreateDocument(bluewave.app.File f) {

        try {
            return bluewave.app.Document.find("file_id=", f.getID())[0];
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            final bluewave.app.Document doc = new bluewave.app.Document();
            doc.setFile(f);
            doc.save();
            return doc;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    // **************************************************************************
    // ** encode
    // **************************************************************************
    /**
     * Returns a csv-safe version of a JSON object
     */
    private String encode(JSONObject md) {
        try {
            if (md != null) {
                return encode(md.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private String encode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // **************************************************************************
    // ** getResponse
    // **************************************************************************
    /**
     * Returns a HTTP response from a remote document server (Image2000)
     */
    private javaxt.http.Response getResponse(String url) {
        final String edge = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/99.0.4844.51 Safari/537.36 Edg/99.0.1150.30";
        javaxt.http.Request r = new javaxt.http.Request(url);
        r.setConnectTimeout(5000);
        r.setReadTimeout(5000);
        r.validateSSLCertificates(false);
        r.setHeader("User-Agent", edge);
        r.setNumRedirects(0);
        javaxt.http.Response response = r.getResponse();
        if (response.getStatus() == 200) {
            return response;
        }
        final String cookie = response.getHeader("Set-Cookie");
        if (cookie != null) {
            r = new javaxt.http.Request(url);
            r.setConnectTimeout(5000);
            r.setReadTimeout(5000);
            r.validateSSLCertificates(false);
            r.setHeader("User-Agent", edge);
            r.setNumRedirects(0);
            r.setHeader("Cookie", cookie);
            response = r.getResponse();
            if (response.getStatus() == 200) {
                return response;
                // else console.log(r);
            }
        }
        return response;
    }

    // **************************************************************************
    // ** getRemoteSearchStatus
    // **************************************************************************
    public ServiceResponse getRemoteSearchStatus(ServiceRequest request, Database database) throws ServletException {
        final Boolean remoteSearch = Config.get("webserver").get("remoteSearch").toBoolean();
        return new ServiceResponse(remoteSearch == null ? "false" : remoteSearch + "");
    }

    // **************************************************************************
    // ** getRefreshDocumentIndex
    // **************************************************************************
    public ServiceResponse getRefreshDocumentIndex(ServiceRequest request, Database database) throws ServletException {
        final bluewave.app.User user = (bluewave.app.User) request.getUser();
        if (user.getAccessLevel() < 5) {
            return new ServiceResponse(401, "Not Authorized");
        }
        try {
            updateIndex();
            return new ServiceResponse(200);
        } catch (Exception e) {
            return new ServiceResponse(e);
        }
    }

    // **************************************************************************
    // ** updateIndex
    // **************************************************************************
    /**
     * Used to add/remove items from the index
     */
    private void updateIndex() throws Exception {

        // Remove any docs that might have been moved or deleted from the upload
        // directory
        for (final bluewave.app.Document doc : bluewave.app.Document.find()) {
            final bluewave.app.File f = doc.getFile();
            final javaxt.io.Directory dir = new javaxt.io.Directory(f.getPath().getDir());
            final javaxt.io.File file = new javaxt.io.File(dir, f.getName());
            if (!file.exists()) {

                // Remove any document comparisons associated with the file
                final Long docId = doc.getID();
                final Map<String, Long> constraints = new HashMap<>();
                constraints.put("a_id=", docId);
                constraints.put("b_id=", docId);
                for (final DocumentComparison dc : DocumentComparison.find(constraints)) {
                    dc.delete();
                }

                // Remove document from the database
                doc.delete();

                // Remove from index
                index.removeFile(file);
                notify("indexUpdate," + index.getSize() + "," + Config.getIndexDir().getSize());
            }
        }

        // Add new documents to the index
        final HashMap<javaxt.io.Directory, bluewave.app.Path> paths = new HashMap<>();
        for (final javaxt.io.File file : getUploadDir().getFiles("*.pdf", true)) {
            final javaxt.io.Directory dir = file.getDirectory();
            bluewave.app.Path path = paths.get(dir);
            if (path == null) {
                path = getOrCreatePath(dir);
            }
            if (path != null) {
                paths.put(dir, path);
                pool.add(new Object[] { file, path });
            }
        }

    }
}