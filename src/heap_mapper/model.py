from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

PointerValue = int | None


class InfeasiblePathError(ValueError):
    """Raised when the selected execution path has contradictory constraints."""


@dataclass(frozen=True)
class PathOperation:
    """A single normalized operation from the selected execution path."""

    op: str
    expr: str | None = None
    lhs: str | None = None
    rhs: str | None = None
    var: str | None = None
    raw: dict[str, Any] = field(default_factory=dict)


@dataclass
class MappingResult:
    """Pseudo-address model built for a selected path."""

    memory: dict[int, dict[str, PointerValue]] = field(default_factory=dict)
    env: dict[str, PointerValue] = field(default_factory=dict)
    values: dict[tuple[int, str], int] = field(default_factory=dict)
    node_states: dict[int, str] = field(default_factory=dict)
    input_addresses: set[int] = field(default_factory=set)
    constraints: list[str] = field(default_factory=list)
    target_function: str = "target_function"
    root_variable: str = "p"

    def pointer_links(self) -> dict[int, dict[str, PointerValue]]:
        """Return pointer fields for nodes that belong to the input structure."""

        return {
            address: dict(self.memory.get(address, {}))
            for address in sorted(self.input_addresses)
        }

    def numeric_values(self) -> dict[int, dict[str, int]]:
        """Return numeric fields grouped by pseudo-address."""

        grouped: dict[int, dict[str, int]] = {}
        for (address, field_name), value in sorted(self.values.items()):
            if address in self.input_addresses:
                grouped.setdefault(address, {})[field_name] = value
        return grouped
