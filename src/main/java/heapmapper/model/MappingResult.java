package heapmapper.model;

import java.util.*;

public final class MappingResult {

    private final Map<Integer, Map<String, Integer>> memory;
    private final Map<String, Integer> env;
    private final Map<FieldKey, Integer> values;
    private final Map<Integer, String> nodeStates;
    private final Set<Integer> inputAddresses;
    private final List<String> constraints;
    private final String targetFunction;
    private final String rootVariable;
    private final StructureModel structure;
    private final ResultStatus status;
    private final ViolationKind violationKind;
    private final String diagnostic;
    private final Integer terminalOperationIndex;

    public MappingResult(
            Map<Integer, Map<String, Integer>> memory,
            Map<String, Integer> env,
            Map<FieldKey, Integer> values,
            Map<Integer, String> nodeStates,
            Set<Integer> inputAddresses,
            List<String> constraints,
            String targetFunction,
            String rootVariable,
            StructureModel structure,
            ResultStatus status,
            ViolationKind violationKind,
            String diagnostic,
            Integer terminalOperationIndex) {
        this.memory = Collections.unmodifiableMap(new HashMap<>(memory));
        this.env = Collections.unmodifiableMap(new LinkedHashMap<>(env));
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        this.nodeStates = Collections.unmodifiableMap(new HashMap<>(nodeStates));
        this.inputAddresses = Collections.unmodifiableSet(new HashSet<>(inputAddresses));
        this.constraints = Collections.unmodifiableList(new ArrayList<>(constraints));
        this.targetFunction = targetFunction;
        this.rootVariable = rootVariable;
        this.structure = structure;
        this.status = Objects.requireNonNull(status, "status");
        this.violationKind = violationKind;
        this.diagnostic = diagnostic == null ? "" : diagnostic;
        this.terminalOperationIndex = terminalOperationIndex;
        if (status == ResultStatus.VIOLATION && violationKind == null) {
            throw new IllegalArgumentException("VIOLATION result requires a violation kind");
        }
    }

    public Map<Integer, Map<String, Integer>> memory()   { return memory; }
    public Map<String, Integer>               env()      { return env; }
    public Map<FieldKey, Integer>             values()   { return values; }
    public Map<Integer, String>               nodeStates() { return nodeStates; }
    public Set<Integer>                       inputAddresses() { return inputAddresses; }
    public List<String>                       constraints()    { return constraints; }
    public String                             targetFunction() { return targetFunction; }
    public String                             rootVariable()   { return rootVariable; }
    public StructureModel                     structure()      { return structure; }
    public ResultStatus                       status()         { return status; }
    public ViolationKind                      violationKind()  { return violationKind; }
    public String                             diagnostic()     { return diagnostic; }
    public Integer                            terminalOperationIndex() { return terminalOperationIndex; }

    public Map<Integer, Map<String, Integer>> pointerLinks() {
        var result = new TreeMap<Integer, Map<String, Integer>>();
        for (int addr : inputAddresses) {
            result.put(addr, new HashMap<>(memory.getOrDefault(addr, Map.of())));
        }
        return result;
    }

    public Map<Integer, Map<String, Integer>> numericValues() {
        var grouped = new TreeMap<Integer, Map<String, Integer>>();
        for (var entry : values.entrySet()) {
            int addr = entry.getKey().address();
            if (inputAddresses.contains(addr)) {
                grouped.computeIfAbsent(addr, k -> new HashMap<>())
                       .put(entry.getKey().field(), entry.getValue());
            }
        }
        return grouped;
    }
}
