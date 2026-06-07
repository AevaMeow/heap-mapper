package heapmapper.experiment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import heapmapper.codegen.CCodeGenerator;
import heapmapper.mapper.AddressMapper;
import heapmapper.model.FieldModel;
import heapmapper.model.InfeasiblePathException;
import heapmapper.model.MappingResult;
import heapmapper.model.PathDocument;
import heapmapper.model.PathOperation;
import heapmapper.model.ResultStatus;
import heapmapper.model.ViolationKind;
import heapmapper.parser.PathParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BenchmarkRunner {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern FIELD_ACCESS =
            Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_]*)\\.([A-Za-z_][A-Za-z0-9_]*)\\b");
    private static final Pattern NAME =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern INTEGER_LITERAL =
            Pattern.compile("(?<![A-Za-z0-9_])[+-]?\\d+");

    private BenchmarkRunner() {}

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        if (args.length == 0) {
            System.err.println(
                    "Usage: BenchmarkRunner <manifest.json> "
                            + "[--out <directory>] [--iterations <n>] "
                            + "[--baseline-attempts <n>] [--compiler <gcc>]");
            return 2;
        }

        Path manifest = Path.of(args[0]).toAbsolutePath().normalize();
        Path output = Path.of("reports/benchmark").toAbsolutePath().normalize();
        int iterations = 30;
        int baselineAttempts = 100;
        String compiler = "gcc";

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--out" -> {
                    if (i + 1 >= args.length) return missingValue("--out");
                    output = Path.of(args[++i]).toAbsolutePath().normalize();
                }
                case "--iterations" -> {
                    if (i + 1 >= args.length) return missingValue("--iterations");
                    iterations = Integer.parseInt(args[++i]);
                    if (iterations < 1) {
                        throw new IllegalArgumentException("iterations must be positive");
                    }
                }
                case "--compiler" -> {
                    if (i + 1 >= args.length) return missingValue("--compiler");
                    compiler = args[++i];
                }
                case "--baseline-attempts" -> {
                    if (i + 1 >= args.length) return missingValue("--baseline-attempts");
                    baselineAttempts = Integer.parseInt(args[++i]);
                    if (baselineAttempts < 0) {
                        throw new IllegalArgumentException(
                                "baseline attempts must be non-negative");
                    }
                }
                default -> throw new IllegalArgumentException("unknown option: " + args[i]);
            }
        }

        try {
            BenchmarkSuite suite = loadSuite(manifest);
            Files.createDirectories(output);
            Path generatedDir = output.resolve("generated");
            Files.createDirectories(generatedDir);

            var results = new ArrayList<CaseResult>();
            for (BenchmarkCase benchmarkCase : suite.cases()) {
                CaseResult result = executeCase(
                        benchmarkCase,
                        manifest.getParent(),
                        generatedDir,
                        iterations,
                        baselineAttempts,
                        compiler);
                results.add(result);
                System.out.printf(
                        Locale.ROOT,
                        "%-28s expected=%-10s actual=%-10s %s%n",
                        result.id(),
                        result.expected(),
                        result.actual(),
                        result.classificationCorrect() ? "PASS" : "FAIL");
            }

            Summary summary = summarize(suite.name(), results);
            Files.writeString(
                    output.resolve("results.csv"),
                    toCsv(results),
                    StandardCharsets.UTF_8);
            Files.writeString(
                    output.resolve("summary.md"),
                    toMarkdown(summary, results),
                    StandardCharsets.UTF_8);

            System.out.println();
            System.out.println("Report: " + output.resolve("summary.md"));
            System.out.printf(
                    Locale.ROOT,
                    "Manifest agreement %.3f, applicability %.3f, end-to-end coverage %.3f%n",
                    summary.accuracy(),
                    summary.applicability(),
                    summary.endToEndCoverage());
            return summary.incorrectCases() == 0 ? 0 : 1;
        } catch (IOException | RuntimeException error) {
            System.err.println("benchmark: " + error.getMessage());
            return 2;
        }
    }

    private static int missingValue(String option) {
        System.err.println("benchmark: missing value for " + option);
        return 2;
    }

    private static BenchmarkSuite loadSuite(Path manifest) throws IOException {
        JsonNode root = JSON.readTree(manifest.toFile());
        String name = requiredString(root, "name");
        var cases = new ArrayList<BenchmarkCase>();
        var ids = new HashSet<String>();
        loadCases(manifest, root, cases, ids);
        if (cases.isEmpty()) {
            throw new IllegalArgumentException("benchmark suite must contain at least one case");
        }
        return new BenchmarkSuite(name, List.copyOf(cases));
    }

    private static void loadCases(
            Path manifest,
            JsonNode root,
            List<BenchmarkCase> cases,
            Set<String> ids) throws IOException {
        JsonNode includesNode = root.get("includes");
        if (includesNode != null) {
            if (!includesNode.isArray()) {
                throw new IllegalArgumentException("'includes' must be an array");
            }
            for (JsonNode include : includesNode) {
                if (!include.isTextual() || include.asText().isBlank()) {
                    throw new IllegalArgumentException(
                            "manifest include must be a non-blank string");
                }
                Path includedManifest = manifest.getParent()
                        .resolve(include.asText())
                        .normalize();
                loadCases(
                        includedManifest,
                        JSON.readTree(includedManifest.toFile()),
                        cases,
                        ids);
            }
        }

        JsonNode casesNode = root.get("cases");
        if (casesNode == null) return;
        if (!casesNode.isArray()) {
            throw new IllegalArgumentException("'cases' must be an array");
        }
        for (JsonNode node : casesNode) {
            String id = requiredString(node, "id");
            if (!ids.add(id)) {
                throw new IllegalArgumentException("duplicate benchmark id: " + id);
            }
            String category = requiredString(node, "category");
            String inputValue = optionalString(node, "input");
            String input = inputValue == null
                    ? null
                    : manifest.getParent().resolve(inputValue).normalize().toString();
            ResultStatus expected = ResultStatus.valueOf(
                    requiredString(node, "expected").toUpperCase(Locale.ROOT));
            String expectedDiagnostic = optionalString(node, "expected_diagnostic");
            Integer expectedNodes = optionalInteger(node, "expected_nodes");
            String origin = optionalString(node, "origin", "internal");
            String groundTruth = optionalString(
                    node, "ground_truth", expected.name().toLowerCase(Locale.ROOT));
            String structureCategory = optionalString(
                    node, "structure_category", category);
            String defectType = optionalString(node, "defect_type", category);
            String sourceUrl = optionalString(node, "source_url", "");
            String sourceFile = optionalString(node, "source_file", "");
            String property = optionalString(node, "property", "");
            String unsupportedReason = optionalString(node, "unsupported_reason", "");
            if (input == null && expected != ResultStatus.UNSUPPORTED) {
                throw new IllegalArgumentException(
                        "benchmark case " + id + " requires 'input'");
            }
            cases.add(new BenchmarkCase(
                    id,
                    category,
                    origin,
                    groundTruth,
                    structureCategory,
                    defectType,
                    sourceUrl,
                    sourceFile,
                    property,
                    input,
                    expected,
                    expectedDiagnostic,
                    expectedNodes,
                    unsupportedReason));
        }
    }

    private static CaseResult executeCase(
            BenchmarkCase benchmarkCase,
            Path manifestDirectory,
            Path generatedDirectory,
            int iterations,
            int baselineAttempts,
            String compiler) throws IOException {
        if (benchmarkCase.input() == null) {
            return unsupportedCase(benchmarkCase);
        }
        Path input = manifestDirectory.resolve(benchmarkCase.input()).normalize();

        long parseStart = System.nanoTime();
        PathDocument document = PathParser.loadDocument(input);
        long parseNanos = System.nanoTime() - parseStart;

        var generationNanos = new long[iterations];
        ResultStatus actual = null;
        String diagnostic = "";
        MappingResult mapping = null;
        String generatedCode = null;

        for (int iteration = 0; iteration < iterations; iteration++) {
            long start = System.nanoTime();
            ResultStatus iterationOutcome;
            try {
                MappingResult currentMapping = new AddressMapper(
                        document.entryPointer(),
                        document.targetFunction(),
                        document.structure())
                        .processPath(document.operations());
                String currentCode = new CCodeGenerator().generate(currentMapping);
                iterationOutcome = currentMapping.status();
                if (iteration == 0) {
                    mapping = currentMapping;
                    generatedCode = currentCode;
                    diagnostic = currentMapping.diagnostic();
                }
            } catch (InfeasiblePathException error) {
                iterationOutcome = ResultStatus.UNSAT;
                if (iteration == 0) diagnostic = error.getMessage();
            } catch (IllegalArgumentException error) {
                iterationOutcome = ResultStatus.UNSUPPORTED;
                if (iteration == 0) diagnostic = error.getMessage();
            }
            generationNanos[iteration] = System.nanoTime() - start;
            if (actual == null) {
                actual = iterationOutcome;
            } else if (actual != iterationOutcome) {
                actual = ResultStatus.UNSUPPORTED;
                diagnostic = "non-deterministic classification across iterations";
                break;
            }
        }

        boolean classificationCorrect =
                benchmarkCase.expected() == actual;
        boolean diagnosticMatch =
                benchmarkCase.expectedDiagnostic() == null
                        || diagnostic.contains(benchmarkCase.expectedDiagnostic());
        Integer actualNodes = mapping == null ? null : mapping.inputAddresses().size();
        boolean nodeCountMatch =
                benchmarkCase.expectedNodes() == null
                        || benchmarkCase.expectedNodes().equals(actualNodes);

        Boolean compiled = null;
        Boolean runtimeSupported = null;
        Boolean targetReached = null;
        Boolean sanitizerClean = null;
        Boolean sanitizerMatched = null;
        Boolean baselineCompiled = null;
        Integer baselineTargetHits = null;
        Integer baselineSanitizerHits = null;
        String toolOutput = "";

        if ((actual == ResultStatus.SAFE || actual == ResultStatus.VIOLATION)
                && generatedCode != null) {
            String safeId = safeFileName(benchmarkCase.id());
            Path source = generatedDirectory.resolve(safeId + ".c");
            Path object = generatedDirectory.resolve(safeId + ".o");
            Files.writeString(source, generatedCode, StandardCharsets.UTF_8);

            ProcessResult compileResult = runProcess(
                    List.of(
                            compiler,
                            "-std=c11",
                            "-Wall",
                            "-Wextra",
                            "-Werror",
                            "-c",
                            source.toString(),
                            "-o",
                            object.toString()),
                    generatedDirectory,
                    20);
            compiled = compileResult.exitCode() == 0;
            toolOutput = compileResult.output();
            if (compileResult.exitCode() == 124) {
                actual = ResultStatus.TIMEOUT;
            } else if (!compiled) {
                actual = ResultStatus.COMPILE_ERROR;
            }

            OracleSource oracle = buildOracle(document, mapping);
            runtimeSupported = oracle.supported();
            if (compiled && oracle.supported()) {
                Path runtimeSource = generatedDirectory.resolve(safeId + "-runtime.c");
                Path executable = generatedDirectory.resolve(safeId + "-runtime");
                Files.writeString(
                        runtimeSource,
                        generatedCode + System.lineSeparator() + oracle.source(),
                        StandardCharsets.UTF_8);

                ProcessResult sanitizerCompile = runProcess(
                        List.of(
                                compiler,
                                "-std=c11",
                                "-O0",
                                "-g",
                                "-Wall",
                                "-Wextra",
                                "-Werror",
                                "-fno-omit-frame-pointer",
                                "-fsanitize=address,undefined,leak",
                                runtimeSource.toString(),
                                "-o",
                                executable.toString()),
                        generatedDirectory,
                        30);
                if (sanitizerCompile.exitCode() != 0) {
                    actual = sanitizerCompile.exitCode() == 124
                            ? ResultStatus.TIMEOUT
                            : ResultStatus.COMPILE_ERROR;
                    targetReached = false;
                    sanitizerClean = false;
                    sanitizerMatched = false;
                    toolOutput = sanitizerCompile.output();
                } else {
                    ProcessResult runtime = runProcess(
                            List.of(executable.toString()),
                            generatedDirectory,
                            10,
                            "ASAN_OPTIONS", "detect_leaks=1:halt_on_error=1",
                            "UBSAN_OPTIONS", "halt_on_error=1");
                    toolOutput = runtime.output();
                    if (runtime.exitCode() == 124) {
                        actual = ResultStatus.TIMEOUT;
                    }
                    targetReached = toolOutput.contains("HEAP_MAPPER_TARGET_REACHED");
                    boolean sanitizerReported =
                            toolOutput.contains("AddressSanitizer")
                                    || toolOutput.contains("LeakSanitizer")
                                    || toolOutput.contains("runtime error:");
                    sanitizerClean = !sanitizerReported;
                    sanitizerMatched = mapping.status() == ResultStatus.VIOLATION
                            ? matchesViolation(mapping.violationKind(), toolOutput)
                            : null;
                }

                if (baselineAttempts > 0
                        && actual != ResultStatus.TIMEOUT
                        && actual != ResultStatus.COMPILE_ERROR) {
                    BaselineResult baseline = runRandomBaseline(
                            benchmarkCase,
                            document,
                            mapping,
                            oracle,
                            generatedDirectory,
                            safeId,
                            compiler,
                            baselineAttempts);
                    baselineCompiled = baseline.compiled();
                    baselineTargetHits = baseline.targetHits();
                    baselineSanitizerHits = baseline.sanitizerHits();
                    if (!baseline.output().isBlank()) {
                        toolOutput = toolOutput + " baseline: " + baseline.output();
                    }
                }
            }
        }

        Arrays.sort(generationNanos);
        double generationMedianUs =
                generationNanos[generationNanos.length / 2] / 1_000.0;
        classificationCorrect = benchmarkCase.expected() == actual;
        boolean casePassed =
                classificationCorrect
                        && diagnosticMatch
                        && nodeCountMatch
                        && (compiled == null || compiled)
                        && (runtimeSupported == null
                                || !runtimeSupported
                                || runtimePasses(
                                        benchmarkCase.expected(),
                                        targetReached,
                                        sanitizerClean,
                                        sanitizerMatched));

        return new CaseResult(
                benchmarkCase.id(),
                benchmarkCase.category(),
                benchmarkCase.origin(),
                benchmarkCase.groundTruth(),
                benchmarkCase.structureCategory(),
                benchmarkCase.defectType(),
                benchmarkCase.sourceUrl(),
                benchmarkCase.sourceFile(),
                benchmarkCase.property(),
                benchmarkCase.expected(),
                actual,
                classificationCorrect,
                diagnosticMatch,
                diagnostic,
                benchmarkCase.expectedNodes(),
                actualNodes,
                nodeCountMatch,
                parseNanos / 1_000.0,
                generationMedianUs,
                compiled,
                runtimeSupported,
                targetReached,
                sanitizerClean,
                sanitizerMatched,
                baselineAttempts > 0 ? baselineAttempts : null,
                baselineCompiled,
                baselineTargetHits,
                baselineSanitizerHits,
                oneLine(toolOutput),
                casePassed);
    }

    private static CaseResult unsupportedCase(BenchmarkCase benchmarkCase) {
        String diagnostic = benchmarkCase.unsupportedReason().isBlank()
                ? "no normalized path is available"
                : benchmarkCase.unsupportedReason();
        boolean correct = benchmarkCase.expected() == ResultStatus.UNSUPPORTED;
        return new CaseResult(
                benchmarkCase.id(),
                benchmarkCase.category(),
                benchmarkCase.origin(),
                benchmarkCase.groundTruth(),
                benchmarkCase.structureCategory(),
                benchmarkCase.defectType(),
                benchmarkCase.sourceUrl(),
                benchmarkCase.sourceFile(),
                benchmarkCase.property(),
                benchmarkCase.expected(),
                ResultStatus.UNSUPPORTED,
                correct,
                true,
                diagnostic,
                benchmarkCase.expectedNodes(),
                null,
                benchmarkCase.expectedNodes() == null,
                0.0,
                0.0,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "",
                correct);
    }

    private static BaselineResult runRandomBaseline(
            BenchmarkCase benchmarkCase,
            PathDocument document,
            MappingResult mapping,
            OracleSource oracle,
            Path generatedDirectory,
            String safeId,
            String compiler,
            int attempts) throws IOException {
        int nodeLimit = benchmarkCase.expectedNodes() != null
                ? benchmarkCase.expectedNodes()
                : mapping.inputAddresses().size();
        String baselineSource = buildRandomBaselineSource(
                document, oracle.source(), nodeLimit);
        Path source = generatedDirectory.resolve(safeId + "-baseline.c");
        Path executable = generatedDirectory.resolve(safeId + "-baseline");
        Files.writeString(source, baselineSource, StandardCharsets.UTF_8);

        ProcessResult compile = runProcess(
                List.of(
                        compiler,
                        "-std=c11",
                        "-O0",
                        "-g",
                        "-Wall",
                        "-Wextra",
                        "-Werror",
                        "-fno-omit-frame-pointer",
                        "-fsanitize=address,undefined",
                        source.toString(),
                        "-o",
                        executable.toString()),
                generatedDirectory,
                30);
        if (compile.exitCode() != 0) {
            return new BaselineResult(false, 0, 0, oneLine(compile.output()));
        }

        int targetHits = 0;
        int sanitizerHits = 0;
        int parallelism = Math.min(24, attempts);
        try (ExecutorService executor = Executors.newFixedThreadPool(parallelism)) {
            var futures = new ArrayList<Future<BaselineAttempt>>();
            for (int attempt = 1; attempt <= attempts; attempt++) {
                int seed = attempt;
                futures.add(executor.submit(() -> {
                    ProcessResult runtime = runProcess(
                            List.of(executable.toString(), Integer.toString(seed)),
                            generatedDirectory,
                            5,
                            "ASAN_OPTIONS", "detect_leaks=0:halt_on_error=1",
                            "UBSAN_OPTIONS", "halt_on_error=1");
                    boolean targetReached =
                            runtime.output().contains("HEAP_MAPPER_TARGET_REACHED");
                    return new BaselineAttempt(
                            targetReached,
                            targetReached
                                    && mapping.status() == ResultStatus.VIOLATION
                                    && matchesViolation(
                                            mapping.violationKind(), runtime.output()));
                }));
            }
            for (Future<BaselineAttempt> future : futures) {
                try {
                    BaselineAttempt attempt = future.get();
                    if (attempt.targetReached()) targetHits++;
                    if (attempt.sanitizerMatched()) sanitizerHits++;
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    return new BaselineResult(
                            true, targetHits, sanitizerHits, "baseline interrupted");
                } catch (ExecutionException error) {
                    Throwable cause = error.getCause();
                    if (cause instanceof IOException ioError) throw ioError;
                    throw new IOException("baseline attempt failed", cause);
                }
            }
        }
        return new BaselineResult(true, targetHits, sanitizerHits, "");
    }

    private static String buildRandomBaselineSource(
            PathDocument document,
            String oracleSource,
            int nodeLimit) {
        var constants = new ArrayList<Integer>();
        constants.add(0);
        constants.add(1);
        constants.add(-1);
        for (PathOperation operation : document.operations()) {
            if (operation instanceof PathOperation.Constraint(var expression)) {
                Matcher matcher = INTEGER_LITERAL.matcher(expression);
                while (matcher.find()) {
                    int value = Integer.parseInt(matcher.group());
                    if (!constants.contains(value)) constants.add(value);
                }
            }
        }

        String structName = document.structure().name();
        var source = new StringBuilder();
        source.append("#include <stdint.h>\n")
                .append("#include <stdio.h>\n")
                .append("#include <stdlib.h>\n\n")
                .append("typedef struct ").append(structName).append(" {\n");
        for (FieldModel field : document.structure().fields()) {
            source.append("    ");
            if (field.kind() == FieldModel.Kind.INT) {
                source.append("int ").append(field.name());
            } else {
                source.append("struct ")
                        .append(field.target())
                        .append(" *")
                        .append(field.name());
            }
            source.append(";\n");
        }
        source.append("} ").append(structName).append(";\n\n")
                .append(oracleSource)
                .append("\nstatic uint32_t heap_mapper_random_state;\n\n")
                .append("static uint32_t heap_mapper_random(void) {\n")
                .append("    heap_mapper_random_state = ")
                .append("heap_mapper_random_state * UINT32_C(1664525) + ")
                .append("UINT32_C(1013904223);\n")
                .append("    return heap_mapper_random_state;\n")
                .append("}\n\n")
                .append("int main(int argc, char **argv) {\n")
                .append("    heap_mapper_random_state = argc > 1\n")
                .append("            ? (uint32_t)strtoul(argv[1], NULL, 10)\n")
                .append("            : UINT32_C(1);\n")
                .append("    static const int values[] = {");
        for (int i = 0; i < constants.size(); i++) {
            if (i > 0) source.append(", ");
            source.append(constants.get(i));
        }
        source.append("};\n")
                .append("    const size_t node_count = ")
                .append(nodeLimit)
                .append(";\n")
                .append("    ").append(structName).append(" **nodes = NULL;\n")
                .append("    if (node_count > 0) {\n")
                .append("        nodes = calloc(node_count, sizeof(*nodes));\n")
                .append("        if (nodes == NULL) return 2;\n")
                .append("        for (size_t i = 0; i < node_count; i++) {\n")
                .append("            nodes[i] = malloc(sizeof(*nodes[i]));\n")
                .append("            if (nodes[i] == NULL) return 2;\n")
                .append("        }\n")
                .append("        for (size_t i = 0; i < node_count; i++) {\n");
        for (FieldModel field : document.structure().integerFields()) {
            source.append("            nodes[i]->")
                    .append(field.name())
                    .append(" = values[heap_mapper_random() % ")
                    .append(constants.size())
                    .append("U];\n");
        }
        for (FieldModel field : document.structure().pointerFields()) {
            source.append("            {\n")
                    .append("                size_t choice = heap_mapper_random() % ")
                    .append("(node_count + 1U);\n")
                    .append("                nodes[i]->")
                    .append(field.name())
                    .append(" = choice == node_count ? NULL : nodes[choice];\n")
                    .append("            }\n");
        }
        source.append("        }\n")
                .append("    }\n")
                .append("    size_t root_choice = node_count == 0\n")
                .append("            ? 0 : heap_mapper_random() % (node_count + 1U);\n")
                .append("    ").append(structName).append(" *")
                .append(document.entryPointer())
                .append(" = node_count == 0 || root_choice == node_count\n")
                .append("            ? NULL : nodes[root_choice];\n")
                .append("    ").append(document.targetFunction())
                .append("(").append(document.entryPointer()).append(");\n")
                .append("    (void)values;\n")
                .append("    return 0;\n")
                .append("}\n");
        return source.toString();
    }

    private static OracleSource buildOracle(
            PathDocument document,
            MappingResult mapping) {
        Set<String> declared = new HashSet<>();
        declared.add(document.entryPointer());
        Set<String> localPointers = new HashSet<>();
        Set<String> allocated = new HashSet<>();
        Set<String> freed = new HashSet<>();
        var body = new StringBuilder();
        int sinkIndex = 0;

        for (int operationIndex = 0;
                operationIndex < document.operations().size();
                operationIndex++) {
            PathOperation operation = document.operations().get(operationIndex);
            if (mapping.status() == ResultStatus.VIOLATION
                    && mapping.terminalOperationIndex() != null
                    && mapping.terminalOperationIndex() == operationIndex) {
                appendTargetMarker(body);
            }
            switch (operation) {
                case PathOperation.Constraint(var expr) -> {
                    if (!allVariablesDeclared(expr, declared)) {
                        return OracleSource.unsupported(
                                "constraint refers to an independent pointer variable");
                    }
                    body.append("    if (!(")
                            .append(toCExpression(expr))
                            .append(")) abort();\n");
                }
                case PathOperation.Assign(var lhs, var rhs) -> {
                    if (!allVariablesDeclared(rhs, declared)) {
                        return OracleSource.unsupported(
                                "assignment reads an independent pointer variable");
                    }
                    if (isFieldAccess(lhs)) {
                        String base = baseVariable(lhs);
                        if (!declared.contains(base)) {
                            return OracleSource.unsupported(
                                    "assignment writes through an undeclared pointer");
                        }
                        body.append("    ")
                                .append(toCExpression(lhs))
                                .append(" = ")
                                .append(toCExpression(rhs))
                                .append(";\n");
                    } else {
                        if (!NAME.matcher(lhs).matches()) {
                            return OracleSource.unsupported("unsupported assignment target");
                        }
                        if (declared.add(lhs)) {
                            localPointers.add(lhs);
                            body.append("    ")
                                    .append(document.structure().name())
                                    .append(" *")
                                    .append(lhs)
                                    .append(" = ")
                                    .append(toCExpression(rhs))
                                    .append(";\n");
                        } else {
                            body.append("    ")
                                    .append(lhs)
                                    .append(" = ")
                                    .append(toCExpression(rhs))
                                    .append(";\n");
                        }
                    }
                }
                case PathOperation.Malloc(var variable) -> {
                    if (declared.add(variable)) {
                        localPointers.add(variable);
                        body.append("    ")
                                .append(document.structure().name())
                                .append(" *")
                                .append(variable);
                    } else {
                        body.append("    ").append(variable);
                    }
                    body.append(" = malloc(sizeof(")
                            .append(document.structure().name())
                            .append("));\n")
                            .append("    if (")
                            .append(variable)
                            .append(" == NULL) abort();\n");
                    allocated.add(variable);
                }
                case PathOperation.Free(var variable) -> {
                    if (!declared.contains(variable)) {
                        return OracleSource.unsupported("free refers to an undeclared pointer");
                    }
                    body.append("    free(").append(variable).append(");\n");
                    freed.add(variable);
                }
                case PathOperation.Deref(var expr) -> {
                    if (!isFieldAccess(expr) || !declared.contains(baseVariable(expr))) {
                        return OracleSource.unsupported(
                                "deref refers to an undeclared pointer");
                    }
                    String fieldName = fieldName(expr);
                    FieldModel field = document.structure().requireField(fieldName);
                    String sink = "heap_mapper_sink_" + sinkIndex++;
                    if (field.kind() == FieldModel.Kind.INT) {
                        body.append("    volatile int ").append(sink);
                    } else {
                        body.append("    ")
                                .append(document.structure().name())
                                .append(" * volatile ")
                                .append(sink);
                    }
                    body.append(" = ")
                            .append(toCExpression(expr))
                            .append(";\n")
                            .append("    (void)")
                            .append(sink)
                            .append(";\n");
                }
            }
        }

        localPointers.stream()
                .sorted()
                .forEach(variable ->
                        body.append("    (void)").append(variable).append(";\n"));

        allocated.stream()
                .filter(variable -> !freed.contains(variable))
                .sorted()
                .forEach(variable ->
                        body.append("    free(").append(variable).append(");\n"));

        if (mapping.status() == ResultStatus.SAFE) {
            appendTargetMarker(body);
        }

        String source =
                "#include <stdio.h>\n\n"
                        + "static void heap_mapper_mark_target(void) {\n"
                        + "    fputs(\"HEAP_MAPPER_TARGET_REACHED\\n\", stderr);\n"
                        + "    fflush(stderr);\n"
                        + "}\n\n"
                        + "void "
                        + document.targetFunction()
                        + "("
                        + document.structure().name()
                        + " *"
                        + document.entryPointer()
                        + ") {\n"
                        + body
                        + "}\n";
        return OracleSource.supported(source);
    }

    private static void appendTargetMarker(StringBuilder body) {
        body.append("    heap_mapper_mark_target();\n");
    }

    private static boolean runtimePasses(
            ResultStatus expected,
            Boolean targetReached,
            Boolean sanitizerClean,
            Boolean sanitizerMatched) {
        if (!Boolean.TRUE.equals(targetReached)) return false;
        return switch (expected) {
            case SAFE -> Boolean.TRUE.equals(sanitizerClean);
            case VIOLATION -> Boolean.TRUE.equals(sanitizerMatched);
            default -> true;
        };
    }

    private static boolean matchesViolation(ViolationKind kind, String output) {
        if (kind == null) return false;
        String normalized = output.toLowerCase(Locale.ROOT);
        return switch (kind) {
            case NULL_DEREFERENCE ->
                    (normalized.contains("addresssanitizer")
                                    && normalized.contains("segv"))
                            || normalized.contains("member access within null pointer")
                            || normalized.contains("load of null pointer");
            case USE_AFTER_FREE ->
                    normalized.contains("heap-use-after-free")
                            || normalized.contains("use-after-free");
            case DOUBLE_FREE ->
                    normalized.contains("double-free")
                            || normalized.contains("attempting double-free");
        };
    }

    private static boolean allVariablesDeclared(String expression, Set<String> declared) {
        String normalized = expression.replace("->", ".");
        String[] sides = normalized.split("==|!=", -1);
        for (String side : sides) {
            String term = side.strip();
            if (term.equals("NULL") || term.matches("[+-]?\\d+")) continue;
            String variable = isFieldAccess(term) ? baseVariable(term) : term;
            if (!declared.contains(variable)) return false;
        }
        return true;
    }

    private static boolean isFieldAccess(String expression) {
        return FIELD_ACCESS.matcher(expression.replace("->", ".").strip()).matches();
    }

    private static String baseVariable(String expression) {
        Matcher matcher = FIELD_ACCESS.matcher(expression.replace("->", ".").strip());
        if (!matcher.matches()) throw new IllegalArgumentException("not a field access: " + expression);
        return matcher.group(1);
    }

    private static String fieldName(String expression) {
        Matcher matcher = FIELD_ACCESS.matcher(expression.replace("->", ".").strip());
        if (!matcher.matches()) throw new IllegalArgumentException("not a field access: " + expression);
        return matcher.group(2);
    }

    private static String toCExpression(String expression) {
        String normalized = expression.replace("->", ".");
        return FIELD_ACCESS.matcher(normalized).replaceAll("$1->$2");
    }

    private static ProcessResult runProcess(
            List<String> command,
            Path directory,
            int timeoutSeconds,
            String... environmentPairs) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(directory.toFile())
                .redirectErrorStream(true);
        for (int i = 0; i + 1 < environmentPairs.length; i += 2) {
            builder.environment().put(environmentPairs[i], environmentPairs[i + 1]);
        }

        Process process = builder.start();
        boolean finished;
        try {
            finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return new ProcessResult(124, "interrupted");
        }
        if (!finished) {
            process.destroyForcibly();
            return new ProcessResult(124, "timeout after " + timeoutSeconds + " seconds");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new ProcessResult(process.exitValue(), output);
    }

    private static Summary summarize(String suiteName, List<CaseResult> results) {
        int supported = 0;
        int expectedGeneratable = 0;
        int generated = 0;
        int compiled = 0;
        int generatedCases = 0;
        int runtimeSupported = 0;
        int targetReached = 0;
        int expectedViolations = 0;
        int matchedViolations = 0;
        int expectedSafeRuntime = 0;
        int falseConfirmations = 0;
        int baselineAttempts = 0;
        int baselineTargetHits = 0;
        int baselineViolationAttempts = 0;
        int baselineSanitizerHits = 0;
        int endToEndConfirmed = 0;
        int buggyCases = 0;
        int buggyConfirmed = 0;
        int goodCases = 0;
        int goodVerified = 0;
        int incorrect = 0;
        int nodeSum = 0;
        int nodeCount = 0;
        var medians = new ArrayList<Double>();
        var statusCounts = new EnumMap<ResultStatus, Integer>(ResultStatus.class);
        for (ResultStatus status : ResultStatus.values()) statusCounts.put(status, 0);

        for (CaseResult result : results) {
            if (!result.casePassed()) incorrect++;
            statusCounts.merge(result.actual(), 1, Integer::sum);
            if (result.actual() != ResultStatus.UNSUPPORTED) supported++;
            if (result.expected() == ResultStatus.SAFE
                    || result.expected() == ResultStatus.VIOLATION) {
                expectedGeneratable++;
                if (result.actual() == ResultStatus.SAFE
                        || result.actual() == ResultStatus.VIOLATION) {
                    generated++;
                }
            }
            if (result.actual() == ResultStatus.SAFE
                    || result.actual() == ResultStatus.VIOLATION) {
                generatedCases++;
                if (Boolean.TRUE.equals(result.compiled())) compiled++;
                if (result.actualNodes() != null) {
                    nodeSum += result.actualNodes();
                    nodeCount++;
                }
            }
            if (Boolean.TRUE.equals(result.runtimeSupported())) {
                runtimeSupported++;
                if (Boolean.TRUE.equals(result.targetReached())) targetReached++;
                if (result.expected() == ResultStatus.VIOLATION) {
                    expectedViolations++;
                    if (Boolean.TRUE.equals(result.sanitizerMatched())) matchedViolations++;
                }
                if (result.expected() == ResultStatus.SAFE) {
                    expectedSafeRuntime++;
                    if (Boolean.FALSE.equals(result.sanitizerClean())) falseConfirmations++;
                }
            }
            if (Boolean.TRUE.equals(result.targetReached())) {
                endToEndConfirmed++;
            }
            if ("buggy".equalsIgnoreCase(result.groundTruth())) {
                buggyCases++;
                if (Boolean.TRUE.equals(result.sanitizerMatched())) buggyConfirmed++;
            }
            if ("good".equalsIgnoreCase(result.groundTruth())) {
                goodCases++;
                if (result.actual() == ResultStatus.SAFE
                        && Boolean.TRUE.equals(result.targetReached())
                        && Boolean.TRUE.equals(result.sanitizerClean())) {
                    goodVerified++;
                }
            }
            if (Boolean.TRUE.equals(result.baselineCompiled())
                    && result.baselineAttempts() != null) {
                baselineAttempts += result.baselineAttempts();
                baselineTargetHits += result.baselineTargetHits();
                if (result.expected() == ResultStatus.VIOLATION) {
                    baselineViolationAttempts += result.baselineAttempts();
                    baselineSanitizerHits += result.baselineSanitizerHits();
                }
            }
            if (result.generationMedianUs() > 0.0) {
                medians.add(result.generationMedianUs());
            }
        }

        medians.sort(Comparator.naturalOrder());
        double suiteMedian = medians.isEmpty() ? 0.0 : medians.get(medians.size() / 2);
        int p95Index = medians.isEmpty()
                ? 0
                : Math.min(
                        medians.size() - 1,
                        (int) Math.ceil(medians.size() * 0.95) - 1);
        double p95 = medians.isEmpty() ? 0.0 : medians.get(p95Index);
        int correct = results.size() - results.stream()
                .filter(result -> !result.classificationCorrect())
                .toList()
                .size();
        return new Summary(
                suiteName,
                results.size(),
                incorrect,
                ratio(correct, results.size()),
                ratio(supported, results.size()),
                ratio(generated, expectedGeneratable),
                ratio(compiled, generatedCases),
                ratio(targetReached, runtimeSupported),
                ratio(matchedViolations, expectedViolations),
                ratio(falseConfirmations, expectedSafeRuntime),
                ratio(endToEndConfirmed, results.size()),
                ratio(buggyConfirmed, buggyCases),
                ratio(goodVerified, goodCases),
                ratio(baselineTargetHits, baselineAttempts),
                ratio(baselineSanitizerHits, baselineViolationAttempts),
                suiteMedian,
                p95,
                nodeCount == 0 ? 0.0 : (double) nodeSum / nodeCount,
                Map.copyOf(statusCounts));
    }

    private static String toCsv(List<CaseResult> results) {
        var output = new StringBuilder();
        output.append(
                "id,category,origin,ground_truth,structure_category,defect_type,"
                        + "source_url,source_file,property,expected,actual,"
                        + "classification_correct,diagnostic_match,"
                        + "diagnostic,expected_nodes,actual_nodes,node_count_match,"
                        + "parse_us,generation_median_us,c_compiled,runtime_supported,"
                        + "target_reached,sanitizer_clean,sanitizer_matched,"
                        + "baseline_attempts,baseline_compiled,baseline_target_hits,"
                        + "baseline_sanitizer_hits,"
                        + "case_passed,tool_output\n");
        for (CaseResult result : results) {
            output.append(csv(result.id())).append(',')
                    .append(csv(result.category())).append(',')
                    .append(csv(result.origin())).append(',')
                    .append(csv(result.groundTruth())).append(',')
                    .append(csv(result.structureCategory())).append(',')
                    .append(csv(result.defectType())).append(',')
                    .append(csv(result.sourceUrl())).append(',')
                    .append(csv(result.sourceFile())).append(',')
                    .append(csv(result.property())).append(',')
                    .append(result.expected()).append(',')
                    .append(result.actual()).append(',')
                    .append(result.classificationCorrect()).append(',')
                    .append(result.diagnosticMatch()).append(',')
                    .append(csv(result.diagnostic())).append(',')
                    .append(nullable(result.expectedNodes())).append(',')
                    .append(nullable(result.actualNodes())).append(',')
                    .append(result.nodeCountMatch()).append(',')
                    .append(format(result.parseUs())).append(',')
                    .append(format(result.generationMedianUs())).append(',')
                    .append(nullable(result.compiled())).append(',')
                    .append(nullable(result.runtimeSupported())).append(',')
                    .append(nullable(result.targetReached())).append(',')
                    .append(nullable(result.sanitizerClean())).append(',')
                    .append(nullable(result.sanitizerMatched())).append(',')
                    .append(nullable(result.baselineAttempts())).append(',')
                    .append(nullable(result.baselineCompiled())).append(',')
                    .append(nullable(result.baselineTargetHits())).append(',')
                    .append(nullable(result.baselineSanitizerHits())).append(',')
                    .append(result.casePassed()).append(',')
                    .append(csv(result.toolOutput()))
                    .append('\n');
        }
        return output.toString();
    }

    private static String toMarkdown(Summary summary, List<CaseResult> results) {
        var output = new StringBuilder();
        output.append("# Benchmark report: ").append(summary.suiteName()).append("\n\n")
                .append("## Summary\n\n")
                .append("| Metric | Value |\n")
                .append("| --- | ---: |\n")
                .append("| Cases | ").append(summary.totalCases()).append(" |\n")
                .append("| Cases matching declared manifest outcome | ")
                .append(summary.totalCases() - summary.incorrectCases()).append(" |\n")
                .append("| Manifest outcome agreement | ")
                .append(format(summary.accuracy())).append(" |\n")
                .append("| Applicability | ")
                .append(format(summary.applicability())).append(" |\n")
                .append("| End-to-end confirmation coverage | ")
                .append(format(summary.endToEndCoverage())).append(" |\n")
                .append("| Buggy-case confirmation coverage | ")
                .append(format(summary.bugConfirmationCoverage())).append(" |\n")
                .append("| Good-case verification coverage | ")
                .append(format(summary.goodVerificationCoverage())).append(" |\n")
                .append("| Input generation success | ")
                .append(format(summary.generationSuccessRate())).append(" |\n")
                .append("| Generated C compilation rate | ")
                .append(format(summary.compilationRate())).append(" |\n")
                .append("| Target reach rate | ")
                .append(format(summary.targetReachRate())).append(" |\n")
                .append("| Matched sanitizer confirmation rate | ")
                .append(format(summary.matchedSanitizerRate())).append(" |\n")
                .append("| False confirmation rate | ")
                .append(format(summary.falseConfirmationRate())).append(" |\n")
                .append("| Random baseline target reach per attempt | ")
                .append(format(summary.baselineTargetReachRate())).append(" |\n")
                .append("| Random baseline sanitizer confirmation per violation attempt | ")
                .append(format(summary.baselineSanitizerRate())).append(" |\n")
                .append("| Median generation time, us | ")
                .append(format(summary.medianGenerationUs())).append(" |\n")
                .append("| P95 generation time, us | ")
                .append(format(summary.p95GenerationUs())).append(" |\n")
                .append("| Mean generated node count | ")
                .append(format(summary.meanNodeCount())).append(" |\n\n")
                .append("## Outcome distribution\n\n")
                .append("| Outcome | Cases |\n")
                .append("| --- | ---: |\n");

        for (ResultStatus status : ResultStatus.values()) {
            output.append("| ").append(status).append(" | ")
                    .append(summary.statusCounts().getOrDefault(status, 0))
                    .append(" |\n");
        }

        appendGroupedResults(output, "Structure category", results, true);
        appendGroupedResults(output, "Defect type", results, false);

        output.append("\n")
                .append("## Cases\n\n")
                .append("| ID | Origin | Category | Truth | Expected | Actual | Nodes | C compile | Target | Sanitizer match | Baseline target | Time, us | Pass |\n")
                .append("| --- | --- | --- | --- | --- | --- | ---: | --- | --- | --- | ---: | ---: | --- |\n");

        for (CaseResult result : results) {
            output.append("| ").append(result.id())
                    .append(" | ").append(result.origin())
                    .append(" | ").append(result.category())
                    .append(" | ").append(result.groundTruth())
                    .append(" | ").append(result.expected())
                    .append(" | ").append(result.actual())
                    .append(" | ").append(nullable(result.actualNodes()))
                    .append(" | ").append(mark(result.compiled()))
                    .append(" | ").append(mark(result.targetReached()))
                    .append(" | ").append(mark(result.sanitizerMatched()))
                    .append(" | ").append(baselineRatio(result))
                    .append(" | ").append(format(result.generationMedianUs()))
                    .append(" | ").append(result.casePassed() ? "yes" : "no")
                    .append(" |\n");
        }
        return output.toString();
    }

    private static void appendGroupedResults(
            StringBuilder output,
            String heading,
            List<CaseResult> results,
            boolean byStructure) {
        var groups = new TreeMap<String, List<CaseResult>>();
        for (CaseResult result : results) {
            String key = byStructure
                    ? result.structureCategory()
                    : result.defectType();
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(result);
        }
        output.append("\n## By ").append(heading.toLowerCase(Locale.ROOT)).append("\n\n")
                .append("| ").append(heading)
                .append(" | Cases | Correct | SAFE | UNSAT | VIOLATION | UNSUPPORTED | TIMEOUT | COMPILE_ERROR | Target reach |\n")
                .append("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        for (Map.Entry<String, List<CaseResult>> entry : groups.entrySet()) {
            List<CaseResult> group = entry.getValue();
            long correct = group.stream().filter(CaseResult::classificationCorrect).count();
            long runtime = group.stream()
                    .filter(result -> Boolean.TRUE.equals(result.runtimeSupported()))
                    .count();
            long reached = group.stream()
                    .filter(result -> Boolean.TRUE.equals(result.targetReached()))
                    .count();
            output.append("| ").append(entry.getKey())
                    .append(" | ").append(group.size())
                    .append(" | ").append(correct)
                    .append(" | ").append(countStatus(group, ResultStatus.SAFE))
                    .append(" | ").append(countStatus(group, ResultStatus.UNSAT))
                    .append(" | ").append(countStatus(group, ResultStatus.VIOLATION))
                    .append(" | ").append(countStatus(group, ResultStatus.UNSUPPORTED))
                    .append(" | ").append(countStatus(group, ResultStatus.TIMEOUT))
                    .append(" | ").append(countStatus(group, ResultStatus.COMPILE_ERROR))
                    .append(" | ").append(format(ratio((int) reached, (int) runtime)))
                    .append(" |\n");
        }
    }

    private static long countStatus(List<CaseResult> results, ResultStatus status) {
        return results.stream().filter(result -> result.actual() == status).count();
    }

    private static String baselineRatio(CaseResult result) {
        if (result.baselineAttempts() == null || result.baselineTargetHits() == null) {
            return "n/a";
        }
        return result.baselineTargetHits() + "/" + result.baselineAttempts();
    }

    private static String mark(Boolean value) {
        if (value == null) return "n/a";
        return value ? "pass" : "fail";
    }

    private static String nullable(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String csv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static String requiredString(JsonNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("missing non-blank string '" + key + "'");
        }
        return value.asText().strip();
    }

    private static String optionalString(JsonNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) {
            throw new IllegalArgumentException("'" + key + "' must be a string");
        }
        return value.asText();
    }

    private static String optionalString(JsonNode node, String key, String fallback) {
        String value = optionalString(node, key);
        return value == null ? fallback : value;
    }

    private static Integer optionalInteger(JsonNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null || value.isNull()) return null;
        if (!value.isIntegralNumber()) {
            throw new IllegalArgumentException("'" + key + "' must be an integer");
        }
        return value.intValue();
    }

    private static double ratio(int numerator, int denominator) {
        return denominator == 0 ? 0.0 : (double) numerator / denominator;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String safeFileName(String id) {
        return id.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private static String oneLine(String output) {
        return output == null ? "" : output.strip().replaceAll("\\s+", " ");
    }

    private record BenchmarkSuite(String name, List<BenchmarkCase> cases) {}

    private record BenchmarkCase(
            String id,
            String category,
            String origin,
            String groundTruth,
            String structureCategory,
            String defectType,
            String sourceUrl,
            String sourceFile,
            String property,
            String input,
            ResultStatus expected,
            String expectedDiagnostic,
            Integer expectedNodes,
            String unsupportedReason) {}

    private record CaseResult(
            String id,
            String category,
            String origin,
            String groundTruth,
            String structureCategory,
            String defectType,
            String sourceUrl,
            String sourceFile,
            String property,
            ResultStatus expected,
            ResultStatus actual,
            boolean classificationCorrect,
            boolean diagnosticMatch,
            String diagnostic,
            Integer expectedNodes,
            Integer actualNodes,
            boolean nodeCountMatch,
            double parseUs,
            double generationMedianUs,
            Boolean compiled,
            Boolean runtimeSupported,
            Boolean targetReached,
            Boolean sanitizerClean,
            Boolean sanitizerMatched,
            Integer baselineAttempts,
            Boolean baselineCompiled,
            Integer baselineTargetHits,
            Integer baselineSanitizerHits,
            String toolOutput,
            boolean casePassed) {}

    private record Summary(
            String suiteName,
            int totalCases,
            int incorrectCases,
            double accuracy,
            double applicability,
            double generationSuccessRate,
            double compilationRate,
            double targetReachRate,
            double matchedSanitizerRate,
            double falseConfirmationRate,
            double endToEndCoverage,
            double bugConfirmationCoverage,
            double goodVerificationCoverage,
            double baselineTargetReachRate,
            double baselineSanitizerRate,
            double medianGenerationUs,
            double p95GenerationUs,
            double meanNodeCount,
            Map<ResultStatus, Integer> statusCounts) {}

    private record OracleSource(boolean supported, String source, String reason) {
        static OracleSource supported(String source) {
            return new OracleSource(true, source, "");
        }

        static OracleSource unsupported(String reason) {
            return new OracleSource(false, "", reason);
        }
    }

    private record ProcessResult(int exitCode, String output) {}

    private record BaselineResult(
            boolean compiled,
            int targetHits,
            int sanitizerHits,
            String output) {}

    private record BaselineAttempt(
            boolean targetReached,
            boolean sanitizerMatched) {}
}
