# Benchmark report: heap-mapper-all-v1

## Summary

| Metric | Value |
| --- | ---: |
| Cases | 94 |
| Cases matching declared manifest outcome | 94 |
| Manifest outcome agreement | 1.000 |
| Applicability | 0.681 |
| End-to-end confirmation coverage | 0.500 |
| Buggy-case confirmation coverage | 0.077 |
| Good-case verification coverage | 0.333 |
| Input generation success | 1.000 |
| Generated C compilation rate | 1.000 |
| Target reach rate | 1.000 |
| Matched sanitizer confirmation rate | 1.000 |
| False confirmation rate | 0.000 |
| Random baseline target reach per attempt | 0.206 |
| Random baseline sanitizer confirmation per violation attempt | 0.417 |
| Median generation time, us | 58.459 |
| P95 generation time, us | 375.626 |
| Mean generated node count | 5.620 |

## Outcome distribution

| Outcome | Cases |
| --- | ---: |
| SAFE | 37 |
| UNSAT | 14 |
| VIOLATION | 13 |
| UNSUPPORTED | 30 |
| TIMEOUT | 0 |
| COMPILE_ERROR | 0 |

## By structure category

| Structure category | Cases | Correct | SAFE | UNSAT | VIOLATION | UNSUPPORTED | TIMEOUT | COMPILE_ERROR | Target reach |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| aliasing | 5 | 5 | 2 | 3 | 0 | 0 | 0 | 0 | 0.000 |
| binary-tree | 2 | 2 | 2 | 0 | 0 | 0 | 0 | 0 | 1.000 |
| composite-constraint-conflict | 2 | 2 | 0 | 2 | 0 | 0 | 0 | 0 | 0.000 |
| composite-cycle | 3 | 3 | 2 | 1 | 0 | 0 | 0 | 0 | 1.000 |
| composite-double-free | 1 | 1 | 0 | 0 | 1 | 0 | 0 | 0 | 1.000 |
| composite-doubly-list | 3 | 3 | 2 | 1 | 0 | 0 | 0 | 0 | 1.000 |
| composite-graph | 1 | 1 | 1 | 0 | 0 | 0 | 0 | 0 | 1.000 |
| composite-memory-safety | 1 | 1 | 1 | 0 | 0 | 0 | 0 | 0 | 1.000 |
| composite-null-dereference | 1 | 1 | 0 | 0 | 1 | 0 | 0 | 0 | 1.000 |
| composite-singly-list | 2 | 2 | 2 | 0 | 0 | 0 | 0 | 0 | 1.000 |
| composite-tree | 5 | 5 | 3 | 2 | 0 | 0 | 0 | 0 | 1.000 |
| composite-tree-aliasing | 3 | 3 | 1 | 1 | 1 | 0 | 0 | 0 | 1.000 |
| composite-use-after-free | 2 | 2 | 0 | 0 | 2 | 0 | 0 | 0 | 1.000 |
| constraint-conflict | 4 | 4 | 0 | 4 | 0 | 0 | 0 | 0 | 0.000 |
| cycle | 7 | 7 | 3 | 0 | 0 | 4 | 0 | 0 | 1.000 |
| cyclic-list | 1 | 1 | 1 | 0 | 0 | 0 | 0 | 0 | 1.000 |
| double-free | 1 | 1 | 0 | 0 | 1 | 0 | 0 | 0 | 1.000 |
| doubly-linked-list | 1 | 1 | 1 | 0 | 0 | 0 | 0 | 0 | 1.000 |
| doubly-list | 7 | 7 | 1 | 0 | 0 | 6 | 0 | 0 | 1.000 |
| memory-safety | 3 | 3 | 3 | 0 | 0 | 0 | 0 | 0 | 1.000 |
| nested-list | 2 | 2 | 0 | 0 | 0 | 2 | 0 | 0 | 0.000 |
| null-dereference | 2 | 2 | 0 | 0 | 2 | 0 | 0 | 0 | 1.000 |
| scalar-allocation | 12 | 12 | 0 | 0 | 0 | 12 | 0 | 0 | 0.000 |
| scale-binary-tree | 1 | 1 | 1 | 0 | 0 | 0 | 0 | 0 | 1.000 |
| scale-singly-list | 1 | 1 | 1 | 0 | 0 | 0 | 0 | 0 | 1.000 |
| singly-list | 13 | 13 | 9 | 0 | 0 | 4 | 0 | 0 | 1.000 |
| tree | 4 | 4 | 1 | 0 | 1 | 2 | 0 | 0 | 1.000 |
| use-after-free | 4 | 4 | 0 | 0 | 4 | 0 | 0 | 0 | 1.000 |

