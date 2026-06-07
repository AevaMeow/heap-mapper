package heapmapper

import heapmapper.codegen.CCodeGenerator
import heapmapper.mapper.AddressMapper
import heapmapper.model.PathOperation
import heapmapper.parser.PathParser
import spock.lang.Specification

class CodegenSpec extends Specification {

    def "generates valid C harness for a two-node list"() {
        given:
        def result = new AddressMapper().processPath([
            new PathOperation.Constraint("p != NULL"),
            new PathOperation.Assign("x", "p.next"),
            new PathOperation.Constraint("x.key == 5"),
        ])

        when:
        def code = new CCodeGenerator().generate(result)

        then:
        code.contains("Node *n1 = malloc(sizeof(Node));")
        code.contains("Node *n2 = malloc(sizeof(Node));")
        code.contains("n1->next = n2;")
        code.contains("n2->key = 5;")
        code.contains("Node *p = n1;")
        code.contains("target_function(p);")
    }

    def "generates a doubly linked list with next and prev links"() {
        given:
        def json = '''
            {
              "structure": {
                "name": "DNode",
                "fields": [
                  {"name": "key", "type": "int"},
                  {"name": "next", "type": "pointer", "target": "DNode"},
                  {"name": "prev", "type": "pointer", "target": "DNode"}
                ]
              },
              "entry_pointer": "head",
              "target_function": "inspect_doubly_list",
              "path": [
                {"op": "constraint", "expr": "head != NULL"},
                {"op": "assign", "lhs": "second", "rhs": "head.next"},
                {"op": "assign", "lhs": "second.prev", "rhs": "head"}
              ]
            }
        '''

        when:
        def code = generate(json)

        then:
        code.contains("struct DNode *next;")
        code.contains("struct DNode *prev;")
        code.contains("n1->next = n2;")
        code.contains("n2->prev = n1;")
        code.contains("DNode *head = n1;")
        code.contains("inspect_doubly_list(head);")
    }

    def "generates a binary tree with two children"() {
        given:
        def json = '''
            {
              "structure": {
                "name": "TreeNode",
                "fields": [
                  {"name": "value", "type": "int"},
                  {"name": "left", "type": "pointer", "target": "TreeNode"},
                  {"name": "right", "type": "pointer", "target": "TreeNode"}
                ]
              },
              "entry_pointer": "root",
              "target_function": "inspect_tree",
              "path": [
                {"op": "constraint", "expr": "root != NULL"},
                {"op": "assign", "lhs": "left_child", "rhs": "root.left"},
                {"op": "assign", "lhs": "right_child", "rhs": "root.right"},
                {"op": "constraint", "expr": "left_child.value == 3"},
                {"op": "constraint", "expr": "right_child.value == 9"}
              ]
            }
        '''

        when:
        def code = generate(json)

        then:
        code.contains("int value;")
        code.contains("struct TreeNode *left;")
        code.contains("struct TreeNode *right;")
        code.contains("n1->left = n2;")
        code.contains("n1->right = n3;")
        code.contains("n2->value = 3;")
        code.contains("n3->value = 9;")
    }

    def "generates a cycle without duplicating allocations or frees"() {
        given:
        def json = '''
            {
              "structure": {
                "name": "Node",
                "fields": [
                  {"name": "key", "type": "int"},
                  {"name": "next", "type": "pointer", "target": "Node"}
                ]
              },
              "entry_pointer": "head",
              "target_function": "inspect_cycle",
              "path": [
                {"op": "constraint", "expr": "head != NULL"},
                {"op": "assign", "lhs": "second", "rhs": "head.next"},
                {"op": "assign", "lhs": "second.next", "rhs": "head"}
              ]
            }
        '''

        when:
        def code = generate(json)

        then:
        code.contains("n1->next = n2;")
        code.contains("n2->next = n1;")
        code.count("malloc(sizeof(Node))") == 2
        code.count("free(n1);") == 1
        code.count("free(n2);") == 1
    }

    def "does not free a node that the selected path already releases"() {
        given:
        def result = new AddressMapper().processPath([
            new PathOperation.Constraint("p != NULL"),
            new PathOperation.Free("p"),
        ])

        when:
        def code = new CCodeGenerator().generate(result)

        then:
        !code.contains("free(n1);")
    }

    def "generates an input harness for a reachable use-after-free violation"() {
        given:
        def result = new AddressMapper().processPath([
            new PathOperation.Constraint("p != NULL"),
            new PathOperation.Free("p"),
            new PathOperation.Deref("p.key"),
        ])

        when:
        def code = new CCodeGenerator().generate(result)

        then:
        result.status().name() == "VIOLATION"
        code.contains("Node *n1 = malloc(sizeof(Node));")
        code.contains("target_function(p);")
        !code.contains("free(n1);")
    }

    private static String generate(String json) {
        def document = PathParser.parseDocumentFromString(json)
        def result = new AddressMapper(
            document.entryPointer(),
            document.targetFunction(),
            document.structure()
        ).processPath(document.operations())
        return new CCodeGenerator().generate(result)
    }
}
