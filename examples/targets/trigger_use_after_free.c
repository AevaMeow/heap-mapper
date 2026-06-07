#include <stdlib.h>

typedef struct Node {
    int key;
    struct Node *next;
} Node;

void trigger_use_after_free(Node *p) {
    free(p);
    volatile int value = p->key;
    (void)value;
}
