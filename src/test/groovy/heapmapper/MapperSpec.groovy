package heapmapper

import heapmapper.mapper.AddressMapper
import heapmapper.model.FieldKey
import heapmapper.model.InfeasiblePathException
import heapmapper.model.PathOperation
import heapmapper.model.ResultStatus
import heapmapper.model.ViolationKind
import spock.lang.Specification

class MapperSpec extends Specification {

    def "builds linked-list shape: p -> n1 -> n2, n2.key = 5"() {
        when:
        def result = new AddressMapper().processPath([
            new PathOperation.Constraint("p != NULL"),
            new PathOperation.Assign("x", "p.next"),
            new PathOperation.Constraint("x != NULL"),
            new PathOperation.Constraint("x.key == 5"),
        ])

        then:
        result.env().get("p")                       == 1
        result.env().get("x")                       == 2
        result.memory().get(1).get("next")          == 2
        result.values().get(new FieldKey(2, "key")) == 5
        result.inputAddresses()                     == [1, 2] as Set
    }

    def "detects infeasible path: #description"() {
        when:
        new AddressMapper().processPath(ops)

        then:
        def ex = thrown(InfeasiblePathException)
        ex.message.contains(expectedMessage)

        where:
        description                   | ops                                                          | expectedMessage
        "pointer equality conflict"   | [con("p != NULL"),  con("q != NULL"),  con("p == q")]         | "conflicting pointer equality"
        "pointer inequality conflict" | [con("p != NULL"),  con("q == p"),     con("p != q")]         | "conflicting pointer inequality"
        "field nullability conflict"  | [con("p != NULL"),  con("p.next == NULL"), asgn("x", "p.next"), con("x != NULL")] | "both NULL and non-NULL"
        "numeric conflict"            | [con("p != NULL"), con("p.key == 5"), con("p.key == 7")]       | "conflicting numeric constraint"
    }

    def "preserves input model for reachable violation: #description"() {
        when:
        def result = new AddressMapper().processPath(ops)

        then:
        result.status() == ResultStatus.VIOLATION
        result.violationKind() == kind
        result.diagnostic().contains(expectedMessage)
        result.terminalOperationIndex() == terminalIndex

        where:
        description                | ops                                                      | kind                           | terminalIndex | expectedMessage
        "null field read"          | [con("p == NULL"), asgn("x", "p.next")]                  | ViolationKind.NULL_DEREFERENCE | 1             | "dereference of NULL pointer p"
        "explicit null deref"      | [con("p == NULL"), deref("p.key")]                       | ViolationKind.NULL_DEREFERENCE | 1             | "dereference of NULL pointer p"
        "numeric use after free"   | [con("p != NULL"), free("p"), con("p.key == 5")]          | ViolationKind.USE_AFTER_FREE    | 2             | "dereference after free of pointer p"
        "explicit use after free"  | [con("p != NULL"), free("p"), deref("p.key")]             | ViolationKind.USE_AFTER_FREE    | 2             | "dereference after free of pointer p"
        "double free"              | [con("p != NULL"), free("p"), free("p")]                  | ViolationKind.DOUBLE_FREE       | 2             | "double free of pointer p"
    }

    def "inequality constraint is materialized as two distinct addresses"() {
        when:
        def result = new AddressMapper().processPath([new PathOperation.Constraint("p != q")])

        then:
        result.env().get("p") != null
        result.env().get("q") != null
        result.env().get("p") != result.env().get("q")
    }

    def "unknown pointer inequality remains feasible when q later becomes non-null"() {
        when:
        def result = new AddressMapper().processPath([
            con("p != q"),
            con("q != NULL"),
        ])

        then:
        result.env().get("p") != null
        result.env().get("q") != null
        result.env().get("p") != result.env().get("q")
    }

    def "explicit dereference accepts a live non-null pointer without assigning a field value"() {
        when:
        def result = new AddressMapper().processPath([
            con("p != NULL"),
            deref("p.key"),
        ])

        then:
        result.env().get("p") != null
        result.values().isEmpty()
    }

    def "free of NULL follows C semantics and remains feasible"() {
        when:
        def result = new AddressMapper().processPath([
            con("p == NULL"),
            free("p"),
        ])

        then:
        result.status() == ResultStatus.SAFE
        result.env().get("p") == null
        result.inputAddresses().isEmpty()
    }

    def "free of an unknown pointer materializes a valid allocated witness"() {
        when:
        def result = new AddressMapper().processPath([free("p")])

        then:
        result.env().get("p") == 1
        result.nodeStates().get(1) == "freed"
        result.inputAddresses() == [1] as Set
    }

    def "assignment from an unknown variable preserves the copied pointer value"() {
        when:
        def result = new AddressMapper().processPath([
            asgn("x", "y"),
            con("x == y"),
        ])

        then:
        result.env().get("x") == result.env().get("y")
        result.inputAddresses().size() == 1
    }

    private static PathOperation con(String expr)            { new PathOperation.Constraint(expr) }
    private static PathOperation asgn(String lhs, String rhs) { new PathOperation.Assign(lhs, rhs) }
    private static PathOperation free(String var)             { new PathOperation.Free(var) }
    private static PathOperation deref(String expr)           { new PathOperation.Deref(expr) }
}
