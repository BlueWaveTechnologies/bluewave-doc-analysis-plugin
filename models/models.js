var package = "bluewave.app.benchmark";

var benchmark_models = {
    
    DocumentComparisonTest: {
        fields: [
            {name: 'numThreads', type: 'int'},
            {name: 'scriptVersion', type: 'string'},
            {name: 'host', type: 'string'},
            {name: 'info', type: 'json'}
        ],
        constraints: [
            {name: 'numThreads', required: true},
            {name: 'scriptVersion', required: true}
        ]
    },

    DocumentComparisonStats: {
        fields: [
            {name: 'test', type: 'DocumentComparisonTest'},
            {name: 'a', type: 'Document'},
            {name: 'b', type: 'Document'},
            {name: 't', type: 'long'},
            {name: 'a_size', type: 'long'},
            {name: 'b_size', type: 'long'},
            {name: 'info', type: 'json'}
        ],
        constraints: [
            {name: 'test', required: true},
            {name: 'a', required: true},
            {name: 'b', required: true},
            {name: 't', required: true}
        ]
    }
};