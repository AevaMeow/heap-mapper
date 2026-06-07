package heapmapper.model;

public sealed interface PathOperation
        permits PathOperation.Constraint, PathOperation.Assign,
                PathOperation.Malloc, PathOperation.Free, PathOperation.Deref {

    record Constraint(String expr) implements PathOperation {}

    record Assign(String lhs, String rhs) implements PathOperation {}

    record Malloc(String var) implements PathOperation {}

    record Free(String var) implements PathOperation {}

    record Deref(String expr) implements PathOperation {}
}
