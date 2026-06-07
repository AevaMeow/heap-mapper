package heapmapper

import java.nio.file.Files

import spock.lang.Specification

class MainSpec extends Specification {

    def "builds and runs an executable test with the target function"() {
        given:
        def directory = Files.createTempDirectory("heap-mapper-main-")
        def json = directory.resolve("path.json")
        def target = directory.resolve("target.c")
        def harness = directory.resolve("harness.c")
        def executable = directory.resolve("ready-test")
        Files.writeString(json, '''
            {
              "structure": {
                "name": "Node",
                "fields": [
                  {"name": "key", "type": "int"},
                  {"name": "next", "type": "pointer", "target": "Node"}
                ]
              },
              "entry_pointer": "head",
              "target_function": "inspect",
              "path": [
                {"op": "constraint", "expr": "head != NULL"},
                {"op": "constraint", "expr": "head.key == 7"}
              ]
            }
        ''')
        Files.writeString(target, '''
            #include <stdlib.h>

            typedef struct Node {
                int key;
                struct Node *next;
            } Node;

            void inspect(Node *head) {
                if (head == NULL || head->key != 7) {
                    abort();
                }
            }
        ''')

        when:
        int exitCode = Main.run([
            json.toString(),
            "--out", harness.toString(),
            "--target-source", target.toString(),
            "--executable", executable.toString(),
            "--run"
        ] as String[])

        then:
        exitCode == 0
        Files.isRegularFile(harness)
        Files.isExecutable(executable)

        cleanup:
        if (directory != null) {
            Files.walk(directory)
                .sorted(Comparator.reverseOrder())
                .forEach(Files::deleteIfExists)
        }
    }

    def "requires a target source before running a test"() {
        expect:
        Main.run(["path.json", "--run"] as String[]) == 2
    }
}
