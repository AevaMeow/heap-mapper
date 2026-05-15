from heap_mapper.codegen import CCodeGenerator
from heap_mapper.mapper import AddressMapper
from heap_mapper.parser import parse_operations


def testGeneratesCHarness():
    operations = parse_operations(
        [
            {"op": "constraint", "expr": "p != NULL"},
            {"op": "assign", "lhs": "x", "rhs": "p.next"},
            {"op": "constraint", "expr": "x.key == 5"},
        ]
    )
    result = AddressMapper().process_path(operations)

    code = CCodeGenerator().generate(result)

    assert "Node *n1 = malloc(sizeof(Node));" in code
    assert "Node *n2 = malloc(sizeof(Node));" in code
    assert "n1->next = n2;" in code
    assert "n2->key = 5;" in code
    assert "Node *p = n1;" in code
    assert "target_function(p);" in code
