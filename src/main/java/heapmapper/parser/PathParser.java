package heapmapper.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import heapmapper.model.FieldModel;
import heapmapper.model.PathDocument;
import heapmapper.model.PathOperation;
import heapmapper.model.StructureModel;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PathParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static List<PathOperation> loadPath(Path file) throws IOException {
        return loadDocument(file).operations();
    }

    public static PathDocument loadDocument(Path file) throws IOException {
        return parseDocument(MAPPER.readTree(file.toFile()));
    }

    public static PathDocument parseDocument(JsonNode root) {
        StructureModel structure = StructureModel.defaultNode();
        String entryPointer = "p";
        String targetFunction = "target_function";

        if (root.isObject()) {
            if (root.has("structure")) {
                structure = parseStructure(root.get("structure"));
            }
            entryPointer = optionalString(root, "entry_pointer", entryPointer);
            targetFunction = optionalString(root, "target_function", targetFunction);
        }

        return new PathDocument(
                structure,
                entryPointer,
                targetFunction,
                parseOperations(root));
    }

    public static List<PathOperation> parseOperations(JsonNode root) {
        JsonNode array;
        if (root.isArray()) {
            array = root;
        } else if (root.isObject() && root.has("path")) {
            array = root.get("path");
        } else {
            throw new IllegalArgumentException(
                    "path JSON must be a list or an object with a 'path' list");
        }
        if (!array.isArray()) {
            throw new IllegalArgumentException(
                    "path JSON must be a list or an object with a 'path' list");
        }

        var operations = new ArrayList<PathOperation>();
        for (JsonNode item : array) {
            operations.add(parseOperation(item));
        }
        return operations;
    }

    public static List<PathOperation> parseOperationsFromString(String json) throws IOException {
        return parseOperations(MAPPER.readTree(json));
    }

    public static PathDocument parseDocumentFromString(String json) throws IOException {
        return parseDocument(MAPPER.readTree(json));
    }

    private static PathOperation parseOperation(JsonNode item) {
        if (!item.isObject()) {
            throw new IllegalArgumentException(
                    "path operation must be an object, got: " + item);
        }
        JsonNode opNode = item.get("op");
        if (opNode == null || !opNode.isTextual()) {
            throw new IllegalArgumentException(
                    "path operation is missing string 'op': " + item);
        }
        return switch (opNode.asText()) {
            case "constraint" -> new PathOperation.Constraint(requiredString(item, "expr"));
            case "assign"     -> new PathOperation.Assign(
                    requiredString(item, "lhs"), requiredString(item, "rhs"));
            case "malloc"     -> new PathOperation.Malloc(requiredString(item, "var"));
            case "free"       -> new PathOperation.Free(requiredString(item, "var"));
            case "deref"      -> new PathOperation.Deref(requiredString(item, "expr"));
            default -> throw new IllegalArgumentException(
                    "unsupported path operation '" + opNode.asText() + "'");
        };
    }

    private static String requiredString(JsonNode item, String key) {
        JsonNode node = item.get(key);
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            throw new IllegalArgumentException(
                    "path operation is missing string '" + key + "': " + item);
        }
        return node.asText().strip();
    }

    private static String optionalString(JsonNode item, String key, String defaultValue) {
        JsonNode node = item.get(key);
        if (node == null) return defaultValue;
        if (!node.isTextual() || node.asText().isBlank()) {
            throw new IllegalArgumentException("'" + key + "' must be a non-blank string");
        }
        return node.asText().strip();
    }

    private static StructureModel parseStructure(JsonNode node) {
        if (!node.isObject()) {
            throw new IllegalArgumentException("'structure' must be an object");
        }
        String name = requiredString(node, "name");
        JsonNode fieldsNode = node.get("fields");
        if (fieldsNode == null || !fieldsNode.isArray()) {
            throw new IllegalArgumentException("structure is missing array 'fields'");
        }

        var fields = new ArrayList<FieldModel>();
        for (JsonNode fieldNode : fieldsNode) {
            if (!fieldNode.isObject()) {
                throw new IllegalArgumentException("structure field must be an object");
            }
            String fieldName = requiredString(fieldNode, "name");
            String type = requiredString(fieldNode, "type");
            fields.add(switch (type) {
                case "int" -> FieldModel.intField(fieldName);
                case "pointer" -> FieldModel.pointerField(
                        fieldName, requiredString(fieldNode, "target"));
                default -> throw new IllegalArgumentException(
                        "unsupported field type '" + type + "' for " + fieldName);
            });
        }
        return new StructureModel(name, fields);
    }
}
