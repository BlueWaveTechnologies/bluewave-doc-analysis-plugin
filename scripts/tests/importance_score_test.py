import pytest

from src.importance_score import calculate_importance_score


@pytest.mark.parametrize(
    "suspicious_pairs, expected_output",
    [
        (
            [
                {
                    "type": "Duplicate page",
                    "cosine_distance": 0.0,
                    "page_text": "* Critical Care: This is a specific referral to board-certified critical care "
                    "specialist. If it is after hours, there will be a call-in fee. ** Please note cardiology "
                    "availability changes from week to week and may not be available every week. Please contact us "
                    "about availability. ***Please call 604-514-8383 when sending direct transfers",
                    "pages": [
                        {"file_index": 0, "page": 1, "bbox": (0.01, 0.01, 0.99, 0.99)},
                        {"file_index": 1, "page": 1, "bbox": (0.01, 0.01, 0.99, 0.99)},
                    ],
                }
            ],
            [
                {
                    "type": "Duplicate page",
                    "cosine_distance": 0.0,
                    "page_text": "* Critical Care: This is a specific referral to board-certified critical care "
                    "specialist. If it is after hours, there will be a call-in fee. ** Please note cardiology "
                    "availability changes from week to week and may not be available every week. Please contact us "
                    "about availability. ***Please call 604-514-8383 when sending direct transfers",
                    "pages": [
                        {"file_index": 0, "page": 1, "bbox": (0.01, 0.01, 0.99, 0.99)},
                        {"file_index": 1, "page": 1, "bbox": (0.01, 0.01, 0.99, 0.99)},
                    ],
                    "importance": 0,
                }
            ],
        )
    ],
)
def test_calculate_importance_score(suspicious_pairs, expected_output):
    """Test Calculate Importance Score"""
    actual_output = calculate_importance_score(suspicious_pairs)
    assert actual_output == expected_output
