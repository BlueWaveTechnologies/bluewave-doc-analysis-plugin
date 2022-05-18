# Introduction

The document analysis module is used to detect similarities within the PDF documents. This capability is used by analysis to identify potentially fraudulent documents where data tables, images, and text have been plagiarized. Users perform their analysis via a custom web interface where they can search for documents, run pairwise comparisons, and review suspicious document pairs. Under the hood, the document analysis module uses lucene for document search and a custom python script to perform analysis. 

## Maven Quickstart
1. Build the plugin
```console
git clone https://github.com/BlueWaveTechnologies/bluewave-doc-analysis-plugin.git
cd bluewave-doc-analysis-plugin
mvn install
```
2. Copy the `dist` directory into the bluewave plugins folder



## Config and Admin

### Config.json

There are 3 document-specific config settings 
1. uploadDir: directory where to store files
1. indexDir: directory where to create the lucene index
1. uploadEnabled: boolean

### Deleting an index

The process for deleting the document index is as follows:
1. stop the server
1. delete the index directory
1. run the following

```console
java -jar target\bluewave-dev.jar -config ../config.json -delete index
```


## Python Scripts

### Prerequisites
Install PyMuPDF and PyDivSufSort

### Online Installation
```
pip3 install pymupdf
pip3 install pydivsufsort
```
Alternatively
```
python -m pip install --upgrade pymupdf
python -m pip install --upgrade pydivsufsort
```

### Offline Installation
Offline installation requires a .whl file that can be downloaded here:

https://pypi.org/project/PyMuPDF/#files

Once the correct wheel file is downloaded it can be installed like this:
```
pip3 install PyMuPDF-1.19.1-cp36-cp36m-manylinux1_x86_64.whl
```
Note that the wheel files for Linux may need to be renamed to conform to whatever pip supports. You can get a list of tags by running the following python script
```python
>>> import pip
>>> pip.pep425tags.get_supported()
```

