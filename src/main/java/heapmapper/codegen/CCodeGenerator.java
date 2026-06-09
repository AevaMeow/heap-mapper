package heapmapper.codegen;

import heapmapper.model.FieldKey;
import heapmapper.model.FieldModel;
import heapmapper.model.MappingResult;
import heapmapper.model.ResultStatus;
import heapmapper.model.StructureModel;

import java.util.*;

public final class CCodeGenerator {

    public String generate(MappingResult result) {
        StructureModel structure = result.structure();
        String structName = structure.name();
        List<Integer> addresses = new ArrayList<>(result.inputAddresses());
        Collections.sort(addresses);

        var lines = new ArrayList<String>();
        lines.add("#include <stdio.h>");
        lines.add("#include <stdlib.h>");
        lines.add("");
        lines.add("typedef struct " + structName + " {");
        for (FieldModel field : structure.fields()) {
            lines.add("    " + fieldDeclaration(field) + ";");
        }
        lines.add("} " + structName + ";");
        lines.add("");
        lines.add("void " + result.targetFunction()
                + "(" + structName + " *" + result.rootVariable() + ");");
        lines.add("");
        lines.add("int main(void) {");

        if (!addresses.isEmpty()) {
            for (int addr : addresses) {
                lines.add("    " + structName + " *n" + addr
                        + " = malloc(sizeof(" + structName + "));");
            }
            lines.add("");
            for (int addr : addresses) {
                lines.add("    if (n" + addr + " == NULL) {");
                lines.add("        return 1;");
                lines.add("    }");
            }
            lines.add("");
        }

        for (int addr : addresses) {
            for (FieldModel field : structure.integerFields()) {
                int value = result.values().getOrDefault(
                        new FieldKey(addr, field.name()), 0);
                lines.add("    n" + addr + "->" + field.name() + " = " + value + ";");
            }
        }
        if (!addresses.isEmpty()) lines.add("");

        for (int addr : addresses) {
            for (FieldModel field : structure.pointerFields()) {
                Integer target = result.memory()
                        .getOrDefault(addr, Map.of())
                        .get(field.name());
                lines.add("    n" + addr + "->" + field.name()
                        + " = " + pointerLiteral(target, addresses) + ";");
            }
        }
        if (!addresses.isEmpty()) lines.add("");

        Integer rootVal = result.env().get(result.rootVariable());
        lines.add("    " + structName + " *" + result.rootVariable()
                + " = " + pointerLiteral(rootVal, addresses) + ";");
        lines.add("");

        if (result.status() == ResultStatus.VIOLATION) {
            var reversed = new ArrayList<>(addresses);
            Collections.reverse(reversed);
            for (int addr : reversed) {
                if ("freed".equals(result.nodeStates().get(addr))) {
                    lines.add("    free(n" + addr + ");");
                }
            }
            lines.add("    puts(\"HEAP_MAPPER_TARGET_REACHED\");");
            lines.add("");
        }

        lines.add("    " + result.targetFunction() + "(" + result.rootVariable() + ");");

        if (result.status() == ResultStatus.SAFE) {
            lines.add("    puts(\"HEAP_MAPPER_TARGET_REACHED\");");
        }

        if (!addresses.isEmpty()) {
            var reversed = new ArrayList<>(addresses);
            Collections.reverse(reversed);
            boolean hasLive = reversed.stream()
                    .anyMatch(addr -> !"freed".equals(result.nodeStates().get(addr)));
            if (hasLive) {
                lines.add("");
                for (int addr : reversed) {
                    if (!"freed".equals(result.nodeStates().get(addr))) {
                        lines.add("    free(n" + addr + ");");
                    }
                }
            }
        }

        lines.add("");
        lines.add("    return 0;");
        lines.add("}");
        lines.add("");

        return String.join("\n", lines);
    }

    private static String pointerLiteral(Integer value, List<Integer> addresses) {
        if (value == null || !addresses.contains(value)) return "NULL";
        return "n" + value;
    }

    private static String fieldDeclaration(FieldModel field) {
        return switch (field.kind()) {
            case INT -> "int " + field.name();
            case POINTER -> "struct " + field.target() + " *" + field.name();
        };
    }
}
