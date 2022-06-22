if (!bluewave) bluewave = {};
//******************************************************************************
//**  StatNavbar
//******************************************************************************
/**
 * Used for rendering a graphed navbar in document analysis
 *
 ******************************************************************************/
bluewave.analytics.StatNavbar = function(parent, config){
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
        if (navbar) navbar.remove();
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

        var div = document.createElement("div");
        div.className = "doc-compare-panel-footer-navbar";
        navbar = div;
        parent.appendChild(div);

        var d = document.createElement("div");
        div.appendChild(d);
        d.className = "doc-compare-panel-footer-navbar-container";

        var rectSize = javaxt.dhtml.utils.getRect(d);

        var svg = d3.select(d)
          .append("svg")
          .attr("preserveAspectRatio", "xMinYMin meet")
          .attr("viewBox", `0 0 ${rectSize.width} 35`)

        svg.attr("visibility", "hidden");

      // format data for barchart
        var xAxis = Object.keys(records[0])[0];
        var yAxis = Object.keys(records[0])[4];
        var csv =
        `${xAxis},${yAxis}\n`


        var as = "";
        for (var i in records){
            as = as + `${i},${parseInt(records[i][yAxis])}\n`;
        };
      // create last record in the dataset for rendering
        var removeThisIndex = records.length;
        as = as + `${removeThisIndex}, ${parseInt(100)}`;

        var csv = csv + as;
        var data = d3.csvParse(csv);
        // var valueBarChart = new bluewave.charts.BarChart(d, {});
        var valueBarChart = new bluewave.charts.BarChart(svg, {});

        valueBarChart.update({xAxis:xAxis, yAxis:yAxis, yTicks: false, xTicks: false, barColor:"black", showTooltip:false, colors:["#0f6391"]}, [data]);


        setTimeout(() => {

          // get the bar associated with index
            svg.selectAll("rect").filter(function (d, i) { return i === removeThisIndex;}).node().remove();
          // fix barchart padding
            var es = d3.selectAll("rect")._groups[0];
            var difference = es[1].x.animVal.value - es[0].x.animVal.value;
            var f = 0;

            var rect = javaxt.dhtml.utils.getRect(es[0].parentNode);
            var currentBarWidth = rect.width/2;
            var newBarWidth = currentBarWidth +(difference-currentBarWidth-(currentBarWidth*f));
            var tr = svg.selectAll("rect").size()-1;
            d3.selectAll("rect").each(function (d,i){
                d3.select(this).attr("width",newBarWidth);

                if (i === tr){
                  svg.attr("visibility", "visible");
                }

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

                tooltip.update = function(overlayRect){
                    var x;
                    x = d3.select(overlayRect).attr("x");


                    this.style.left = `${x}`;
                    this.style.bottom = `${javaxt.dhtml.utils.getRect(d).height}`;

                    this.show();
                    var data = d3.select(overlayRect).data()[0];
                    innerDiv.innerText = `Doc page (L): ${data.left }\nDoc page (R): ${data.right}\nImportance (AVG): ${data.importance}\nMatches: ${data.matches}`;


                }

                parent.appendChild(tooltip);
            };
            var createBox = function(index, left, right, matches, importance, x, width, bar){

              svg
                  .append("rect")
                  .data([{importance: importance, matches: matches, index: index, left: left, right: right, bar: bar}])
                  .attr("x", `${x}px`)
                  .attr("y", `0px`)
                  .attr("pointer-events", "all")
                  .attr("fill", "none")
                  .style("width", `${width}px`)
                  .attr("class", "doc-compare-panel-footer-navbar-overlay-div")
                  .on("mouseover", function(){
                    if (!tooltip) createTooltip();
                    tooltip.update(this);
                    tooltip.show();
                    d3.select(this).attr("fill", "white");
                    d3.select(this).attr("opacity", .8);
                  })
                  .on("mouseleave", function(){
                    if (tooltip) tooltip.hide();
                    d3.select(this).attr("fill", "none");
                    d3.select(this).attr("opacity", null);
                  })
                  .on("click", function(){
                    me.barClick(this);
                  })
            };
            d3.selectAll("rect").each(function(d,i){
                var x = this.x.animVal.value;
                var w = javaxt.dhtml.utils.getRect(this).width;
                createBox(r[i].index, r[i].left, r[i].right, r[i].matches, r[i].importance, x, w, this);
        });
        }, 1000);

    };


  // cut down the number of includes here
    var merge = javaxt.dhtml.utils.merge;
    var addShowHide = javaxt.dhtml.utils.addShowHide;
    init();


};

