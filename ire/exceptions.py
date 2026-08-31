class IREError(Exception):
    """Base application exception."""


class ConfigurationError(IREError):
    """Raised when configuration is invalid or missing."""


class ValidationError(IREError):
    """Raised when model validation fails."""


class RepositoryError(IREError):
    """Raised for repository and storage errors."""


class DuplicateSourceRecordError(RepositoryError):
    """Raised when duplicate source system/pk/payload_hash is appended."""


class NotFoundError(RepositoryError):
    """Raised when an expected entity cannot be found."""


class InvalidReviewDecisionError(ValidationError):
    """Raised when a review decision is invalid for the current state."""


class GoldenRecordConflictError(ValidationError):
    """Raised when a Golden Record update has a conflict."""


class StaleVersionError(ValidationError):
    """Raised when optimistic concurrency detects a stale entity version."""


class MergeBlockedError(ValidationError):
    """Raised when a Golden-to-Golden merge is blocked by a safety rule."""