## By defect type

| Defect type | Cases | Correct | SAFE | UNSAT | VIOLATION | UNSUPPORTED | TIMEOUT | COMPILE_ERROR | Target reach |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| aliasing | 5 | 5 | 2 | 3 | 0 | 0 | 0 | 0 | 0.000 |
| binary-tree | 2 | 2 | 2 | 0 | 0 | 0 | 0 | 0 | 1.000 |
| composite-constraint-conflict | 2 | 2 | 0 | 2 | 0 | 0 | 0 | 0 | 0.000 |
| composite-cycle | 3 | 3 | 2 | 1 | 0 | 0 | 0 | 0 | 1.000 |
| composite-double-free | 1 | 1 | 0 | 0 | 1 | 0 | 0 | 0 | 1.000 |
| composite-doubly-list | 3 | 3 | 2 | 1 | 0 | 0 | 0 | 0 | 1.000 |
| composite-graph | 1 | 1 | 1 | 0 | 0 | 0 | 0 | 0 | 1.000 |
| composite-memory-safety | 1 | 1 | 1 | 0 | 0 | 0 | 0 | 0 | 1.000 |
| composite-null-dereference | 1 | 1 | 0 | 0 | 1 | 0 | 0 | 0 | 1.000 |
| composite-singly-list | 2 | 2 | 2 | 0 | 0 | 0 | 0 | 0 | 1.000 |
| composite-tree | 5 | 5 | 3 | 2 | 0 | 0 | 0 | 0 | 1.000 |
| composite-tree-aliasing | 3 | 3 | 1 | 1 | 1 | 0 | 0 | 0 | 1.000 |
| composite-use-after-free | 2 | 2 | 0 | 0 | 2 | 0 | 0 | 0 | 1.000 |
| constraint-conflict | 4 | 4 | 0 | 4 | 0 | 0 | 0 | 0 | 0.000 |
| cyclic-list | 1 | 1 | 1 | 0 | 0 | 0 | 0 | 0 | 1.000 |
| double-free | 1 | 1 | 0 | 0 | 1 | 0 | 0 | 0 | 1.000 |
| doubly-linked-list | 1 | 1 | 1 | 0 | 0 | 0 | 0 | 0 | 1.000 |
| memory-leak | 5 | 5 | 0 | 0 | 0 | 5 | 0 | 0 | 0.000 |
| memory-safety | 3 | 3 | 3 | 0 | 0 | 0 | 0 | 0 | 1.000 |
| none | 21 | 21 | 9 | 0 | 0 | 12 | 0 | 0 | 1.000 |
| null-dereference | 4 | 4 | 0 | 0 | 3 | 1 | 0 | 0 | 1.000 |
| scale-binary-tree | 1 | 1 | 1 | 0 | 0 | 0 | 0 | 0 | 1.000 |
| scale-singly-list | 1 | 1 | 1 | 0 | 0 | 0 | 0 | 0 | 1.000 |
| singly-list | 5 | 5 | 5 | 0 | 0 | 0 | 0 | 0 | 1.000 |
| use-after-free | 16 | 16 | 0 | 0 | 4 | 12 | 0 | 0 | 1.000 |

## Cases

