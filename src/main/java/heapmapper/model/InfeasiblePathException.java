package heapmapper.model;

public class InfeasiblePathException extends RuntimeException {

    public InfeasiblePathException(String message) {
        super(message);
    }

    public static InfeasiblePathException pointerEqualityConflict() {
        return new InfeasiblePathException("conflicting pointer equality constraints");
    }

    public static InfeasiblePathException pointerInequalityConflict() {
        return new InfeasiblePathException("conflicting pointer inequality constraints");
    }

    public static InfeasiblePathException nullabilityConflict(String name) {
        return new InfeasiblePathException(name + " is both NULL and non-NULL");
    }

}
