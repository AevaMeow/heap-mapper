package heapmapper.mapper;

import heapmapper.model.*;

import java.util.*;
import java.util.regex.Pattern;

public final class AddressMapper {

    private static final Pattern NAME_RE =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern FIELD_RE =
            Pattern.compile("^(?<base>[A-Za-z_][A-Za-z0-9_]*)(?:\\.|->)(?<field>[A-Za-z_][A-Za-z0-9_]*)$");
    private static final Pattern INT_RE =
            Pattern.compile("^[+-]?\\d+$");
    private static final Pattern CONSTRAINT_RE =
            Pattern.compile("^(?<left>.+?)\\s*(?<op>==|!=)\\s*(?<right>.+?)$");

    private final String rootVariable;
    private final String targetFunction;
    private final StructureModel structure;

    private final Map<String, PointerValue> env = new LinkedHashMap<>();
    private final Map<Integer, Map<String, PointerValue>> memory = new HashMap<>();
    private final Map<FieldKey, Integer> values = new LinkedHashMap<>();
    private final Map<Integer, String> nodeStates = new HashMap<>();
    private final Set<Integer> inputAddresses = new HashSet<>();
    private final List<String> constraints = new ArrayList<>();

    private final List<String[]> aliases = new ArrayList<>();
    private final Set<Set<String>> inequalities = new HashSet<>();

    private int nextAddress = 1;
    private ResultStatus resultStatus = ResultStatus.SAFE;
    private ViolationKind violationKind;
    private String diagnostic = "";
    private Integer terminalOperationIndex;

    public AddressMapper() {
        this("p", "target_function", StructureModel.defaultNode());
    }

    public AddressMapper(String rootVariable, String targetFunction) {
        this(rootVariable, targetFunction, StructureModel.defaultNode());
    }

    public AddressMapper(
            String rootVariable,
            String targetFunction,
            StructureModel structure) {
        this.rootVariable = rootVariable;
        this.targetFunction = targetFunction;
        this.structure = Objects.requireNonNull(structure, "structure");
    }

    public MappingResult processPath(List<PathOperation> operations) {
        for (int index = 0; index < operations.size(); index++) {
            try {
                evalOperation(operations.get(index));
            } catch (PathViolationException violation) {
                resultStatus = ResultStatus.VIOLATION;
                violationKind = violation.kind();
                diagnostic = violation.getMessage();
                terminalOperationIndex = index;
                break;
            }
        }
        return buildResult();
    }

    private void evalOperation(PathOperation op) {
        switch (op) {
            case PathOperation.Constraint(var expr) -> applyConstraint(expr);
            case PathOperation.Assign(var lhs, var rhs) -> assign(lhs, rhs);
            case PathOperation.Malloc(var v) -> {
                int addr = newAddress(false);
                setVariable(v, new PointerValue.KnownPtr(addr));
            }
            case PathOperation.Free(var v) -> free(v);
            case PathOperation.Deref(var expr) -> dereference(expr);
        }
    }

    private void applyConstraint(String expr) {
        String normalized = normalizeExpr(expr);
        var m = CONSTRAINT_RE.matcher(normalized);
        if (!m.matches()) {
            throw new IllegalArgumentException("unsupported constraint expression: " + expr);
        }
        Term left  = parseTerm(m.group("left"));
        String op  = m.group("op");
        Term right = parseTerm(m.group("right"));
        constraints.add(normalized);

        if (isNumericConstraint(left, right)) {
            applyNumericConstraint(left, op, right);
            return;
        }
        if ("==".equals(op)) {
            applyPointerEquality(left, right);
        } else {
            applyPointerInequality(left, right);
        }
    }

    private void assign(String lhsExpr, String rhsExpr) {
        Term lhs = parseTerm(normalizeExpr(lhsExpr));
        Term rhs = parseTerm(normalizeExpr(rhsExpr));
        switch (lhs) {
            case Term.VarTerm(var name) -> {
                PointerValue value = readPointer(rhs, true);
                if (value instanceof PointerValue.UnknownPtr) {
                    value = new PointerValue.KnownPtr(newAddress(true));
                    writePointer(rhs, value);
                }
                setVariable(name, value);
            }
            case Term.FieldTerm(var base, var field) -> writePointerField(base, field, readPointer(rhs, false));
            default -> throw new IllegalArgumentException(
                    "left side of assignment must be variable or pointer field: " + lhsExpr);
        }
    }

