from __future__ import annotations


def exact_match(a: str | None, b: str | None) -> float:
    if a is None or b is None:
        return 0.0
    return 1.0 if a == b else 0.0


def jaro_winkler(a: str | None, b: str | None) -> float:
    if not a or not b:
        return 0.0
    if a == b:
        return 1.0

    len_a = len(a)
    len_b = len(b)
    match_distance = max(len_a, len_b) // 2 - 1
    a_matches = [False] * len_a
    b_matches = [False] * len_b

    matches = 0
    for i, char_a in enumerate(a):
        start = max(0, i - match_distance)
        end = min(i + match_distance + 1, len_b)
        for j in range(start, end):
            if b_matches[j] or b[j] != char_a:
                continue
            a_matches[i] = True
            b_matches[j] = True
            matches += 1
            break

    if matches == 0:
        return 0.0

    transpositions = 0
    j = 0
    for i in range(len_a):
        if not a_matches[i]:
            continue
        while not b_matches[j]:
            j += 1
        if a[i] != b[j]:
            transpositions += 1
        j += 1
    transpositions /= 2

    jaro = (
        (matches / len_a) +
        (matches / len_b) +
        ((matches - transpositions) / matches)
    ) / 3.0

    prefix = 0
    for char_a, char_b in zip(a[:4], b[:4]):
        if char_a != char_b:
            break
        prefix += 1

    return min(1.0, jaro + prefix * 0.1 * (1.0 - jaro))


def email_similarity(a: str | None, b: str | None) -> float:
    if not a or not b:
        return 0.0
    if a == b:
        return 1.0
    if "@" not in a or "@" not in b:
        return 0.0
    local_a, domain_a = a.split("@", 1)
    local_b, domain_b = b.split("@", 1)
    if domain_a == domain_b:
        return 0.7 if local_a == local_b else 0.5 * jaro_winkler(local_a, local_b) + 0.2
    if local_a == local_b:
        return 0.6
    return 0.5 * jaro_winkler(local_a, local_b) + 0.3 * jaro_winkler(domain_a, domain_b)


def phone_similarity(a: str | None, b: str | None) -> float:
    if not a or not b:
        return 0.0
    if a == b:
        return 1.0
    if a[-8:] == b[-8:]:
        return 0.8
    return 0.0


def dob_similarity(a: str | None, b: str | None) -> float:
    return exact_match(a, b)


def gender_similarity(a: str | None, b: str | None) -> float:
    return exact_match(a, b)


def address_jaccard(a_tokens: list[str] | None, b_tokens: list[str] | None) -> float:
    if not a_tokens or not b_tokens:
        return 0.0
    set_a = set(a_tokens)
    set_b = set(b_tokens)
    if not set_a or not set_b:
        return 0.0
    return len(set_a & set_b) / len(set_a | set_b)


def name_similarity(a: str | None, b: str | None) -> float:
    return jaro_winkler(a, b)
