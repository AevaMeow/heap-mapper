typedef struct Node {
    int key;
    struct Node *next;
} Node;

void unreachable_target(Node *p) {
    (void)p;
}
