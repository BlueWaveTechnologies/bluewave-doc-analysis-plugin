if (!bluewave) bluewave = {};
//******************************************************************************
//**  StatNavbar
//******************************************************************************
/**
 * Used for rendering a nice navbar in document analysis
 *
 ******************************************************************************/
bluewave.analytics.StatNavbar = function(parent, config){
    console.log("initializing special navbar");
    var me = this;
    var defaultConfig = {};
    var navbar;


  //**************************************************************************
  //** Constructor
  //**************************************************************************
    var init = function(){
        // merge configs
        config = merge(config, defaultConfig);
    };

  //**************************************************************************
  //** this.clear
  //**************************************************************************
    this.clear = function(){
        if (navbar) navbar.innerHTML = "";
    };

  //**************************************************************************
  //** this.update
  //**************************************************************************
    this.update = function(records){
        me.clear();
        update(records);

    };


  //**************************************************************************
  //** this.getNavbar
  //**************************************************************************
    this.getNavbar = function(){
        return navbar;
    };

  //**************************************************************************
  //** this.barClick
  //**************************************************************************
    this.barClick = function(div){ // re-assigned by initializing function

    };


  //**************************************************************************
  //** update
  //**************************************************************************
    var update = function(records){
        var r = records;
        var tooltip;


        var rect = javaxt.dhtml.utils.getRect(parent);
        var div = document.createElement("div");
        div.className = "doc-compare-panel-footer-navbar";
        console.log(div.getAttribute("position"));
        console.log("getting position attribute for navbar");
        navbar = div;
        parent.appendChild(div);

        var d = document.createElement("div");
        div.appendChild(d);
        d.className = "doc-compare-panel-footer-navbar-container";
        // d.style.height = "10px";
        // div.appendChild(d);

      // format data for barchart
        var xAxis = Object.keys(records[0])[0];
        var yAxis = Object.keys(records[0])[4];
        var csv =
        `${xAxis},${yAxis}\n`


        var as = "";
        for (var i in records){
            as = as + `${i},${parseInt(records[i][yAxis])}\n`;
        };

        var csv = csv + as;
        var data = d3.csvParse(csv);
        var valueBarChart = new bluewave.charts.BarChart(d, {});
        valueBarChart.update({xAxis:xAxis, yAxis:yAxis, yTicks: false, xTicks: false, barColor:"black", showTooltip:false, colors:["black"]}, [data]);

        setTimeout(() => {
          // fix barchart padding

            var es = d3.selectAll("rect")._groups[0];
            var difference = es[1].x.animVal.value - es[0].x.animVal.value;
            var f = 0;


            var rect = javaxt.dhtml.utils.getRect(es[0].parentNode);
            console.log("logging es values here for resizing");
            var currentBarWidth = rect.width/2;
            console.log(currentBarWidth);
            console.log(es[0].parentNode);
            var newBarWidth = currentBarWidth +(difference-currentBarWidth-(currentBarWidth*f));
            console.log(newBarWidth);
            d3.selectAll("rect").each(function (d,i){
                console.log("resizing rect");
                console.log(d3.select(this).attr("height"));
                d3.select(this).attr("width",newBarWidth);
            });

            var createTooltip = function(){
                tooltip = document.createElement("div");
                tooltip.className = "doc-compare-panel-footer-navbar-tooltip";
                tooltip.style.bottom = `${javaxt.dhtml.utils.getRect(d).height}px`;
                tooltip.innerText = "";


                var backgroundDiv = document.createElement("div"); // inner div with .8 opacity background
                backgroundDiv.className = "doc-compare-panel-footer-navbar-tooltip-background";
                tooltip.appendChild(backgroundDiv);

                var innerDiv = document.createElement("div"); // holds text
                innerDiv.className = "doc-compare-panel-footer-navbar-tooltip-text";
                tooltip.appendChild(innerDiv);

                addShowHide(tooltip);
                tooltip.hide();

                tooltip.update = function(div){
                    var x, rect;
                    rect = javaxt.dhtml.utils.getRect(div);
                    x = rect.x;

                    this.style.left = `${x}px`;
                    this.style.bottom = `${javaxt.dhtml.utils.getRect(d).height}`;


                    this.show();
                    innerDiv.innerText = `Doc page (L): ${div.left }\nDoc page (R): ${div.right}\nImportance (AVG): ${div.importance}\nMatches: ${div.matches}`;

                }

                d.appendChild(tooltip);
            };
            var createBox = function(index, left, right, matches, importance, x, width, bar){
                var div = document.createElement("div");
                div.className = "doc-compare-panel-footer-navbar-overlay-div";
                div.bar = bar;
                div.style.width = `${width}px`;
                div.style.left = `${x}px`;
                div.importance = importance;
                div.matches = matches;
                div.left = left;
                div.right = right;
                div.index = index;

                div.onmouseover = function(){
                    console.log("mousing over div");
                    if (!tooltip) createTooltip();
                    tooltip.update(this);
                    tooltip.show();
                    div.style.backgroundColor = "white";
                    div.style.opacity = .8;
                };


                div.onmouseleave = function(){
                    if (tooltip) tooltip.hide();
                    div.style.backgroundColor = null;
                };

                div.onclick = function(){
                    console.log("clicking this div");
                    d3.selectAll("rect").each(function(){
                        this.style.fill = "black";
                    });
                    this.bar.style.fill = "red";
                    me.barClick(this);
                };

                d.appendChild(div);
            };
            d3.selectAll("rect").each(function(d,i){
                var x = this.x.animVal.value;
                var w = javaxt.dhtml.utils.getRect(this).width;
                createBox(r[i].index, r[i].left, r[i].right, r[i].matches, r[i].importance, x, w, this);
        });
        }, 1000);

    };


  //**************************************************************************
  //** clear
  //**************************************************************************
    var clear = function(){

    };


  //**************************************************************************
  //** this.getConfig
  //**************************************************************************
    this.getConfig = function(){

    };


  //**************************************************************************
  //** this.setConfig
  //**************************************************************************
    this.setConfig = function(){

    };


  // cut down the number of includes here
    var merge = javaxt.dhtml.utils.merge;
    var onRender = javaxt.dhtml.utils.onRender;
    var createTable = javaxt.dhtml.utils.createTable;
    var createSpacer = bluewave.utils.createSpacer;
    var addShowHide = javaxt.dhtml.utils.addShowHide;
    var del = javaxt.dhtml.utils.delete;
    var get = bluewave.utils.get;
    var destroy = javaxt.dhtml.utils.destroy;
    var addResizeListener = javaxt.dhtml.utils.addResizeListener;

    init();


};

