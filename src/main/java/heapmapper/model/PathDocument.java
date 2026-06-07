package heapmapper.model;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public record PathDocument(
        StructureModel structure,
        String entryPointer,
        String targetFunction,
        List<PathOperation> operations) {

    private static final Pattern C_IDENTIFIER =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    public PathDocument {
        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(entryPointer, "entry pointer");
        Objects.requireNonNull(targetFunction, "target function");
        Objects.requireNonNull(operations, "operations");
        if (!C_IDENTIFIER.matcher(entryPointer).matches()) {
            throw new IllegalArgumentException("invalid entry pointer: " + entryPointer);
        }
        if (!C_IDENTIFIER.matcher(targetFunction).matches()) {
            throw new IllegalArgumentException("invalid target function: " + targetFunction);
        }
        operations = List.copyOf(operations);
    }
}
