import pytest

from compare_pdfs import compare_pdf_files, get_version
from tests.constants import EXPECTED_OUTPUT_1, EXPECTED_OUTPUT_2, EXPECTED_OUTPUT_3


@pytest.mark.parametrize(
    "filenames, expected_output",
    [
        (
            ["./sample_files/sample_file_1.pdf", "./sample_files/sample_file_1.pdf"],
            EXPECTED_OUTPUT_1,
        ),
        (
            ["./sample_files/sample_file_2.pdf", "./sample_files/sample_file_2.pdf"],
            EXPECTED_OUTPUT_2,
        ),
        (
            ["./sample_files/sample_file_1.pdf", "./sample_files/sample_file_2.pdf"],
            EXPECTED_OUTPUT_3,
        ),
    ],
)
def test_compare_pdf_files(filenames, expected_output):
    """Test Compare pdf files"""
    actual_output = compare_pdf_files(filenames, regen_cache=True, pretty_print=True)
    keys_to_pop = ["time", "elapsed_time_sec", "pages_per_second"]
    for key in keys_to_pop:
        actual_output.pop(key, None)
    assert actual_output == expected_output


def test_get_version():
    """Test get version"""
    actual_output = get_version()
    assert actual_output != ""
