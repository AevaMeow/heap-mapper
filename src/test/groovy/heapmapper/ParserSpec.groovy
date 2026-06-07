package heapmapper

import heapmapper.model.PathOperation
import heapmapper.parser.PathParser
import spock.lang.Specification

class ParserSpec extends Specification {

    def "parses a flat operation list"() {
        given:
        def json = '''
            [
              {"op": "constraint", "expr": "p != NULL"},
              {"op": "assign",     "lhs": "x", "rhs": "p.next"}
            ]
        '''

        when:
        def ops = PathParser.parseOperationsFromString(json)

        then:
        ops.size() == 2
        ops[0] == new PathOperation.Constraint("p != NULL")
        ops[1] == new PathOperation.Assign("x", "p.next")
    }

    def "parses a path-wrapper object"() {
        given:
        def json = '{"path": [{"op": "constraint", "expr": "p == NULL"}]}'

        when:
        def ops = PathParser.parseOperationsFromString(json)

        then:
        ops.size() == 1
        ops[0] == new PathOperation.Constraint("p == NULL")
    }

    def "parses an explicit dereference operation"() {
        given:
        def json = '[{"op": "deref", "expr": "x.key"}]'

        when:
        def ops = PathParser.parseOperationsFromString(json)

        then:
        ops == [new PathOperation.Deref("x.key")]
    }

    def "parses structure model and document metadata"() {
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
              "path": [{"op": "constraint", "expr": "root != NULL"}]
            }
        '''

        when:
        def document = PathParser.parseDocumentFromString(json)

        then:
        document.structure().name() == "TreeNode"
        document.structure().integerFields()*.name() == ["value"]
        document.structure().pointerFields()*.name() == ["left", "right"]
        document.entryPointer() == "root"
        document.targetFunction() == "inspect_tree"
        document.operations() == [new PathOperation.Constraint("root != NULL")]
    }
}
