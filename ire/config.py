from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from .enums import SourceTrustLevel
from .exceptions import ConfigurationError
from .models import SourceSystem


@dataclass(frozen=True)
class MatchingThresholds:
    auto_merge: float
    manual_review: float
    candidate_gap: float

    def __post_init__(self) -> None:
        for name, value in (
            ("auto_merge", self.auto_merge),
            ("manual_review", self.manual_review),
            ("candidate_gap", self.candidate_gap),
        ):
            if not (0.0 <= value <= 1.0):
                raise ConfigurationError(f"threshold {name} must be between 0 and 1")
        if self.auto_merge < self.manual_review:
            raise ConfigurationError("auto_merge must be >= manual_review")


@dataclass(frozen=True)
class MatchingPolicy:
    version: str
    thresholds: MatchingThresholds
    field_priorities: tuple[str, ...]
    algorithms: tuple[str, ...]
    candidate_limit: int
    full_scan_limit: int
    min_comparable_fields: int
    safety: dict[str, Any]

    def __post_init__(self) -> None:
        if self.candidate_limit <= 0:
            raise ConfigurationError("candidate_limit must be > 0")
        if self.full_scan_limit <= 0:
            raise ConfigurationError("full_scan_limit must be > 0")
        if self.min_comparable_fields <= 0:
            raise ConfigurationError("min_comparable_fields must be > 0")


@dataclass(frozen=True)
class SurvivorshipPolicy:
    version: str
    protect_manual_lock: bool
    field_strategies: dict[str, str]


@dataclass(frozen=True)
class IREConfig:
    source_systems: tuple[SourceSystem, ...]
    matching_policy: MatchingPolicy
    survivorship_policy: SurvivorshipPolicy


def _load_json_file(path: Path) -> Any:
    if not path.exists():
        raise ConfigurationError(f"required config file not found: {path}")
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise ConfigurationError(f"invalid JSON in config file {path}: {exc}") from exc


def _load_source_systems(path: Path) -> tuple[SourceSystem, ...]:
    data = _load_json_file(path)
    if not isinstance(data, list) or not data:
        raise ConfigurationError("source_systems.json must be a non-empty JSON array")
    systems: list[SourceSystem] = []
    for item in data:
        if "trust_level" not in item:
            raise ConfigurationError("source system missing trust_level")
        item = dict(item)
        item["trust_level"] = SourceTrustLevel(item["trust_level"])
        systems.append(SourceSystem.from_dict(item))
    return tuple(systems)


def _load_matching_policy(path: Path) -> MatchingPolicy:
    data = _load_json_file(path)
    try:
        thresholds = MatchingThresholds(
            auto_merge=float(data["thresholds"]["auto_merge"]),
            manual_review=float(data["thresholds"]["manual_review"]),
            candidate_gap=float(data["thresholds"]["candidate_gap"]),
        )
        return MatchingPolicy(
            version=str(data["version"]),
            thresholds=thresholds,
            field_priorities=tuple(data["field_priorities"]),
            algorithms=tuple(data["algorithms"]),
            candidate_limit=int(data["candidate_limit"]),
            full_scan_limit=int(data.get("full_scan_limit", data.get("safety", {}).get("full_scan_limit", 50))),
            min_comparable_fields=int(data.get("min_comparable_fields", 2)),
            safety=dict(data["safety"]),
        )
    except KeyError as exc:
        raise ConfigurationError(f"matching policy missing required key: {exc}") from exc


def _load_survivorship_policy(path: Path) -> SurvivorshipPolicy:
    data = _load_json_file(path)
    try:
        return SurvivorshipPolicy(
            version=str(data["version"]),
            protect_manual_lock=bool(data["protect_manual_lock"]),
            field_strategies=dict(data["field_strategies"]),
        )
    except KeyError as exc:
        raise ConfigurationError(f"survivorship policy missing required key: {exc}") from exc


def load_config(config_dir: str | Path) -> IREConfig:
    root = Path(config_dir)
    source_systems = _load_source_systems(root / "source_systems.json")
    matching_policy = _load_matching_policy(root / "matching_policy.json")
    survivorship_policy = _load_survivorship_policy(root / "survivorship_policy.json")
    return IREConfig(
        source_systems=source_systems,
        matching_policy=matching_policy,
        survivorship_policy=survivorship_policy,
    )
