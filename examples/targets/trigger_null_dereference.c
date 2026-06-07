#include <stddef.h>

typedef struct Node {
    int key;
    struct Node *next;
} Node;

void trigger_null_dereference(Node *p) {
    volatile int value = p->key;
    (void)value;
}
