import pytest

from heap_mapper.mapper import AddressMapper
from heap_mapper.model import InfeasiblePathError
from heap_mapper.parser import parse_operations


def mapPath(items):
    operations = parse_operations(items)
    return AddressMapper().process_path(operations)


def testBuildsListShape():
    result = mapPath(
        [
            {"op": "constraint", "expr": "p != NULL"},
            {"op": "assign", "lhs": "x", "rhs": "p.next"},
            {"op": "constraint", "expr": "x != NULL"},
            {"op": "constraint", "expr": "x.key == 5"},
        ]
    )

    assert result.env == {"p": 1, "x": 2}
    assert result.memory[1]["next"] == 2
    assert result.values[(2, "key")] == 5
    assert result.input_addresses == {1, 2}


def testDetectsNullDereference():
    operations = parse_operations(
        [
            {"op": "constraint", "expr": "p == NULL"},
            {"op": "assign", "lhs": "x", "rhs": "p.next"},
        ]
    )

    with pytest.raises(InfeasiblePathError, match="dereference of NULL pointer p"):
        AddressMapper().process_path(operations)


def testDetectsPointerEqualityConflict():
    operations = parse_operations(
        [
            {"op": "constraint", "expr": "p != NULL"},
            {"op": "constraint", "expr": "q != NULL"},
            {"op": "constraint", "expr": "p == q"},
        ]
    )

    with pytest.raises(InfeasiblePathError, match="conflicting pointer equality"):
        AddressMapper().process_path(operations)


def testDetectsPointerInequalityConflict():
    operations = parse_operations(
        [
            {"op": "constraint", "expr": "p != NULL"},
            {"op": "constraint", "expr": "q == p"},
            {"op": "constraint", "expr": "p != q"},
        ]
    )

    with pytest.raises(InfeasiblePathError, match="conflicting pointer inequality"):
        AddressMapper().process_path(operations)


def testPointerInequalityCreatesDistinctValues():
    result = mapPath(
        [
            {"op": "constraint", "expr": "p != q"},
        ]
    )

    assert result.env["p"] != result.env["q"]


def testDetectsFieldNullabilityConflict():
    operations = parse_operations(
        [
            {"op": "constraint", "expr": "p != NULL"},
            {"op": "constraint", "expr": "p.next == NULL"},
            {"op": "assign", "lhs": "x", "rhs": "p.next"},
            {"op": "constraint", "expr": "x != NULL"},
        ]
    )

    with pytest.raises(InfeasiblePathError, match="both NULL and non-NULL"):
        AddressMapper().process_path(operations)
