from __future__ import annotations

from ire.similarity import (
    address_jaccard,
    dob_similarity,
    email_similarity,
    exact_match,
    gender_similarity,
    jaro_winkler,
    name_similarity,
    phone_similarity,
)


def test_exact_and_jaro_winkler() -> None:
    assert exact_match("a", "a") == 1.0
    assert exact_match("a", "b") == 0.0
    assert round(jaro_winkler("martha", "marhta"), 3) == 0.961


def test_email_and_phone_similarity() -> None:
    assert email_similarity("a@example.com", "a@example.com") == 1.0
    assert email_similarity("alice@example.com", "alice@sample.com") >= 0.6
    assert phone_similarity("85261234567", "61234567") == 0.8


def test_dob_gender_address_and_name_similarity() -> None:
    assert dob_similarity("1990-01-01", "1990-01-01") == 1.0
    assert gender_similarity("F", "F") == 1.0
    assert address_jaccard(["1", "main"], ["1", "main", "road"]) == 2 / 3
    assert name_similarity("John", "Jon") > 0.9
