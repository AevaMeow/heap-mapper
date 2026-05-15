from __future__ import annotations

from heap_mapper.model import MappingResult, PointerValue


class CCodeGenerator:
    """Generate a small C harness from a pseudo-address mapping result."""

    def __init__(self, *, struct_name: str = "Node", pointer_field: str = "next") -> None:
        self.struct_name = struct_name
        self.pointer_field = pointer_field

    def generate(self, result: MappingResult) -> str:
        addresses = sorted(result.input_addresses)
        lines: list[str] = [
            "#include <stdlib.h>",
            "",
            f"typedef struct {self.struct_name} {{",
            "    int key;",
            f"    struct {self.struct_name} *{self.pointer_field};",
            f"}} {self.struct_name};",
            "",
            f"void {result.target_function}({self.struct_name} *{result.root_variable});",
            "",
            "int main(void) {",
        ]

        if addresses:
            for address in addresses:
                lines.append(
                    f"    {self.struct_name} *n{address} = malloc(sizeof({self.struct_name}));"
                )
            lines.append("")
            for address in addresses:
                lines.append(f"    if (n{address} == NULL) {{")
                lines.append("        return 1;")
                lines.append("    }")
            lines.append("")

        for address in addresses:
            key_value = result.values.get((address, "key"), 0)
            lines.append(f"    n{address}->key = {key_value};")
        if addresses:
            lines.append("")

        for address in addresses:
            next_value = result.memory.get(address, {}).get(self.pointer_field)
            lines.append(
                f"    n{address}->{self.pointer_field} = {self._pointer_literal(next_value, addresses)};"
            )
        if addresses:
            lines.append("")

        root_value = result.env.get(result.root_variable)
        lines.append(
            f"    {self.struct_name} *{result.root_variable} = {self._pointer_literal(root_value, addresses)};"
        )
        lines.append("")
        lines.append(f"    {result.target_function}({result.root_variable});")

        if addresses:
            lines.append("")
            for address in reversed(addresses):
                lines.append(f"    free(n{address});")

        lines.extend(
            [
                "",
                "    return 0;",
                "}",
                "",
            ]
        )
        return "\n".join(lines)

    @staticmethod
    def _pointer_literal(value: PointerValue, addresses: list[int]) -> str:
        if value is None:
            return "NULL"
        if value not in addresses:
            return "NULL"
        return f"n{value}"
