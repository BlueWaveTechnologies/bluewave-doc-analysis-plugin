from compare_pdfs import get_version

EXPECTED_OUTPUT_1 = {
    "files": [
        {
            "filename": "sample_file_1.pdf",
            "path_to_file": "./sample_files",
            "n_pages": 1,
            "n_suspicious_pages": 1,
            "suspicious_pages": [1],
        },
        {
            "filename": "sample_file_1.pdf",
            "path_to_file": "./sample_files",
            "n_pages": 1,
            "n_suspicious_pages": 1,
            "suspicious_pages": [1],
        },
    ],
    "suspicious_pairs": [
        {
            "type": "Duplicate page",
            "cosine_distance": 0.0,
            "page_text": "* Critical Care: This is a specific referral to board-certified critical care specialist. If it is after hours, there will be a call-in fee. ** Please note cardiology availability changes from week to week and may not be available every week. Please contact us about availability. ***Please call 604-514-8383 when sending direct transfers",
            "pages": [
                {
                    "file_index": 0,
                    "page": 1,
                    "bbox": (0.01, 0.01, 0.99, 0.99),
                },
                {
                    "file_index": 1,
                    "page": 1,
                    "bbox": (0.01, 0.01, 0.99, 0.99),
                },
            ],
            "importance": 0,
        }
    ],
    "num_suspicious_pairs": 1,
    "similarity_scores": {
        0: {
            1: {
                "Common digit sequence": 0.0,
                "Common text string": 0.0,
                "Identical image": "Undefined",
            }
        }
    },
    "version": get_version(),
}
EXPECTED_OUTPUT_2 = {
    "files": [
        {
            "filename": "sample_file_2.pdf",
            "path_to_file": "./sample_files",
            "n_pages": 1,
            "n_suspicious_pages": 1,
            "suspicious_pages": [1],
        },
        {
            "filename": "sample_file_2.pdf",
            "path_to_file": "./sample_files",
            "n_pages": 1,
            "n_suspicious_pages": 1,
            "suspicious_pages": [1],
        },
    ],
    "suspicious_pairs": [
        {
            "type": "Duplicate page",
            "cosine_distance": 0.0,
            "page_text": "Signature                                                         Title                 Date Signed TEACHER QUESTIONNAIRE \n \nStudents Name:__________________________________________              Date: ___________________ \n \nTeachers Name:_______________________________________ Phone #:__________________________ \n \nStudents Grade:___________  School:______________________________________________________ \n \nMAIN PROBLEMS \n \nHow long have you known this child? __________________ In your own words, briefly  \ndescribe the childs main problem or problems. _________________________________ \n________________________________________________________________________ \n________________________________________________________________________ \n \n \nACHIEVEMENT IN SCHOOL SUBJECTS \n(group subjects into the appropriate category of achievement) \n \n     Very Good \n      Average \n   Barely Passing \n      Failing STANDARDIZED TEST RESULTS \nIntelligence or Ability or Achievement Tests \nName of Test or Subject Area         Date        \n  Percentile   Standard Score  Grade Level SPECIAL PLACEMENTS OR ASSISTANCE \n \nPlease list any special education placement or other special assistance this child receives \nat school and the amount of time he/she receives it (i.e., tutoring, resource room, etc.). \nSpecial Assistance or Placement \nWho provides this service? \nNumber of hours Please add any information concerning this childs home, family, or school relationships which might have \nbearing on the childs attitudes and behavior.  Include any other thoughts you feel are relevant. \n________________________________________________________________________\n________________________________________________________________________\n________________________________________________________________________\n________________________________________________________________________",
            "pages": [
                {"file_index": 0, "page": 1, "bbox": (0.01, 0.01, 0.99, 0.99)},
                {"file_index": 1, "page": 1, "bbox": (0.01, 0.01, 0.99, 0.99)},
            ],
            "importance": 0,
        }
    ],
    "num_suspicious_pairs": 1,
    "similarity_scores": {
        0: {
            1: {
                "Common digit sequence": "Undefined",
                "Common text string": 0.0,
                "Identical image": "Undefined",
            }
        }
    },
    "version": "1.6.4",
}
EXPECTED_OUTPUT_3 = {
    "files": [
        {
            "filename": "sample_file_1.pdf",
            "path_to_file": "./sample_files",
            "n_pages": 1,
            "n_suspicious_pages": 0,
            "suspicious_pages": [],
        },
        {
            "filename": "sample_file_2.pdf",
            "path_to_file": "./sample_files",
            "n_pages": 1,
            "n_suspicious_pages": 0,
            "suspicious_pages": [],
        },
    ],
    "suspicious_pairs": [],
    "num_suspicious_pairs": 0,
    "similarity_scores": {
        0: {
            1: {
                "Common digit sequence": 0.0,
                "Common text string": 0.0,
                "Identical image": "Undefined",
            }
        }
    },
    "version": "1.6.4",
}
