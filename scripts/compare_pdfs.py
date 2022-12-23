"""
Compare two pdf files
"""
import argparse
import concurrent.futures
import datetime
import json
import sys
import time
from multiprocessing import cpu_count

from src.compare_pdfs_text import compare_two_pdfs_text
from src.get_file_data import get_file_data
from src.importance_score import calculate_importance_score
from src.pdf_duplicate_pages import (
    find_duplicate_pages,
    get_dup_page_results,
    remove_duplicate_pages,
)
from src.utils import list_of_unique_dicts

VERSION = "1.6.4"


# -------------------------------------------------------------------------------
# COMPARISON
# -------------------------------------------------------------------------------


def compare_images(hash_info_a, hash_info_b):
    # hash_info_a and hash_info_b are lists of tuples
    # (img_hash, bbox, page_number)

    # Create these dicts so that we can look up info later
    info_a = {hsh: (bbx, p) for hsh, bbx, p in hash_info_a}
    info_b = {hsh: (bbx, p) for hsh, bbx, p in hash_info_b}

    hashes_a = set(info_a)
    hashes_b = set(info_b)
    common_hashes = hashes_a.intersection(hashes_b)

    common_hash_info = []
    for h in common_hashes:
        result = (h, info_a[h], info_b[h])
        common_hash_info.append(result)

    return common_hash_info


# -------------------------------------------------------------------------------
# GATHERING RESULTS
# -------------------------------------------------------------------------------
def get_file_info(file_data, suspicious_pairs):
    # Set of suspicious pages for filename
    sus_page_sets = [set() for _ in file_data]
    for pair in suspicious_pairs:
        pages = pair["pages"]
        for page in pages:
            sus_page_sets[page["file_index"]].add(page["page"])

    file_info = []
    for i, data in enumerate(file_data):
        file_sus_pages = list(sus_page_sets[i])
        fi = {
            "filename": data["filename"],
            "path_to_file": data["path_to_file"],
            "n_pages": data["n_pages"],
            "n_suspicious_pages": len(file_sus_pages),
            "suspicious_pages": file_sus_pages,
        }
        file_info.append(fi)
    return file_info


def get_similarity_scores(file_data, suspicious_pairs, methods_run):
    """Get Similarity Scores"""
    method_names = [
        ("Common digit sequence", "digits"),
        ("Common text string", "text"),
        ("Identical image", "images"),
    ]

    # Reorganize the suspicious pairs, so we can efficiently access when looping
    reorg_sus_pairs = {}
    for sus in suspicious_pairs:
        file_a, file_b = [p["file_index"] for p in sus["pages"]]
        method = sus["type"]
        if file_a not in reorg_sus_pairs:
            reorg_sus_pairs[file_a] = {file_b: {method: []}}
        elif file_b not in reorg_sus_pairs[file_a]:
            reorg_sus_pairs[file_a][file_b] = {method: []}
        elif method not in reorg_sus_pairs[file_a][file_b]:
            reorg_sus_pairs[file_a][file_b][method] = []

        reorg_sus_pairs[file_a][file_b][method].append(sus)

    # Only upper triangle not incl. diagonal of cross matrix
    similarity_scores = {}
    for a in range(len(file_data) - 1):
        for b in range(a + 1, len(file_data)):

            if a not in similarity_scores:
                similarity_scores[a] = {b: {}}
            elif b not in similarity_scores[a]:
                similarity_scores[a][b] = {}

            for method_long, method_short in method_names:
                if method_short in methods_run:
                    try:
                        sus_pairs = reorg_sus_pairs[a][b].get(method_long, [])
                    except KeyError:
                        sus_pairs = []
                    union = 0
                    intersect = 0
                    if method_long == "Common digit sequence":
                        intersect = sum(s["length"] for s in sus_pairs)
                        a_clean = file_data[a]["full_digits"]
                        b_clean = file_data[b]["full_digits"]
                        union = len(a_clean) + len(b_clean) - intersect
                    elif method_long == "Common text string":
                        intersect = sum(s["length"] for s in sus_pairs)
                        a_clean = file_data[a]["full_text"]
                        b_clean = file_data[b]["full_text"]
                        union = len(a_clean) + len(b_clean) - intersect
                    elif method_long == "Identical image":
                        intersect = len(sus_pairs)
                        union = (
                            len(file_data[a]["image_hashes"])
                            + len(file_data[b]["image_hashes"])
                            - intersect
                        )
                    if union == 0:
                        jaccard = "Undefined"
                    else:
                        jaccard = intersect / union
                    similarity_scores[a][b][method_long] = jaccard
                else:
                    similarity_scores[a][b][method_long] = "Not run"

    return similarity_scores