    private void free(String variable) {
        PointerValue val = getVariableValue(variable);
        if (val instanceof PointerValue.NullPtr) return;
        if (val instanceof PointerValue.UnknownPtr) {
            int addr = newAddress(true);
            setVariable(variable, new PointerValue.KnownPtr(addr));
            nodeStates.put(addr, "freed");
            return;
        }
        int addr = ((PointerValue.KnownPtr) val).address();
        if ("freed".equals(nodeStates.get(addr))) {
            throw PathViolationException.doubleFree(variable);
        }
        nodeStates.put(addr, "freed");
    }

    private void dereference(String expr) {
        Term term = parseTerm(normalizeExpr(expr));
        if (!(term instanceof Term.FieldTerm(var base, var field))) {
            throw new IllegalArgumentException(
                    "deref expression must be a field access, for example x.key: " + expr);
        }
        structure.requireField(field);
        int addr = ensureVariableNonNull(base);
        ensureNotFreed(addr, base);
    }

    private void applyNumericConstraint(Term left, String op, Term right) {
        if (!"==".equals(op)) {
            throw new IllegalArgumentException("only numeric equality constraints are supported");
        }
        Term.FieldTerm ft = (left instanceof Term.FieldTerm f) ? f : (Term.FieldTerm) right;
        Term.IntTerm   it = (left instanceof Term.IntTerm   i) ? i : (Term.IntTerm)   right;
        structure.requireIntegerField(ft.field());
        int addr = ensureVariableNonNull(ft.base());
        ensureNotFreed(addr, ft.base());
        var key  = new FieldKey(addr, ft.field());
        if (values.containsKey(key) && !values.get(key).equals(it.value())) {
            throw new InfeasiblePathException(
                    "conflicting numeric constraint for " + ft.base() + "." + ft.field());
        }
        values.put(key, it.value());
    }

    private void applyPointerEquality(Term left, Term right) {
        if (left instanceof Term.NullTerm())  { setPointerNull(right); return; }
        if (right instanceof Term.NullTerm()) { setPointerNull(left);  return; }

        if (left instanceof Term.VarTerm(var ln) && right instanceof Term.VarTerm(var rn)) {
            recordAlias(ln, rn);
            return;
        }

        PointerValue lv = readPointer(left,  false);
        PointerValue rv = readPointer(right, false);

        if (lv instanceof PointerValue.UnknownPtr && rv instanceof PointerValue.UnknownPtr) {
            var shared = new PointerValue.KnownPtr(newAddress(true));
            writePointer(left,  shared);
            writePointer(right, shared);
        } else if (lv instanceof PointerValue.UnknownPtr) {
            writePointer(left, rv);
        } else if (rv instanceof PointerValue.UnknownPtr) {
            writePointer(right, lv);
        } else if (!lv.equals(rv)) {
            throw InfeasiblePathException.pointerEqualityConflict();
        }
    }

    private void applyPointerInequality(Term left, Term right) {
        if (left instanceof Term.NullTerm())  { ensurePointerNonNull(right); return; }
        if (right instanceof Term.NullTerm()) { ensurePointerNonNull(left);  return; }

        if (left instanceof Term.VarTerm(var ln) && right instanceof Term.VarTerm(var rn)) {
            recordInequality(ln, rn);
            return;
        }

        PointerValue lv = readPointer(left,  false);
        PointerValue rv = readPointer(right, false);

        if (lv instanceof PointerValue.UnknownPtr) {
            if (rv instanceof PointerValue.UnknownPtr || rv instanceof PointerValue.NullPtr) {
                writePointer(left, new PointerValue.KnownPtr(newAddress(true)));
            } else {
                writePointer(left, new PointerValue.KnownPtr(
                        newAddressExcept(((PointerValue.KnownPtr) rv).address())));
            }
        } else if (rv instanceof PointerValue.UnknownPtr) {
            if (lv instanceof PointerValue.NullPtr) {
                writePointer(right, new PointerValue.KnownPtr(newAddress(true)));
            } else {
                writePointer(right, new PointerValue.KnownPtr(
                        newAddressExcept(((PointerValue.KnownPtr) lv).address())));
            }
        } else if (lv.equals(rv)) {
            throw InfeasiblePathException.pointerInequalityConflict();
        }
    }

    private void recordAlias(String left, String right) {
        if (left.equals(right)) return;
        if (inequalities.contains(Set.of(left, right))) {
            throw InfeasiblePathException.pointerEqualityConflict();
        }
        Set<String> combined = new HashSet<>(aliasGroup(left));
        combined.addAll(aliasGroup(right));

        Set<PointerValue> known = knownValuesFor(combined);
        if (known.size() > 1) throw InfeasiblePathException.pointerEqualityConflict();

        aliases.add(new String[]{left, right});
        if (!known.isEmpty()) setAliasGroupValue(combined, known.iterator().next());
        checkInequalities();
    }

