from __future__ import annotations

from dataclasses import dataclass
import re

from heap_mapper.model import InfeasiblePathError, MappingResult, PathOperation, PointerValue

_NAME = r"[A-Za-z_][A-Za-z0-9_]*"
_FIELD_RE = re.compile(rf"^(?P<base>{_NAME})(?:\.|->)(?P<field>{_NAME})$")
_INT_RE = re.compile(r"^[+-]?\d+$")
_CONSTRAINT_RE = re.compile(r"^(?P<left>.+?)\s*(?P<op>==|!=)\s*(?P<right>.+?)$")


@dataclass(frozen=True)
class _UnknownPointer:
    pass


UNKNOWN = _UnknownPointer()


@dataclass(frozen=True)
class _Term:
    kind: str
    name: str | None = None
    base: str | None = None
    field: str | None = None
    value: int | None = None

    def name_or_raise(self) -> str:
        if self.name is None:
            raise ValueError("term has no variable name")
        return self.name

    def base_or_raise(self) -> str:
        if self.base is None:
            raise ValueError("term has no base variable")
        return self.base

    def field_or_raise(self) -> str:
        if self.field is None:
            raise ValueError("term has no field name")
        return self.field

    def value_or_raise(self) -> int:
        if self.value is None:
            raise ValueError("term has no integer value")
        return self.value


