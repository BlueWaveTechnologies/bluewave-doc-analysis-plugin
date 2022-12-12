import pytest

from src.utils import get_models_directory, list_of_unique_dicts, get_digits


def test_get_models_directory():
    """Check if function is returning the path of models directory"""
    path = get_models_directory()
    assert path != ""


@pytest.mark.parametrize(
    "data, expected_output",
    [
        ([{1: 2}, {1: 2}], [{1: 2}]),
        ([{1: 2, 3: 4}], [{1: 2, 3: 4}]),
        ([{1: 2, 3: 4}, {1: 2, 3: 4}], [{1: 2, 3: 4}]),
        ([{1: 2, 3: 4}, {1: 2, 3: 4}], [{3: 4, 1: 2}]),
        ([{"a": 1, "b": 2}, {1: 2, 3: 4}], [{"a": 1, "b": 2}, {3: 4, 1: 2}]),
    ],
)
def test_list_of_unique_dicts(data, expected_output):
    """Check if function is removing duplicates in a dict"""
    actual_output = list_of_unique_dicts(data)
    assert actual_output == expected_output


@pytest.mark.parametrize(
    "text, expected_output", [("abcd123", "123"), ("123", "123"), ("123.45", "12345")]
)
def test_get_digits(text, expected_output):
    """Test digits extraction from text"""
    actual_output = get_digits(text)
    assert actual_output == expected_output