def get_version():
    """Get Version"""
    return VERSION


# -------------------------------------------------------------------------------
# MAIN
# -------------------------------------------------------------------------------
def compare_pdf_files(
    filenames,
    methods: list = False,
    pretty_print: bool = False,
    verbose: bool = False,
    regen_cache: bool = False,
    sidecar_only: bool = False,
    no_importance: bool = False,
):
    t0 = time.time()

    if verbose:
        print("Reading files...")
    read_pdf_sec_t0 = time.time()
    file_data = []
    for file_index, file_name in enumerate(filenames):
        f_data = get_file_data(file_name, file_index, regen_cache, VERSION)
        file_data.append(f_data)

    read_pdf_sec = time.time() - read_pdf_sec_t0
    if sidecar_only:
        return

    assert len(filenames) >= 2, "Must have at least 2 files to compare!"

    if not methods:
        if verbose:
            print("Methods not specified, using default (all).")
        methods = ["pages", "digits", "images", "text"]
    if verbose:
        print("Using methods:", ", ".join(methods))

    suspicious_pairs = []

    page_analysis_sec = 0
    digit_analysis_sec = 0
    text_analysis_sec = 0
    image_analysis_sec = 0
    total_analysis_t0 = time.time()
    for i in range(len(filenames) - 1):
        for j in range(i + 1, len(filenames)):
            # i always less than j
            a = file_data[i]
            b = file_data[j]

            # Find duplicate pages and remove those from the analysis
            page_analysis_sec_t0 = time.time()
            a_new = a
            b_new = b
            if "pages" in methods:
                if verbose:
                    print("Finding duplicate pages...")
                a_new, b_new = execute_pages_method(a, b, suspicious_pairs)

            page_analysis_sec += time.time() - page_analysis_sec_t0

            # Compare numbers
            digit_analysis_sec_t0 = time.time()
            if "digits" in methods:
                execute_method_executor(
                    "digits",
                    "Common digit sequence",
                    20,
                    a_new,
                    b_new,
                    suspicious_pairs,
                    verbose,
                )

            digit_analysis_sec += time.time() - digit_analysis_sec_t0

            # Compare texts
            text_analysis_sec_t0 = time.time()
            if "text" in methods:
                execute_method_executor(
                    "text",
                    "Common text string",
                    300,
                    a_new,
                    b_new,
                    suspicious_pairs,
                    verbose,
                )
            text_analysis_sec += time.time() - text_analysis_sec_t0

            # Compare images
            image_analysis_sec_t0 = time.time()
            if "images" in methods:
                execute_images_method(a, a_new, b, b_new, suspicious_pairs, verbose)
            image_analysis_sec += time.time() - image_analysis_sec_t0
    total_analysis_sec = time.time() - total_analysis_t0

    # Remove duplicate suspicious pairs (this might happen if a page has
    # multiple common substrings with another page)
    if verbose:
        print("Removing duplicate sus pairs...")
    suspicious_pairs = list_of_unique_dicts(suspicious_pairs)

    # Filter out irrelevant sus pairs
    # if verbose: print('Removing irrelevant pairs...')
    # suspicious_pairs = filter_sus_pairs(suspicious_pairs)

    # Calculate some more things for the final output
    if verbose:
        print("Gathering output...")

    importance_scoring_sec_t0 = time.time()
    if not no_importance:
        if verbose:
            print("\tAdd importance scores...")
        suspicious_pairs = calculate_importance_score(suspicious_pairs)
    importance_scoring_sec = time.time() - importance_scoring_sec_t0

    post_processing_sec_t0 = time.time()
    if verbose:
        print("\tGet file info...")
    file_info = get_file_info(file_data, suspicious_pairs)

    if verbose:
        print("\tGet total pages...")
    total_page_pairs = sum(f["n_pages"] for f in file_info)

    if verbose:
        print("\tGet similarity score...")
    similarity_scores = get_similarity_scores(file_data, suspicious_pairs, methods)

    if verbose:
        print("\tGet version...")
    version = get_version()

    if verbose:
        print("\tResults gathered.")
    post_processing_sec = time.time() - post_processing_sec_t0

    dt = time.time() - t0
    if dt == 0:
        pages_per_second = -1
    else:
        pages_per_second = total_page_pairs / dt

    result = {
        "files": file_info,
        "suspicious_pairs": suspicious_pairs,
        "num_suspicious_pairs": len(suspicious_pairs),
        "elapsed_time_sec": {
            "read_pdf": read_pdf_sec,
            "page_analysis": page_analysis_sec,
            "text_analysis": text_analysis_sec,
            "digit_analysis": digit_analysis_sec,
            "image_analysis": image_analysis_sec,
            "total_analysis": total_analysis_sec,
            "importance_scoring": importance_scoring_sec,
            "post_processing": post_processing_sec,
            "total_sec": dt,
        },
        "pages_per_second": pages_per_second,
        "similarity_scores": similarity_scores,
        "version": version,
        "time": datetime.datetime.now().strftime("%Y-%m-%dT%H:%M:%SZ"),
    }

    if pretty_print:
        print(json.dumps(result, indent=2), file=sys.stdout)
    else:
        print(json.dumps(result), file=sys.stdout)

    return result


