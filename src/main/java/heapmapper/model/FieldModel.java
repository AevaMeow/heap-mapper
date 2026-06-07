package heapmapper.model;

import java.util.Objects;

public record FieldModel(String name, Kind kind, String target) {

    public enum Kind {
        INT,
        POINTER
    }

    public FieldModel {
        Objects.requireNonNull(name, "field name");
        Objects.requireNonNull(kind, "field kind");
        if (name.isBlank()) {
            throw new IllegalArgumentException("field name must not be blank");
        }
        if (kind == Kind.POINTER && (target == null || target.isBlank())) {
            throw new IllegalArgumentException("pointer field '" + name + "' requires a target");
        }
        if (kind == Kind.INT && target != null) {
            throw new IllegalArgumentException("integer field '" + name + "' must not have a target");
        }
    }

    public static FieldModel intField(String name) {
        return new FieldModel(name, Kind.INT, null);
    }

    public static FieldModel pointerField(String name, String target) {
        return new FieldModel(name, Kind.POINTER, target);
    }
}
