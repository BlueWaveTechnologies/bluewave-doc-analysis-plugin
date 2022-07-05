if(!bluewave) var bluewave={};
if(!bluewave.analytics) bluewave.analytics={};

//******************************************************************************
//**  DocumentAnalysis
//******************************************************************************
/**
 *   Panel used to search and compare documents
 *
 ******************************************************************************/

bluewave.analytics.DocumentSimilarityAnalysis = function(parent, config) {

    var defaultConfig = {
        dateFormat: "M/D/YYYY h:mm A",
        style: {
        }
    };
    var grid;
    var documentSimilarities;
    var firstLoad = true; // temporary thing
    var me = this;

  //**************************************************************************
  //** Constructor
  //**************************************************************************

  var init = function(){
      //Process config
      if (!config) config = {};
      config = merge(config, defaultConfig);
      if (!config.fx) config.fx = new javaxt.dhtml.Effects();


      if (!config.style) config.style = javaxt.dhtml.style.default;
      if (!config.style.table) config.style.table = javaxt.dhtml.style.default.table;
      if (!config.style.window) config.style.window = javaxt.dhtml.style.default.window;
      if (!config.style.toolbarButton) config.style.toolbarButton = javaxt.dhtml.style.default.toolbarButton;

      if (!config.waitmask) config.waitmask = new javaxt.express.WaitMask(document.body);
      waitmask = config.waitmask;


    //Create main table
      var table = createTable();
      var tbody = table.firstChild;
      var tr, td;

      tr = document.createElement("tr");
      tbody.appendChild(tr);
      td = document.createElement("td");
      tr.appendChild(td);

      tr = document.createElement("tr");
      tbody.appendChild(tr);
      td = document.createElement("td");
      td.style.height = "100%";
      td.style.padding = "0px";
      tr.appendChild(td);
      createBody(td);

      tr = document.createElement("tr");
      tbody.appendChild(tr);
      td = document.createElement("td");
      td.style.height = "100%";
      td.style.padding = "0px";
      tr.appendChild(td);

      parent.appendChild(table);
      me.el = table;

  };

  //**************************************************************************
  //** this.update
  //**************************************************************************
    this.update = function(){

    //Clear the panel
        me.clear();

    // load datagrid
      // update datagrid from cached results
        populateDatagrid();


    };

  //**************************************************************************
  //** populateDatagrid
  //**************************************************************************
    var populateDatagrid = function(){
      // hit API, requesting results
        var url = "/document/SimilarityResults";
        get(url,{
        success: function(csv){
        var rows = parseCSV(csv, ",");
        var header = rows.shift();

        var createRecord = function(row){
            var r = {};
            header.forEach((field, i)=>{
                var v = row[i];
                r[field] = v;
            });
            return r;
        };


        var data = [];
        rows.forEach((row)=>{
            var doc = createRecord(row);
            if (doc.IMPORTANCE >= 5){ // set to minimum value of default comparison config (removes anything less than 5 from results);
                data.push({
                    A_ID: doc.A_ID,
                    A_PAGE: doc.A_PAGE,
                    B_ID: doc.B_ID,
                    B_PAGE: doc.B_PAGE,
                    COMPARISON_ID: doc.COMPARISON_ID,
                    IMPORTANCE: doc.IMPORTANCE,
                    TYPE: doc.TYPE
                });
            };

        });
        grid.load(data);
        }

        });
    }
  //**************************************************************************
  //** this.clear
  //**************************************************************************
    this.clear = function(){
    };

  //**************************************************************************
  //** createBody
  //**************************************************************************
    var createBody = function(parent){
      //Create table
        var table = createTable();
        parent.appendChild(table);
        var tbody = table.firstChild;
        var el = table;
        var tr, td;


      //Create carousel row
        tr = document.createElement("tr");
        tbody.appendChild(tr);
        td = document.createElement("td");

        tr.appendChild(td);
        td.style.height = "75%";
        createCarousel(td);


     //Create datagrid row
        tr = document.createElement("tr");
        tbody.appendChild(tr);
        td = document.createElement("td");
        tr.appendChild(td);
        td.style.height = "25%";
        createDataGrid(td);
  };

  //**************************************************************************
  //** createCarousel
  //**************************************************************************
    var createCarousel = function(parent){
        var docCompare = new bluewave.analytics.DocumentComparison(parent);

        documentSimilarities = {
            update: function(similarities){
                docCompare.update(similarities);
            },
            getFilteredResults: function(results){
                return docCompare.getFilteredSimilarities(results);
            },
            getConfig: function(){
                return docCompare.getConfig();
            },
            getPairs: function(){
                return docCompare.getCurrPairs();
            },
            goToIndex: function(pairIndex){
                docCompare.goToIndex(pairIndex);
            }

        };

    }

  //**************************************************************************
  //** createDataGrid
  //**************************************************************************
    var createDataGrid = function(parent){
        grid = new javaxt.dhtml.DataGrid(parent, {
            style: config.style.table,
            localSort: true,
            columns: [
                {header: 'Type', width:'100%', sortable: true},
                {header: 'Comparison ID', width:'120px', sortable: true},
                {header: 'Doc A ID', width:'120px', sortable: true},
                {header: 'Doc B ID', width:'120px', sortable: true},
                {header: 'Doc A Page', width:'120px', sortable: true},
                {header: 'Doc B Page', width:'120px', sortable: true},
                {header: 'Importance', width:'120px', sortable: true},
            ],
            update: function(row, record){
                row.set("Doc A ID", record.A_ID);
                row.set("Doc A Page", record.A_PAGE);
                row.set("Doc B ID", record.B_ID);
                row.set("Doc B Page", record.B_PAGE);
                row.set("Comparison ID", record.COMPARISON_ID);
                row.set("Importance", record.IMPORTANCE);
                row.set("Type", record.TYPE);
            }
        });

      //Watch for row click events
        grid.onRowClick = function(row, e){
            var r = row.record;

          // populate carousel with record from grid
            populateCarousel(row.record);

            if (firstLoad){
              // remove headers, footers, ect
                setTimeout(() => {
                    var headerDiv = document.getElementsByClassName("doc-compare-panel-title")[0];
                    var trRows = headerDiv.parentNode.parentNode.getElementsByTagName("tr");

                    var titleHeader = trRows[0];
                    var subtitleHeader = trRows[1];

                    titleHeader.parentNode.removeChild(titleHeader);
                    subtitleHeader.parentNode.removeChild(subtitleHeader);

                    var otherHeader = document.getElementsByClassName("dashboard-title noselect")[0];
                    otherHeader.parentNode.removeChild(otherHeader);

                    var footerArea = document.getElementsByClassName("doc-compare-panel-footer-navbar")[0].parentNode;
                    footerArea.parentNode.removeChild(footerArea);
                }, 1500);
                firstLoad = false;
            }
        };
    };

  //**************************************************************************
  //** populateCarousel
  //**************************************************************************
    var populateCarousel = function(record){
        if (!documentSimilarities) createCarousel();
        get("document/similarity?documents="+ record.A_ID +"," + record.B_ID,{
            success: function(json){
              // update document comparison with the document
                documentSimilarities.update(documentSimilarities.getFilteredResults(json));
                var pairsList = documentSimilarities.getPairs();
                pairsList.forEach((pair)=>{
                    if (parseInt(pair.left) === parseInt(record.A_PAGE) && parseInt(pair.right) === parseInt(record.B_PAGE)){
                        documentSimilarities.goToIndex(pair.index);
                    };
                });
            }
        });

    }
  //**************************************************************************
  //** getTitle
  //**************************************************************************
    this.getTitle = function(){
        return "Similarity Analysis";
    };

  //**************************************************************************
  //** Utils
  //**************************************************************************
  var get = bluewave.utils.get;
  var post = javaxt.dhtml.utils.post;
  var merge = javaxt.dhtml.utils.merge;
  var createTable = javaxt.dhtml.utils.createTable;
  var onRender = javaxt.dhtml.utils.onRender;
  var addShowHide = javaxt.dhtml.utils.addShowHide;
  var createSpacer = bluewave.utils.createSpacer;
  var parseCSV = bluewave.utils.parseCSV;
  var createSlider = bluewave.utils.createSlider;

  init();
};