def execute_pages_method(a, b, suspicious_pairs):
    """Pages method"""
    duplicate_pages = find_duplicate_pages(data_a=a, data_b=b)
    dup_page_results = get_dup_page_results(duplicate_pages)
    suspicious_pairs.extend(dup_page_results)
    a_new = remove_duplicate_pages(a, duplicate_pages)
    b_new = remove_duplicate_pages(b, duplicate_pages)
    return a_new, b_new


def execute_method_executor(
    text_suffix, comparison_type_name, min_len, a_new, b_new, suspicious_pairs, verbose
):
    """Digits Method"""
    if verbose:
        print(f"Comparing {text_suffix}...")
    results = compare_two_pdfs_text(
        data_a=a_new,
        data_b=b_new,
        text_suffix=text_suffix,
        min_len=min_len,
        comparison_type_name=comparison_type_name,
    )
    suspicious_pairs.extend(results)


def execute_images_method(a, a_new, b, b_new, suspicious_pairs, verbose):
    """Images Method"""
    if verbose:
        print("Comparing images...")
    identical_images = compare_images(
        a_new["image_hashes"],
        b_new["image_hashes"],
    )
    any_images_are_sus = len(identical_images) > 0
    if any_images_are_sus:
        for img_hash, info_a, info_b in identical_images:
            bbox_a, sus_page_a = info_a
            bbox_b, sus_page_b = info_b
            sus_result = {
                "type": "Identical image",
                "image_hash": img_hash,
                "pages": [
                    {
                        "file_index": a["file_index"],
                        "page": sus_page_a,
                        "bbox": bbox_a,
                    },
                    {
                        "file_index": b["file_index"],
                        "page": sus_page_b,
                        "bbox": bbox_b,
                    },
                ],
            }
            suspicious_pairs.append(sus_result)


if __name__ == "__main__":

    parser = argparse.ArgumentParser()
    parser.add_argument(
        "-f",
        "--filenames",
        help="PDF filenames to compare",
        nargs="+",
    )
    parser.add_argument(
        "-m",
        "--methods",
        help="Which of the three comparison methods to use: text, digits, images",
        nargs="+",
    )
    parser.add_argument(
        "-p", "--pretty_print", help="Pretty print output", action="store_true"
    )
    parser.add_argument(
        "-c",
        "--regen_cache",
        help="Ignore and overwrite cached data",
        action="store_true",
    )
    parser.add_argument(
        "--sidecar_only",
        help="Just generate sidecar files, dont run analysis",
        action="store_true",
    )
    parser.add_argument(
        "--no_importance", help="Do not generate importance scores", action="store_true"
    )
    parser.add_argument(
        "-v", "--verbose", help="Print things while running", action="store_true"
    )
    parser.add_argument("--version", help="Print version", action="store_true")
    args = parser.parse_args()

    if args.version:
        print(VERSION)
    else:
        compare_pdf_files(
            filenames=args.filenames,
            methods=args.methods,
            pretty_print=args.pretty_print,
            verbose=args.verbose,
            regen_cache=args.regen_cache,
            sidecar_only=args.sidecar_only,
            no_importance=args.no_importance,
        )

# Within-file tests:
#    - Benford's Law
# for file in filenames:
#     a = file_data[file.split('/')[-1]]
#     for page_num, page_text in a['page_texts']:
#         paragraphs = re.split(r'[ \n]{4,}', page_text)
#         for paragraph in paragraphs:
#             p = benford_test(paragraph)
#             if p < 0.05:
#                 sus_result = {
#                     'type': 'Failed Benford Test',
#                     'p_value': p,
#                     'pages': [
#                         {'filename': a['filename'], 'page': page_num},
#                         {'filename': a['filename'], 'page': page_num},
#                     ]
#                 }
