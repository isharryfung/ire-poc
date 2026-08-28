from __future__ import annotations

import pytest

from ire.normalization import (
    canonicalize_payload,
    normalize_address,
    normalize_dob,
    normalize_email,
    normalize_field,
    normalize_gender,
    normalize_hkid,
    normalize_name,
    normalize_phone,
)


def test_normalize_hkid_uppercases_and_validates() -> None:
    assert normalize_hkid(" ab1234567 ") == "AB1234567"
    with pytest.raises(ValueError):
        normalize_hkid("BAD")


def test_normalize_email_phone_and_name() -> None:
    assert normalize_email(" Alice@Example.COM ") == "alice@example.com"
    assert normalize_phone("+852 6123 4567") == "61234567"
    assert normalize_name("  aLiCe chAn ") == "Alice Chan"


def test_normalize_dob_gender_and_address() -> None:
    assert normalize_dob("1990-01-02") == "1990-01-02"
    assert normalize_gender("female") == "F"
    assert normalize_address("1 Main Road, Kowloon") == ["1", "main", "road", "kowloon"]


def test_normalize_field_dispatch_and_canonicalization() -> None:
    assert normalize_field("email", "USER@EXAMPLE.COM") == "user@example.com"
    assert canonicalize_payload({"b": 1, "a": 2}) == '{"a":2,"b":1}'