| ID | Origin | Category | Truth | Expected | Actual | Nodes | C compile | Target | Sanitizer match | Baseline target | Time, us | Pass |
| --- | --- | --- | --- | --- | --- | ---: | --- | --- | --- | ---: | ---: | --- |
| F01-empty-list | internal | singly-list | safe | SAFE | SAFE | 0 | pass | pass | n/a | 100/100 | 285.120 | yes |
| F02-one-node | internal | singly-list | safe | SAFE | SAFE | 1 | pass | pass | n/a | 50/100 | 299.457 | yes |
| F03-tail-null | internal | singly-list | safe | SAFE | SAFE | 1 | pass | pass | n/a | 50/100 | 292.338 | yes |
| F04-two-nodes | internal | singly-list | safe | SAFE | SAFE | 2 | pass | pass | n/a | 13/100 | 470.397 | yes |
| F05-three-nodes | internal | singly-list | safe | SAFE | SAFE | 3 | pass | pass | n/a | 25/100 | 457.983 | yes |
| F06-live-deref | internal | memory-safety | safe | SAFE | SAFE | 1 | pass | pass | n/a | 50/100 | 126.392 | yes |
| F07-free-null | internal | memory-safety | safe | SAFE | SAFE | 0 | pass | pass | n/a | 100/100 | 45.156 | yes |
| F08-free-live | internal | memory-safety | safe | SAFE | SAFE | 1 | pass | pass | n/a | 50/100 | 91.760 | yes |
| F09-cycle | internal | cyclic-list | safe | SAFE | SAFE | 2 | pass | pass | n/a | 7/100 | 182.581 | yes |
| F10-doubly-linked | internal | doubly-linked-list | safe | SAFE | SAFE | 2 | pass | pass | n/a | 14/100 | 159.003 | yes |
| F11-binary-tree | internal | binary-tree | safe | SAFE | SAFE | 3 | pass | pass | n/a | 0/100 | 222.635 | yes |
| F12-tree-left-child | internal | binary-tree | safe | SAFE | SAFE | 2 | pass | pass | n/a | 5/100 | 143.107 | yes |
| F13-alias | internal | aliasing | safe | SAFE | SAFE | 1 | pass | n/a | n/a | n/a | 70.543 | yes |
| F14-disequality | internal | aliasing | safe | SAFE | SAFE | 2 | pass | n/a | n/a | n/a | 67.453 | yes |
| I01-null-field-read | internal | null-dereference | violation | VIOLATION | VIOLATION | 0 | pass | pass | pass | 100/100 | 33.901 | yes |
| I02-explicit-null-deref | internal | null-dereference | violation | VIOLATION | VIOLATION | 0 | pass | pass | pass | 100/100 | 23.248 | yes |
| I03-variable-nullability | internal | constraint-conflict | unsat | UNSAT | UNSAT |  | n/a | n/a | n/a | n/a | 12.514 | yes |
| I04-field-nullability | internal | constraint-conflict | unsat | UNSAT | UNSAT |  | n/a | n/a | n/a | n/a | 30.971 | yes |
| I05-equality-conflict | internal | aliasing | unsat | UNSAT | UNSAT |  | n/a | n/a | n/a | n/a | 16.225 | yes |
| I06-inequality-conflict | internal | aliasing | unsat | UNSAT | UNSAT |  | n/a | n/a | n/a | n/a | 17.806 | yes |
| I07-numeric-conflict | internal | constraint-conflict | unsat | UNSAT | UNSAT |  | n/a | n/a | n/a | n/a | 26.939 | yes |
| I08-explicit-use-after-free | internal | use-after-free | violation | VIOLATION | VIOLATION | 1 | pass | pass | pass | 50/100 | 35.852 | yes |
| I09-numeric-use-after-free | internal | use-after-free | violation | VIOLATION | VIOLATION | 1 | pass | pass | pass | 50/100 | 33.692 | yes |
| I10-double-free | internal | double-free | violation | VIOLATION | VIOLATION | 1 | pass | pass | pass | 50/100 | 39.363 | yes |
| I11-pointer-field-conflict | internal | constraint-conflict | unsat | UNSAT | UNSAT |  | n/a | n/a | n/a | n/a | 24.428 | yes |
| I12-pointer-use-after-free | internal | use-after-free | violation | VIOLATION | VIOLATION | 1 | pass | pass | pass | 50/100 | 37.883 | yes |
| I13-alias-use-after-free | internal | use-after-free | violation | VIOLATION | VIOLATION | 1 | pass | n/a | n/a | n/a | 39.803 | yes |
| I14-self-inequality | internal | aliasing | unsat | UNSAT | UNSAT |  | n/a | n/a | n/a | n/a | 4.632 | yes |
| C01-singly-list-8 | internal | composite-singly-list | safe | SAFE | SAFE | 8 | pass | pass | n/a | 0/100 | 232.877 | yes |
| C02-singly-alias-7 | internal | composite-singly-list | safe | SAFE | SAFE | 7 | pass | pass | n/a | 0/100 | 243.361 | yes |
| C03-doubly-list-6 | internal | composite-doubly-list | safe | SAFE | SAFE | 6 | pass | pass | n/a | 0/100 | 244.932 | yes |
| C04-doubly-reverse-8 | internal | composite-doubly-list | safe | SAFE | SAFE | 8 | pass | pass | n/a | 0/100 | 333.344 | yes |
| C05-cycle-6 | internal | composite-cycle | safe | SAFE | SAFE | 6 | pass | pass | n/a | 0/100 | 194.225 | yes |
| C06-lollipop-cycle-7 | internal | composite-cycle | safe | SAFE | SAFE | 7 | pass | pass | n/a | 0/100 | 226.925 | yes |
| C07-full-tree-7 | internal | composite-tree | safe | SAFE | SAFE | 7 | pass | pass | n/a | 0/100 | 200.287 | yes |
| C08-full-tree-15 | internal | composite-tree | safe | SAFE | SAFE | 15 | pass | pass | n/a | 0/100 | 350.717 | yes |
| C09-shared-subtree-dag-8 | internal | composite-tree-aliasing | safe | SAFE | SAFE | 8 | pass | pass | n/a | 1/100 | 245.142 | yes |
| C10-sparse-zigzag-tree-9 | internal | composite-tree | safe | SAFE | SAFE | 9 | pass | pass | n/a | 0/100 | 181.921 | yes |
| C11-free-tree-leaf-7 | internal | composite-memory-safety | safe | SAFE | SAFE | 7 | pass | pass | n/a | 0/100 | 66.723 | yes |
| C12-cyclic-tree-graph-9 | internal | composite-graph | safe | SAFE | SAFE | 9 | pass | pass | n/a | 0/100 | 99.913 | yes |
| I01-deep-null-deref | internal | composite-null-dereference | violation | VIOLATION | VIOLATION | 7 | pass | pass | pass | 0/100 | 58.459 | yes |
| I02-deep-numeric-conflict | internal | composite-constraint-conflict | unsat | UNSAT | UNSAT |  | n/a | n/a | n/a | n/a | 32.791 | yes |
| I03-deep-pointer-conflict | internal | composite-constraint-conflict | unsat | UNSAT | UNSAT |  | n/a | n/a | n/a | n/a | 24.848 | yes |
| I04-cycle-tail-conflict | internal | composite-cycle | unsat | UNSAT | UNSAT |  | n/a | n/a | n/a | n/a | 32.431 | yes |
| I05-deep-double-free-alias | internal | composite-double-free | violation | VIOLATION | VIOLATION | 6 | pass | pass | pass | 0/100 | 42.484 | yes |
| I06-deep-use-after-free-alias | internal | composite-use-after-free | violation | VIOLATION | VIOLATION | 7 | pass | pass | pass | 0/100 | 48.086 | yes |
| I07-doubly-backlink-conflict | internal | composite-doubly-list | unsat | UNSAT | UNSAT |  | n/a | n/a | n/a | n/a | 32.341 | yes |
| I08-doubly-use-after-free | internal | composite-use-after-free | violation | VIOLATION | VIOLATION | 5 | pass | pass | pass | 0/100 | 51.787 | yes |
| I09-tree-deep-value-conflict | internal | composite-tree | unsat | UNSAT | UNSAT |  | n/a | n/a | n/a | n/a | 70.364 | yes |
| I10-tree-alias-inequality | internal | composite-tree-aliasing | unsat | UNSAT | UNSAT |  | n/a | n/a | n/a | n/a | 40.304 | yes |
| I11-tree-child-conflict | internal | composite-tree | unsat | UNSAT | UNSAT |  | n/a | n/a | n/a | n/a | 63.151 | yes |
| I12-shared-subtree-use-after-free | internal | composite-tree-aliasing | violation | VIOLATION | VIOLATION | 8 | pass | pass | pass | 0/100 | 114.028 | yes |
| S01-singly-list-50 | internal | scale-singly-list | safe | SAFE | SAFE | 50 | pass | pass | n/a | 0/100 | 479.849 | yes |
| S02-binary-tree-50 | internal | scale-binary-tree | safe | SAFE | SAFE | 50 | pass | pass | n/a | 0/100 | 375.626 | yes |
| E01-sv-sll2n-append-equal | SV-Benchmarks | sv-comp-list-simple | good | SAFE | SAFE | 3 | pass | pass | n/a | 0/100 | 50.027 | yes |
| E02-sv-sll2n-append-unequal | SV-Benchmarks | sv-comp-list-simple | good | SAFE | SAFE | 3 | pass | pass | n/a | 0/100 | 36.332 | yes |
| E03-sv-sll2n-insert-equal | SV-Benchmarks | sv-comp-list-simple | good | SAFE | SAFE | 3 | pass | pass | n/a | 0/100 | 32.991 | yes |
| E04-sv-sll2n-insert-unequal | SV-Benchmarks | sv-comp-list-simple | good | SAFE | SAFE | 3 | pass | pass | n/a | 0/100 | 18.976 | yes |
| E05-sv-sll2n-remove-all | SV-Benchmarks | sv-comp-list-simple | good | UNSUPPORTED | UNSUPPORTED |  | n/a | n/a | n/a | n/a | 0.000 | yes |
| E06-sv-sll2n-update-all | SV-Benchmarks | sv-comp-list-simple | good | UNSUPPORTED | UNSUPPORTED |  | n/a | n/a | n/a | n/a | 0.000 | yes |
| E07-sv-sll2c-append-equal | SV-Benchmarks | sv-comp-list-simple | good | SAFE | SAFE | 3 | pass | pass | n/a | 0/100 | 25.948 | yes |
| E08-sv-sll2c-append-unequal | SV-Benchmarks | sv-comp-list-simple | good | UNSUPPORTED | UNSUPPORTED |  | n/a | n/a | n/a | n/a | 0.000 | yes |
| E09-sv-sll2c-remove-all | SV-Benchmarks | sv-comp-list-simple | good | UNSUPPORTED | UNSUPPORTED |  | n/a | n/a | n/a | n/a | 0.000 | yes |
| E10-sv-sll2c-update-all | SV-Benchmarks | sv-comp-list-simple | good | SAFE | SAFE | 2 | pass | pass | n/a | 1/100 | 14.655 | yes |
| E11-sv-dll2n-append-equal | SV-Benchmarks | sv-comp-list-simple | good | SAFE | SAFE | 3 | pass | pass | n/a | 0/100 | 65.201 | yes |
| E12-sv-dll2n-append-unequal | SV-Benchmarks | sv-comp-list-simple | good | UNSUPPORTED | UNSUPPORTED |  | n/a | n/a | n/a | n/a | 0.000 | yes |
| E13-sv-dll2n-remove-all-reverse | SV-Benchmarks | sv-comp-list-simple | good | UNSUPPORTED | UNSUPPORTED |  | n/a | n/a | n/a | n/a | 0.000 | yes |
| E14-sv-dll2c-append-equal | SV-Benchmarks | sv-comp-list-simple | good | SAFE | SAFE | 3 | pass | pass | n/a | 0/100 | 45.155 | yes |
| E15-sv-dll2c-append-unequal | SV-Benchmarks | sv-comp-list-simple | good | UNSUPPORTED | UNSUPPORTED |  | n/a | n/a | n/a | n/a | 0.000 | yes |
| E16-sv-dll2c-remove-all-reverse | SV-Benchmarks | sv-comp-list-simple | good | UNSUPPORTED | UNSUPPORTED |  | n/a | n/a | n/a | n/a | 0.000 | yes |
| E17-sv-tree-1 | SV-Benchmarks | sv-comp-heap-manipulation | buggy | VIOLATION | VIOLATION | 0 | pass | pass | pass | 100/100 | 8.163 | yes |
| E18-sv-tree-2 | SV-Benchmarks | sv-comp-heap-manipulation | buggy | UNSUPPORTED | UNSUPPORTED |  | n/a | n/a | n/a | n/a | 0.000 | yes |
| E19-sv-tree-3 | SV-Benchmarks | sv-comp-heap-manipulation | good | SAFE | SAFE | 2 | pass | pass | n/a | 0/100 | 19.836 | yes |
| E20-sv-tree-4 | SV-Benchmarks | sv-comp-heap-manipulation | buggy | UNSUPPORTED | UNSUPPORTED |  | n/a | n/a | n/a | n/a | 0.000 | yes |
| E21-sv-sll-to-dll-1 | SV-Benchmarks | sv-comp-heap-manipulation | buggy | UNSUPPORTED | UNSUPPORTED |  | n/a | n/a | n/a | n/a | 0.000 | yes |
| E22-sv-sll-to-dll-2 | SV-Benchmarks | sv-comp-heap-manipulation | good | UNSUPPORTED | UNSUPPORTED |  | n/a | n/a | n/a | n/a | 0.000 | yes |
| E23-sv-dll-of-dll-1 | SV-Benchmarks | sv-comp-heap-manipulation | good | UNSUPPORTED | UNSUPPORTED |  | n/a | n/a | n/a | n/a | 0.000 | yes |
| E24-sv-dll-of-dll-2 | SV-Benchmarks | sv-comp-heap-manipulation | buggy | UNSUPPORTED | UNSUPPORTED |  | n/a | n/a | n/a | n/a | 0.000 | yes |
| E25-sv-merge-sort-1 | SV-Benchmarks | sv-comp-heap-manipulation | buggy | UNSUPPORTED | UNSUPPORTED |  | n/a | n/a | n/a | n/a | 0.000 | yes |
| E26-sv-merge-sort-2 | SV-Benchmarks | sv-comp-heap-manipulation | good | UNSUPPORTED | UNSUPPORTED |  | n/a | n/a | n/a | n/a | 0.000 | yes |
| E27-sv-bubble-sort-1 | SV-Benchmarks | sv-comp-heap-manipulation | good | UNSUPPORTED | UNSUPPORTED |  | n/a | n/a | n/a | n/a | 0.000 | yes |
| E28-sv-bubble-sort-2 | SV-Benchmarks | sv-comp-heap-manipulation | buggy | UNSUPPORTED | UNSUPPORTED |  | n/a | n/a | n/a | n/a | 0.000 | yes |
| E29-sard-cwe416-int-01-bad | NIST SARD Juliet via SV-Benchmarks | SARD-Juliet-CWE416 | buggy | UNSUPPORTED | UNSUPPORTED |  | n/a | n/a | n/a | n/a | 0.000 | yes |
| E30-sard-cwe416-int-01-good | NIST SARD Juliet via SV-Benchmarks | SARD-Juliet-CWE416 | good | UNSUPPORTED | UNSUPPORTED |  | n/a | n/a | n/a | n/a | 0.000 | yes |
| E31-sard-cwe416-int-02-bad | NIST SARD Juliet via SV-Benchmarks | SARD-Juliet-CWE416 | buggy | UNSUPPORTED | UNSUPPORTED |  | n/a | n/a | n/a | n/a | 0.000 | yes |
| E32-sard-cwe416-int-02-good | NIST SARD Juliet via SV-Benchmarks | SARD-Juliet-CWE416 | good | UNSUPPORTED | UNSUPPORTED |  | n/a | n/a | n/a | n/a | 0.000 | yes |
| E33-sard-cwe416-int-03-bad | NIST SARD Juliet via SV-Benchmarks | SARD-Juliet-CWE416 | buggy | UNSUPPORTED | UNSUPPORTED |  | n/a | n/a | n/a | n/a | 0.000 | yes |
| E34-sard-cwe416-int-03-good | NIST SARD Juliet via SV-Benchmarks | SARD-Juliet-CWE416 | good | UNSUPPORTED | UNSUPPORTED |  | n/a | n/a | n/a | n/a | 0.000 | yes |
| E35-sard-cwe416-int-04-bad | NIST SARD Juliet via SV-Benchmarks | SARD-Juliet-CWE416 | buggy | UNSUPPORTED | UNSUPPORTED |  | n/a | n/a | n/a | n/a | 0.000 | yes |
| E36-sard-cwe416-int-04-good | NIST SARD Juliet via SV-Benchmarks | SARD-Juliet-CWE416 | good | UNSUPPORTED | UNSUPPORTED |  | n/a | n/a | n/a | n/a | 0.000 | yes |
| E37-sard-cwe416-int-05-bad | NIST SARD Juliet via SV-Benchmarks | SARD-Juliet-CWE416 | buggy | UNSUPPORTED | UNSUPPORTED |  | n/a | n/a | n/a | n/a | 0.000 | yes |
| E38-sard-cwe416-int-05-good | NIST SARD Juliet via SV-Benchmarks | SARD-Juliet-CWE416 | good | UNSUPPORTED | UNSUPPORTED |  | n/a | n/a | n/a | n/a | 0.000 | yes |
| E39-sard-cwe416-int-06-bad | NIST SARD Juliet via SV-Benchmarks | SARD-Juliet-CWE416 | buggy | UNSUPPORTED | UNSUPPORTED |  | n/a | n/a | n/a | n/a | 0.000 | yes |
| E40-sard-cwe416-int-06-good | NIST SARD Juliet via SV-Benchmarks | SARD-Juliet-CWE416 | good | UNSUPPORTED | UNSUPPORTED |  | n/a | n/a | n/a | n/a | 0.000 | yes |
