from __future__ import annotations

import json
from pathlib import Path
from typing import Any, Iterable

from heap_mapper.model import PathOperation


def load_path(path: str | Path) -> list[PathOperation]:
    """Load a selected path from a JSON file."""

    source = Path(path)
    with source.open(encoding="utf-8") as file:
        data = json.load(file)
    return parse_operations(data)


def parse_operations(data: Any) -> list[PathOperation]:
    """Convert JSON data into normalized path operations.

    Accepted top-level shapes:
    - a plain list of operations;
    - an object with a "path" key containing the operation list.
    """

    if isinstance(data, dict):
        data = data.get("path")

    if not isinstance(data, list):
        raise ValueError("path JSON must be a list or an object with a 'path' list")

    return [_parse_operation(item) for item in data]


def _parse_operation(item: Any) -> PathOperation:
    if not isinstance(item, dict):
        raise ValueError(f"path operation must be an object, got {item!r}")

    op = item.get("op")
    if not isinstance(op, str):
        raise ValueError(f"path operation is missing string 'op': {item!r}")

    match op:
        case "constraint":
            expr = _required_string(item, "expr")
            return PathOperation(op=op, expr=expr, raw=dict(item))
        case "assign":
            lhs = _required_string(item, "lhs")
            rhs = _required_string(item, "rhs")
            return PathOperation(op=op, lhs=lhs, rhs=rhs, raw=dict(item))
        case "malloc" | "free":
            var = _required_string(item, "var")
            return PathOperation(op=op, var=var, raw=dict(item))
        case _:
            raise ValueError(f"unsupported path operation '{op}'")


def _required_string(item: dict[str, Any], key: str) -> str:
    value = item.get(key)
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"path operation is missing string '{key}': {item!r}")
    return value.strip()


def operations_to_json(operations: Iterable[PathOperation]) -> str:
    """Serialize operations back to a readable JSON form."""

    payload = []
    for operation in operations:
        if operation.raw:
            payload.append(operation.raw)
        elif operation.op == "constraint":
            payload.append({"op": operation.op, "expr": operation.expr})
        elif operation.op == "assign":
            payload.append({"op": operation.op, "lhs": operation.lhs, "rhs": operation.rhs})
        else:
            payload.append({"op": operation.op, "var": operation.var})
    return json.dumps(payload, indent=2)