class AddressMapper:
    """Build a pseudo-address model for a selected execution path."""

    def __init__(self, *, root_variable: str = "p", target_function: str = "target_function") -> None:
        self.root_variable = root_variable
        self.target_function = target_function
        self.memory: dict[int, dict[str, PointerValue]] = {}
        self.env: dict[str, PointerValue] = {}
        self.values: dict[tuple[int, str], int] = {}
        self.node_states: dict[int, str] = {}
        self.input_addresses: set[int] = set()
        self.constraints: list[str] = []
        self._next_address = 1
        self._aliases: list[tuple[str, str]] = []
        self._inequalities: set[frozenset[str]] = set()

    def process_path(self, operations: list[PathOperation]) -> MappingResult:
        for operation in operations:
            self.eval_operation(operation)

        return MappingResult(
            memory={address: dict(fields) for address, fields in self.memory.items()},
            env=dict(sorted(self.env.items())),
            values=dict(self.values),
            node_states=dict(self.node_states),
            input_addresses=set(self.input_addresses),
            constraints=list(self.constraints),
            target_function=self.target_function,
            root_variable=self.root_variable,
        )

    def eval_operation(self, operation: PathOperation) -> None:
        match operation.op:
            case "constraint":
                if operation.expr is None:
                    raise ValueError("constraint operation requires expr")
                self.apply_constraint(operation.expr)
            case "assign":
                if operation.lhs is None or operation.rhs is None:
                    raise ValueError("assign operation requires lhs and rhs")
                self.assign(operation.lhs, operation.rhs)
            case "malloc":
                if operation.var is None:
                    raise ValueError("malloc operation requires var")
                self._set_variable(operation.var, self._new_address(input_node=False))
            case "free":
                if operation.var is None:
                    raise ValueError("free operation requires var")
                self.free(operation.var)
            case _:
                raise ValueError(f"unsupported operation '{operation.op}'")

    def apply_constraint(self, expr: str) -> None:
        normalized = _normalize_expr(expr)
        match = _CONSTRAINT_RE.match(normalized)
        if match is None:
            raise ValueError(f"unsupported constraint expression: {expr!r}")

        left = _parse_term(match.group("left"))
        operator = match.group("op")
        right = _parse_term(match.group("right"))
        self.constraints.append(normalized)

        if self._is_numeric_constraint(left, right):
            self._apply_numeric_constraint(left, operator, right)
            return

        if operator == "==":
            self._apply_pointer_equality(left, right)
        elif operator == "!=":
            self._apply_pointer_inequality(left, right)
        else:
            raise ValueError(f"unsupported constraint operator: {operator}")

    def assign(self, lhs_expr: str, rhs_expr: str) -> None:
        lhs = _parse_term(_normalize_expr(lhs_expr))
        rhs = _parse_term(_normalize_expr(rhs_expr))

        if lhs.kind == "var":
            value = self._read_pointer(rhs, create_missing_field=True)
            self._set_variable(lhs.name_or_raise(), value)
            return

        if lhs.kind == "field":
            value = self._read_pointer(rhs, create_missing_field=False)
            self._write_pointer_field(lhs.base_or_raise(), lhs.field_or_raise(), value)
            return

        raise ValueError(f"left side of assignment must be variable or pointer field: {lhs_expr!r}")

    def free(self, variable: str) -> None:
        value = self._get_variable_value(variable)
        if value is UNKNOWN:
            raise InfeasiblePathError(f"free of unknown pointer {variable}")
        if value is None:
            raise InfeasiblePathError(f"free of NULL pointer {variable}")
        if self.node_states.get(value) == "freed":
            raise InfeasiblePathError(f"double free of pointer {variable}")
        self.node_states[value] = "freed"

    def _apply_numeric_constraint(self, left: _Term, operator: str, right: _Term) -> None:
        if operator != "==":
            raise ValueError("only numeric equality constraints are supported")

        field_term = left if left.kind == "field" else right
        value_term = right if left.kind == "field" else left
        address = self._ensure_variable_non_null(field_term.base_or_raise())
        key = (address, field_term.field_or_raise())
        if key in self.values and self.values[key] != value_term.value:
            raise InfeasiblePathError(
                f"conflicting numeric constraint for {field_term.base}.{field_term.field}"
            )
        self.values[key] = value_term.value_or_raise()

    def _apply_pointer_equality(self, left: _Term, right: _Term) -> None:
        if left.kind == "null":
            self._set_pointer_null(right)
            return
        if right.kind == "null":
            self._set_pointer_null(left)
            return

        if left.kind == "var" and right.kind == "var":
            self._record_alias(left.name_or_raise(), right.name_or_raise())
            return

        left_value = self._read_pointer(left, create_missing_field=False)
        right_value = self._read_pointer(right, create_missing_field=False)

        if left_value is UNKNOWN and right_value is UNKNOWN:
            address = self._new_address(input_node=True)
            self._write_pointer(left, address)
            self._write_pointer(right, address)
            return
        if left_value is UNKNOWN:
            self._write_pointer(left, right_value)
            return
        if right_value is UNKNOWN:
            self._write_pointer(right, left_value)
            return
        if left_value != right_value:
            raise InfeasiblePathError("conflicting pointer equality constraints")

    def _apply_pointer_inequality(self, left: _Term, right: _Term) -> None:
        if left.kind == "null":
            self._ensure_pointer_non_null(right)
            return
        if right.kind == "null":
            self._ensure_pointer_non_null(left)
            return

        if left.kind == "var" and right.kind == "var":
            self._record_inequality(left.name_or_raise(), right.name_or_raise())
            return

        left_value = self._read_pointer(left, create_missing_field=False)
        right_value = self._read_pointer(right, create_missing_field=False)

        if left_value is UNKNOWN:
            if right_value is UNKNOWN or right_value is None:
                self._write_pointer(left, self._new_address(input_node=True))
            else:
                self._write_pointer(left, self._new_address_except(right_value))
            return
        if right_value is UNKNOWN:
            if left_value is None:
                self._write_pointer(right, self._new_address(input_node=True))
            else:
                self._write_pointer(right, self._new_address_except(left_value))
            return
        if left_value == right_value:
            raise InfeasiblePathError("conflicting pointer inequality constraints")

    def _record_alias(self, left: str, right: str) -> None:
        if left == right:
            return
        if frozenset((left, right)) in self._inequalities:
            raise InfeasiblePathError("conflicting pointer equality constraints")

        left_group = self._alias_group(left)
        right_group = self._alias_group(right)
        combined = left_group | right_group
        known_values = {self.env[name] for name in combined if name in self.env}
        if len(known_values) > 1:
            raise InfeasiblePathError("conflicting pointer equality constraints")
        self._aliases.append((left, right))
        if known_values:
            self._set_alias_group_value(combined, known_values.pop())
        self._check_inequalities()

    def _record_inequality(self, left: str, right: str) -> None:
        if left == right or self._alias_group(left) & self._alias_group(right):
            raise InfeasiblePathError("conflicting pointer inequality constraints")
        self._inequalities.add(frozenset((left, right)))
        left_value = self._get_variable_value(left)
        right_value = self._get_variable_value(right)

        if left_value is UNKNOWN and right_value is UNKNOWN:
            self._set_variable(left, self._new_address(input_node=True))
            self._set_variable(right, None)
            return
        if left_value is UNKNOWN:
            value = self._new_address(input_node=True) if right_value is None else None
            self._set_variable(left, value)
            return
        if right_value is UNKNOWN:
            value = self._new_address(input_node=True) if left_value is None else None
            self._set_variable(right, value)
            return

        self._check_inequality(left, right)

    def _set_pointer_null(self, term: _Term) -> None:
        self._write_pointer(term, None)

    def _ensure_pointer_non_null(self, term: _Term) -> PointerValue:
        current = self._read_pointer(term, create_missing_field=False)
        if current is None:
            raise InfeasiblePathError(f"{_format_term(term)} is both NULL and non-NULL")
        if current is UNKNOWN:
            current = self._new_address(input_node=True)
            self._write_pointer(term, current)
        return current

    def _read_pointer(self, term: _Term, *, create_missing_field: bool) -> PointerValue | _UnknownPointer:
        match term.kind:
            case "null":
                return None
            case "var":
                return self._get_variable_value(term.name_or_raise())
            case "field":
                base_address = self._ensure_variable_non_null(term.base_or_raise())
                self._ensure_not_freed(base_address, term.base_or_raise())
                fields = self.memory.setdefault(base_address, {})
                field_name = term.field_or_raise()
                if field_name not in fields:
                    if create_missing_field:
                        fields[field_name] = self._new_address(input_node=True)
                    else:
                        return UNKNOWN
                return fields[field_name]
            case "int":
                raise ValueError("integer term cannot be used as a pointer")
            case _:
                raise ValueError(f"unsupported term kind: {term.kind}")

    def _write_pointer(self, term: _Term, value: PointerValue) -> None:
        match term.kind:
            case "var":
                self._set_variable(term.name_or_raise(), value)
            case "field":
                self._write_pointer_field(term.base_or_raise(), term.field_or_raise(), value)
            case _:
                raise ValueError(f"cannot assign pointer value to {_format_term(term)}")

    def _write_pointer_field(self, base: str, field_name: str, value: PointerValue | _UnknownPointer) -> None:
        if value is UNKNOWN:
            value = self._new_address(input_node=True)
        base_address = self._ensure_variable_non_null(base)
        self._ensure_not_freed(base_address, base)
        existing = self.memory.setdefault(base_address, {}).get(field_name, UNKNOWN)
        if existing is not UNKNOWN and existing != value:
            raise InfeasiblePathError(f"{base}.{field_name} has conflicting pointer values")
        self.memory[base_address][field_name] = value

    def _set_variable(self, variable: str, value: PointerValue | _UnknownPointer) -> None:
        if value is UNKNOWN:
            value = self._new_address(input_node=True)
        group = self._alias_group(variable)
        known_values = {self.env[name] for name in group if name in self.env}
        if known_values and any(existing != value for existing in known_values):
            raise InfeasiblePathError("conflicting pointer equality constraints")
        self._set_alias_group_value(group, value)
        self._check_inequalities()

    def _set_alias_group_value(self, group: set[str], value: PointerValue) -> None:
        for name in group:
            self.env[name] = value

    def _get_variable_value(self, variable: str) -> PointerValue | _UnknownPointer:
        group = self._alias_group(variable)
        known_values = {self.env[name] for name in group if name in self.env}
        if len(known_values) > 1:
            raise InfeasiblePathError("conflicting pointer equality constraints")
        if not known_values:
            return UNKNOWN
        return known_values.pop()

    def _ensure_variable_non_null(self, variable: str) -> int:
        value = self._get_variable_value(variable)
        if value is None:
            raise InfeasiblePathError(f"dereference of NULL pointer {variable}")
        if value is UNKNOWN:
            value = self._new_address(input_node=True)
            self._set_variable(variable, value)
        return value

    def _ensure_not_freed(self, address: int, variable: str) -> None:
        if self.node_states.get(address) == "freed":
            raise InfeasiblePathError(f"dereference after free of pointer {variable}")

    def _new_address(self, *, input_node: bool) -> int:
        address = self._next_address
        self._next_address += 1
        self.memory.setdefault(address, {})
        self.node_states[address] = "allocated"
        if input_node:
            self.input_addresses.add(address)
        return address

    def _new_address_except(self, forbidden: int) -> int:
        address = self._new_address(input_node=True)
        if address == forbidden:
            address = self._new_address(input_node=True)
        return address

    def _alias_group(self, variable: str) -> set[str]:
        group = {variable}
        changed = True
        while changed:
            changed = False
            for left, right in self._aliases:
                if left in group and right not in group:
                    group.add(right)
                    changed = True
                if right in group and left not in group:
                    group.add(left)
                    changed = True
        return group

    def _check_inequalities(self) -> None:
        for pair in self._inequalities:
            left, right = tuple(pair)
            self._check_inequality(left, right)

    def _check_inequality(self, left: str, right: str) -> None:
        if self._alias_group(left) & self._alias_group(right):
            raise InfeasiblePathError("conflicting pointer inequality constraints")
        left_value = self._get_variable_value(left)
        right_value = self._get_variable_value(right)
        if left_value is not UNKNOWN and right_value is not UNKNOWN and left_value == right_value:
            raise InfeasiblePathError("conflicting pointer inequality constraints")

    @staticmethod
    def _is_numeric_constraint(left: _Term, right: _Term) -> bool:
        return (left.kind == "field" and right.kind == "int") or (
            left.kind == "int" and right.kind == "field"
        )


def _normalize_expr(expr: str) -> str:
    return " ".join(expr.replace("->", ".").split())


def _parse_term(expr: str) -> _Term:
    expr = expr.strip()
    if expr == "NULL":
        return _Term(kind="null")
    if _INT_RE.match(expr):
        return _Term(kind="int", value=int(expr))
    field_match = _FIELD_RE.match(expr)
    if field_match:
        return _Term(
            kind="field",
            base=field_match.group("base"),
            field=field_match.group("field"),
        )
    if re.match(rf"^{_NAME}$", expr):
        return _Term(kind="var", name=expr)
    raise ValueError(f"unsupported expression term: {expr!r}")


def _format_term(term: _Term) -> str:
    match term.kind:
        case "null":
            return "NULL"
        case "int":
            return str(term.value)
        case "var":
            return term.name_or_raise()
        case "field":
            return f"{term.base_or_raise()}.{term.field_or_raise()}"
        case _:
            return term.kind
