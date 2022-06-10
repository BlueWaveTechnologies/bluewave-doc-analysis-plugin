var package = "bluewave.document.analysis.models";
var schema = "application";
var models = {


  //**************************************************************************
  //** DocumentComparison
  //**************************************************************************
    DocumentComparison: {
        fields: [
            {name: 'a',         type: 'Document'},
            {name: 'b',         type: 'Document'},
            {name: 'info',      type: 'json'}
        ],
        constraints: [
            {name: 'a',     required: true},
            {name: 'b',     required: true},
            {name: 'info',  required: true}
        ]
    },


  //**************************************************************************
  //** DocumentComparisonSimilarity
  //**************************************************************************
    DocumentComparisonSimilarity: {
        fields: [
            {name: 'type',          type: 'string'},
            {name: 'a_page',        type: 'int'},
            {name: 'b_page',        type: 'int'},
            {name: 'importance',    type: 'int'},
            {name: 'comparison',    type: 'DocumentComparison'}
        ],
        constraints: [
            {name: 'a_page',        required: true },
            {name: 'b_page',        required: true },
            {name: 'importance',    required: true },
            {name: 'comparison',    required: true, cascade: true }
        ]
    },


  //**************************************************************************
  //** DocumentComparisonTest
  //**************************************************************************
    DocumentComparisonTest: {
        fields: [
            {name: 'numThreads',    type: 'int'},
            {name: 'scriptVersion', type: 'string'},
            {name: 'host',          type: 'string'},
            {name: 'info',          type: 'json'}
        ],
        constraints: [
            {name: 'numThreads',    required: true},
            {name: 'scriptVersion', required: true}
        ]
    },

  //**************************************************************************
  //** DocumentComparisonStats
  //**************************************************************************
    DocumentComparisonStats: {
        fields: [
            {name: 'test',      type: 'DocumentComparisonTest'},
            {name: 'a',         type: 'Document'},
            {name: 'b',         type: 'Document'},
            {name: 't',         type: 'long'},
            {name: 'a_size',    type: 'long'},
            {name: 'b_size',    type: 'long'},
            {name: 'info',      type: 'json'}
        ],
        constraints: [
            {name: 'test',  required: true},
            {name: 'a',     required: true},
            {name: 'b',     required: true},
            {name: 't',     required: true}
        ]
    }

};