    private void recordInequality(String left, String right) {
        if (left.equals(right) || !Collections.disjoint(aliasGroup(left), aliasGroup(right))) {
            throw InfeasiblePathException.pointerInequalityConflict();
        }
        inequalities.add(new HashSet<>(Set.of(left, right)));
        checkInequality(left, right);
    }

    private void setPointerNull(Term term) {
        writePointer(term, new PointerValue.NullPtr());
    }

    private PointerValue ensurePointerNonNull(Term term) {
        PointerValue cur = readPointer(term, false);
        if (cur instanceof PointerValue.NullPtr) {
            throw InfeasiblePathException.nullabilityConflict(formatTerm(term));
        }
        if (cur instanceof PointerValue.UnknownPtr) {
            var addr = new PointerValue.KnownPtr(newAddress(true));
            writePointer(term, addr);
            return addr;
        }
        return cur;
    }

    private PointerValue readPointer(Term term, boolean createMissingField) {
        return switch (term) {
            case Term.NullTerm()           -> new PointerValue.NullPtr();
            case Term.VarTerm(var name)    -> getVariableValue(name);
            case Term.FieldTerm(var base, var field) -> {
                structure.requirePointerField(field);
                int baseAddr = ensureVariableNonNull(base);
                ensureNotFreed(baseAddr, base);
                var fields = memory.computeIfAbsent(baseAddr, k -> new HashMap<>());
                if (!fields.containsKey(field)) {
                    if (createMissingField) {
                        var addr = new PointerValue.KnownPtr(newAddress(true));
                        fields.put(field, addr);
                        yield addr;
                    }
                    yield new PointerValue.UnknownPtr();
                }
                yield fields.get(field);
            }
            case Term.IntTerm(var v) ->
                throw new IllegalArgumentException("integer term cannot be used as a pointer");
        };
    }

    private void writePointer(Term term, PointerValue value) {
        switch (term) {
            case Term.VarTerm(var name)              -> setVariable(name, value);
            case Term.FieldTerm(var base, var field) -> writePointerField(base, field, value);
            default -> throw new IllegalArgumentException(
                    "cannot assign pointer value to " + formatTerm(term));
        }
    }

    private void writePointerField(String base, String field, PointerValue value) {
        structure.requirePointerField(field);
        PointerValue resolved = (value instanceof PointerValue.UnknownPtr)
                ? new PointerValue.KnownPtr(newAddress(true)) : value;
        int baseAddr = ensureVariableNonNull(base);
        ensureNotFreed(baseAddr, base);
        var fields   = memory.computeIfAbsent(baseAddr, k -> new HashMap<>());
        PointerValue existing = fields.get(field);
        if (existing != null
                && !(existing instanceof PointerValue.UnknownPtr)
                && !existing.equals(resolved)) {
            throw new InfeasiblePathException(base + "." + field + " has conflicting pointer values");
        }
        fields.put(field, resolved);
    }

    private void setVariable(String variable, PointerValue value) {
        PointerValue resolved = (value instanceof PointerValue.UnknownPtr)
                ? new PointerValue.KnownPtr(newAddress(true)) : value;
        Set<String>   group  = aliasGroup(variable);
        Set<PointerValue> known = knownValuesFor(group);
        if (!known.isEmpty() && known.stream().anyMatch(v -> !v.equals(resolved))) {
            throw InfeasiblePathException.pointerEqualityConflict();
        }
        setAliasGroupValue(group, resolved);
        checkInequalities();
    }

    private void setAliasGroupValue(Set<String> group, PointerValue value) {
        for (String name : group) env.put(name, value);
    }

    private PointerValue getVariableValue(String variable) {
        Set<String>   group = aliasGroup(variable);
        Set<PointerValue> known = knownValuesFor(group);
        if (known.size() > 1) throw InfeasiblePathException.pointerEqualityConflict();
        return known.isEmpty() ? new PointerValue.UnknownPtr() : known.iterator().next();
    }

    private Set<PointerValue> knownValuesFor(Set<String> group) {
        Set<PointerValue> known = new HashSet<>();
        for (String name : group) {
            if (env.containsKey(name)) known.add(env.get(name));
        }
        return known;
    }

    private int ensureVariableNonNull(String variable) {
        PointerValue val = getVariableValue(variable);
        if (val instanceof PointerValue.NullPtr) {
            throw PathViolationException.nullDereference(variable);
        }
        if (val instanceof PointerValue.UnknownPtr) {
            int addr = newAddress(true);
            setVariable(variable, new PointerValue.KnownPtr(addr));
            return addr;
        }
        return ((PointerValue.KnownPtr) val).address();
    }

