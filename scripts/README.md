# bluewave

This is a Python script to analyze the similarity of two PDFs.


## Installation

Use the package manager [pip](https://pip.pypa.io/en/stable/) to install bluewave.

```bash
pip install -r requirements.txt
```
For installation of black in your local environment, please follow the instructions here:
```
https://black.readthedocs.io/en/stable/integrations/editors.html
```

## Models
Create a folder "models" in root of the repo
```bash
mkdir models
```

Place clf.p file in the model's directory.

## Usage
To run this, an example would be:


```bash
python compare_pdfs.py -f ./sample_files/sample_file_1.pdf ./sample_files/sample_file_2.pdf -v -p
```

To Execute without using cached data
```bash
python compare_pdfs.py -f ./sample_files/sample_file_1.pdf ./sample_files/sample_file_2.pdf -v -p -c
```

```bash
python compare_pdfs.py -f ./sample_files/sample_file_1.pdf ./sample_files/sample_file_1.pdf
```

## Run Pytest
```bash
pytest -vv --disable-warnings
```

## Delete all .jsoncached files
```bash
find . -name "*.jsoncached" -type f -delete
```

## Code Formatting
```bash
pre-commit run --all-files
```

```bash
black src ./
```
