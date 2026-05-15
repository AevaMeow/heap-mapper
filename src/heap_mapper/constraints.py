from __future__ import annotations

from heap_mapper.model import InfeasiblePathError


def pointer_equality_conflict() -> InfeasiblePathError:
    return InfeasiblePathError("conflicting pointer equality constraints")


def pointer_inequality_conflict() -> InfeasiblePathError:
    return InfeasiblePathError("conflicting pointer inequality constraints")


def nullability_conflict(name: str) -> InfeasiblePathError:
    return InfeasiblePathError(f"{name} is both NULL and non-NULL")


def dereference_null(name: str) -> InfeasiblePathError:
    return InfeasiblePathError(f"dereference of NULL pointer {name}")
