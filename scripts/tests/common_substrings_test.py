import pytest

from src.find_common_substrings import find_common_substrings


@pytest.mark.parametrize(
    "text_1, text_2, min_length, expected_output",
    [
        ("a paragraph", "a sentence", 1, []),
        ("aa bb cc", "aa bb", 1, [("aa bb", 0, 0, 5)]),
        ("aa bb", "aa bd", 1, [("aa b", 0, 0, 4)]),
    ],
)
def test_find_common_substrings(text_1, text_2, min_length, expected_output):
    """Test common substrings"""
    actual_output = find_common_substrings(text_1, text_2, min_length)
    assert actual_output == expected_output
