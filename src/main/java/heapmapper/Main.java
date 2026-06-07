package heapmapper;

import heapmapper.codegen.CCodeGenerator;
import heapmapper.mapper.AddressMapper;
import heapmapper.model.InfeasiblePathException;
import heapmapper.model.MappingResult;
import heapmapper.model.ResultStatus;
import heapmapper.model.ViolationKind;
import heapmapper.parser.PathParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class Main {

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        String pathArg   = null;
        String outArg    = "generated/test.c";
        String rootArg   = null;
        String targetArg = null;
        String targetSourceArg = null;
        String executableArg = null;
        String compilerArg = "gcc";
        boolean runTest = false;

        try {
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--out" -> outArg = optionValue(args, ++i, "--out");
                    case "--root" -> rootArg = optionValue(args, ++i, "--root");
                    case "--target" -> targetArg = optionValue(args, ++i, "--target");
                    case "--target-source" ->
                            targetSourceArg = optionValue(args, ++i, "--target-source");
                    case "--executable" ->
                            executableArg = optionValue(args, ++i, "--executable");
                    case "--compiler" ->
                            compilerArg = optionValue(args, ++i, "--compiler");
                    case "--run" -> runTest = true;
                    default -> {
                        if (args[i].startsWith("--")) {
                            System.err.println("heap-mapper: unknown option " + args[i]);
                            return 2;
                        }
                        if (pathArg != null) {
                            System.err.println("heap-mapper: only one path argument is allowed");
                            return 2;
                        }
                        pathArg = args[i];
                    }
                }
            }
        } catch (IllegalArgumentException error) {
            System.err.println("heap-mapper: " + error.getMessage());
            return 2;
        }

        if (pathArg == null) {
            System.err.println("heap-mapper: path argument is required");
            printUsage();
            return 2;
        }
        if (runTest && targetSourceArg == null) {
            System.err.println("heap-mapper: --run requires --target-source");
            return 2;
        }
        if (executableArg != null && targetSourceArg == null) {
            System.err.println("heap-mapper: --executable requires --target-source");
            return 2;
        }

        try {
            var document = PathParser.loadDocument(Path.of(pathArg));
            String root = rootArg != null ? rootArg : document.entryPointer();
            String target = targetArg != null ? targetArg : document.targetFunction();
            var result = new AddressMapper(root, target, document.structure())
                    .processPath(document.operations());
            var code = new CCodeGenerator().generate(result);

            Path out = Path.of(outArg);
            if (out.getParent() != null) Files.createDirectories(out.getParent());
            Files.writeString(out, code);
            if (result.status() == ResultStatus.VIOLATION) {
                System.out.println(
                        "generated " + out + " (VIOLATION: " + result.diagnostic() + ")");
            } else {
                System.out.println("generated " + out + " (SAFE)");
            }

            if (targetSourceArg != null) {
                Path targetSource = Path.of(targetSourceArg);
                if (!Files.isRegularFile(targetSource)) {
                    System.err.println(
                            "heap-mapper: target source does not exist: " + targetSource);
                    return 1;
                }
                Path executable = executableArg == null
                        ? defaultExecutable(out)
                        : Path.of(executableArg);
                int compileCode = compile(
                        compilerArg, out, targetSource, executable);
                if (compileCode != 0) return compileCode;
                if (runTest) return execute(executable, result);
            }
            return 0;

        } catch (InfeasiblePathException | IllegalArgumentException e) {
            System.err.println("heap-mapper: " + e.getMessage());
            return 2;
        } catch (IOException e) {
            System.err.println("heap-mapper: " + e.getMessage());
            return 1;
        }
    }

    private static String optionValue(String[] args, int index, String option) {
        if (index >= args.length || args[index].startsWith("--")) {
            throw new IllegalArgumentException("missing value for " + option);
        }
        return args[index];
    }

    private static void printUsage() {
        System.err.println(
                "Usage: heap-mapper <path.json> [--out <file.c>] "
                        + "[--root <var>] [--target <fn>] "
                        + "[--target-source <file.c>] [--executable <file>] "
                        + "[--compiler <gcc|clang>] [--run]");
    }

    private static Path defaultExecutable(Path source) {
        String name = source.getFileName().toString();
        int extension = name.lastIndexOf('.');
        String executableName = extension > 0 ? name.substring(0, extension) : name + "-test";
        Path parent = source.getParent();
        return parent == null ? Path.of(executableName) : parent.resolve(executableName);
    }

    private static int compile(
            String compiler,
            Path harness,
            Path targetSource,
            Path executable) throws IOException {
        Path absoluteExecutable = executable.toAbsolutePath().normalize();
        if (absoluteExecutable.getParent() != null) {
            Files.createDirectories(absoluteExecutable.getParent());
        }

        List<String> command = List.of(
                compiler,
                "-std=c11",
                "-O0",
                "-g",
                "-Wall",
                "-Wextra",
                "-fno-omit-frame-pointer",
                "-fsanitize=address,undefined",
                harness.toAbsolutePath().normalize().toString(),
                targetSource.toAbsolutePath().normalize().toString(),
                "-o",
                absoluteExecutable.toString());
        ProcessResult process = runProcess(command, Map.of(), Duration.ofSeconds(30));
        if (!process.output().isBlank()) System.out.print(process.output());
        if (process.timedOut()) {
            System.err.println("heap-mapper: compilation timed out");
            return 1;
        }
        if (process.exitCode() != 0) {
            System.err.println("heap-mapper: C compilation failed");
            return 1;
        }
        System.out.println("compiled " + executable);
        return 0;
    }

    private static int execute(Path executable, MappingResult result) throws IOException {
        Path absoluteExecutable = executable.toAbsolutePath().normalize();
        ProcessResult process = runProcess(
                List.of(absoluteExecutable.toString()),
                Map.of(
                        "ASAN_OPTIONS", "detect_leaks=1:halt_on_error=1",
                        "UBSAN_OPTIONS", "halt_on_error=1"),
                Duration.ofSeconds(10));
        if (!process.output().isBlank()) System.out.print(process.output());
        if (process.timedOut()) {
            System.err.println("heap-mapper: test execution timed out");
            return 1;
        }

        if (result.status() == ResultStatus.VIOLATION) {
            if (matchesViolation(result.violationKind(), process.output())) {
                System.out.println(
                        "test confirmed " + violationName(result.violationKind()));
                return 0;
            }
            System.err.println("heap-mapper: expected violation was not confirmed");
            return 1;
        }

        if (process.exitCode() == 0 && !containsSanitizerReport(process.output())) {
            System.out.println("test passed");
            return 0;
        }
        System.err.println("heap-mapper: generated test failed");
        return 1;
    }

    private static ProcessResult runProcess(
            List<String> command,
            Map<String, String> environment,
            Duration timeout) throws IOException {
        Path outputFile = Files.createTempFile("heap-mapper-", ".log");
        try {
            ProcessBuilder builder = new ProcessBuilder(new ArrayList<>(command));
            builder.redirectErrorStream(true);
            builder.redirectOutput(outputFile.toFile());
            builder.environment().putAll(environment);
            Process process = builder.start();
            boolean finished;
            try {
                finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IOException("process interrupted", error);
            }
            if (!finished) {
                process.destroyForcibly();
                try {
                    process.waitFor();
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                }
            }
            String output = Files.readString(outputFile, StandardCharsets.UTF_8);
            return new ProcessResult(finished ? process.exitValue() : 124, output, !finished);
        } finally {
            Files.deleteIfExists(outputFile);
        }
    }

    private static boolean containsSanitizerReport(String output) {
        String normalized = output.toLowerCase(Locale.ROOT);
        return normalized.contains("addresssanitizer")
                || normalized.contains("undefinedbehaviorsanitizer")
                || normalized.contains("runtime error:");
    }

    private static boolean matchesViolation(ViolationKind kind, String output) {
        if (kind == null) return false;
        String normalized = output.toLowerCase(Locale.ROOT);
        return switch (kind) {
            case NULL_DEREFERENCE ->
                    normalized.contains("member access within null pointer")
                            || normalized.contains("load of null pointer")
                            || normalized.contains("null pointer")
                                    && normalized.contains("addresssanitizer")
                            || normalized.contains("addresssanitizer")
                                    && normalized.contains("segv");
            case USE_AFTER_FREE ->
                    normalized.contains("heap-use-after-free")
                            || normalized.contains("use-after-free");
            case DOUBLE_FREE ->
                    normalized.contains("double-free")
                            || normalized.contains("attempting double-free");
        };
    }

    private static String violationName(ViolationKind kind) {
        return switch (kind) {
            case NULL_DEREFERENCE -> "null dereference";
            case USE_AFTER_FREE -> "use-after-free";
            case DOUBLE_FREE -> "double free";
        };
    }

    private record ProcessResult(int exitCode, String output, boolean timedOut) {}
}
