package heapmapper.model;

public final class PathViolationException extends RuntimeException {

    private final ViolationKind kind;

    public PathViolationException(ViolationKind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public ViolationKind kind() {
        return kind;
    }

    public static PathViolationException nullDereference(String name) {
        return new PathViolationException(
                ViolationKind.NULL_DEREFERENCE,
                "dereference of NULL pointer " + name);
    }

    public static PathViolationException useAfterFree(String name) {
        return new PathViolationException(
                ViolationKind.USE_AFTER_FREE,
                "dereference after free of pointer " + name);
    }

    public static PathViolationException doubleFree(String name) {
        return new PathViolationException(
                ViolationKind.DOUBLE_FREE,
                "double free of pointer " + name);
    }
}
