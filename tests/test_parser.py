from heap_mapper.parser import parse_operations


def testParsesOperationList():
    operations = parse_operations(
        [
            {"op": "constraint", "expr": "p != NULL"},
            {"op": "assign", "lhs": "x", "rhs": "p.next"},
        ]
    )

    assert operations[0].op == "constraint"
    assert operations[0].expr == "p != NULL"
    assert operations[1].lhs == "x"
    assert operations[1].rhs == "p.next"


def testParsesPathObject():
    operations = parse_operations({"path": [{"op": "constraint", "expr": "p == NULL"}]})

    assert len(operations) == 1
    assert operations[0].expr == "p == NULL"
