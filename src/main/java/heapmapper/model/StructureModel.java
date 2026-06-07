package heapmapper.model;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public record StructureModel(String name, List<FieldModel> fields) {

    private static final Pattern C_IDENTIFIER =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    public StructureModel {
        Objects.requireNonNull(name, "structure name");
        Objects.requireNonNull(fields, "structure fields");
        if (!C_IDENTIFIER.matcher(name).matches()) {
            throw new IllegalArgumentException("invalid C structure name: " + name);
        }
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("structure must declare at least one field");
        }

        fields = List.copyOf(fields);
        var names = new HashSet<String>();
        for (FieldModel field : fields) {
            if (!C_IDENTIFIER.matcher(field.name()).matches()) {
                throw new IllegalArgumentException("invalid C field name: " + field.name());
            }
            if (!names.add(field.name())) {
                throw new IllegalArgumentException("duplicate structure field: " + field.name());
            }
            if (field.kind() == FieldModel.Kind.POINTER && !name.equals(field.target())) {
                throw new IllegalArgumentException(
                        "MVP supports self-referential pointer fields only: "
                                + field.name() + " targets " + field.target());
            }
        }
    }

    public static StructureModel defaultNode() {
        return new StructureModel("Node", List.of(
                FieldModel.intField("key"),
                FieldModel.pointerField("next", "Node")));
    }

    public FieldModel requireField(String fieldName) {
        return fields.stream()
                .filter(field -> field.name().equals(fieldName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "structure " + name + " has no field '" + fieldName + "'"));
    }

    public void requirePointerField(String fieldName) {
        FieldModel field = requireField(fieldName);
        if (field.kind() != FieldModel.Kind.POINTER) {
            throw new IllegalArgumentException(
                    name + "." + fieldName + " is not a pointer field");
        }
    }

    public void requireIntegerField(String fieldName) {
        FieldModel field = requireField(fieldName);
        if (field.kind() != FieldModel.Kind.INT) {
            throw new IllegalArgumentException(
                    name + "." + fieldName + " is not an integer field");
        }
    }

    public List<FieldModel> integerFields() {
        return fields.stream()
                .filter(field -> field.kind() == FieldModel.Kind.INT)
                .toList();
    }

    public List<FieldModel> pointerFields() {
        return fields.stream()
                .filter(field -> field.kind() == FieldModel.Kind.POINTER)
                .toList();
    }
}