    private void ensureNotFreed(int address, String variable) {
        if ("freed".equals(nodeStates.get(address))) {
            throw PathViolationException.useAfterFree(variable);
        }
    }

    private int newAddress(boolean inputNode) {
        int addr = nextAddress++;
        memory.put(addr, new HashMap<>());
        nodeStates.put(addr, "allocated");
        if (inputNode) inputAddresses.add(addr);
        return addr;
    }

    private int newAddressExcept(int forbidden) {
        int addr = newAddress(true);
        if (addr == forbidden) addr = newAddress(true);
        return addr;
    }

    private Set<String> aliasGroup(String variable) {
        Set<String> group = new HashSet<>();
        group.add(variable);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String[] pair : aliases) {
                if (group.contains(pair[0]) && group.add(pair[1])) changed = true;
                if (group.contains(pair[1]) && group.add(pair[0])) changed = true;
            }
        }
        return group;
    }

    private void checkInequalities() {
        for (Set<String> pair : inequalities) {
            var it = pair.iterator();
            checkInequality(it.next(), it.next());
        }
    }

    private void checkInequality(String left, String right) {
        if (!Collections.disjoint(aliasGroup(left), aliasGroup(right))) {
            throw InfeasiblePathException.pointerInequalityConflict();
        }
        PointerValue lv = getVariableValue(left);
        PointerValue rv = getVariableValue(right);
        if (!(lv instanceof PointerValue.UnknownPtr)
                && !(rv instanceof PointerValue.UnknownPtr)
                && lv.equals(rv)) {
            throw InfeasiblePathException.pointerInequalityConflict();
        }
    }

    private void materializeInequalityVariables() {
        Set<String> variables = new TreeSet<>();
        for (Set<String> pair : inequalities) variables.addAll(pair);

        for (String variable : variables) {
            if (getVariableValue(variable) instanceof PointerValue.UnknownPtr) {
                setVariable(variable, new PointerValue.KnownPtr(newAddress(true)));
            }
        }
        checkInequalities();
    }

    private static boolean isNumericConstraint(Term left, Term right) {
        return (left instanceof Term.FieldTerm && right instanceof Term.IntTerm)
                || (left instanceof Term.IntTerm && right instanceof Term.FieldTerm);
    }

    private static String normalizeExpr(String expr) {
        return expr.replace("->", ".").trim().replaceAll("\\s+", " ");
    }

    private static Term parseTerm(String expr) {
        expr = expr.strip();
        if ("NULL".equals(expr))           return new Term.NullTerm();
        if (INT_RE.matcher(expr).matches()) return new Term.IntTerm(Integer.parseInt(expr));
        var fm = FIELD_RE.matcher(expr);
        if (fm.matches()) return new Term.FieldTerm(fm.group("base"), fm.group("field"));
        if (NAME_RE.matcher(expr).matches()) return new Term.VarTerm(expr);
        throw new IllegalArgumentException("unsupported expression term: " + expr);
    }

    private static String formatTerm(Term term) {
        return switch (term) {
            case Term.NullTerm()           -> "NULL";
            case Term.IntTerm(var v)       -> String.valueOf(v);
            case Term.VarTerm(var n)       -> n;
            case Term.FieldTerm(var b, var f) -> b + "." + f;
        };
    }

    private MappingResult buildResult() {
        materializeInequalityVariables();

        Map<Integer, Map<String, Integer>> resultMemory = new HashMap<>();
        for (var e : memory.entrySet()) {
            Map<String, Integer> fields = new HashMap<>();
            for (var fe : e.getValue().entrySet()) fields.put(fe.getKey(), toNullable(fe.getValue()));
            resultMemory.put(e.getKey(), fields);
        }

        Map<String, Integer> resultEnv = new LinkedHashMap<>();
        new TreeMap<>(env).forEach((k, v) -> resultEnv.put(k, toNullable(v)));

        return new MappingResult(
                resultMemory, resultEnv,
                new LinkedHashMap<>(values),
                new HashMap<>(nodeStates),
                new HashSet<>(inputAddresses),
                new ArrayList<>(constraints),
                targetFunction, rootVariable, structure,
                resultStatus, violationKind, diagnostic, terminalOperationIndex);
    }

    private static Integer toNullable(PointerValue pv) {
        return switch (pv) {
            case PointerValue.NullPtr()      -> null;
            case PointerValue.KnownPtr(var a) -> a;
            case PointerValue.UnknownPtr()   -> null;
        };
    }
}
