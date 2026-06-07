#include <assert.h>
#include <stddef.h>

typedef struct Node {
    int key;
    struct Node *next;
} Node;

void inspect_cycle(Node *head) {
    assert(head != NULL);
    assert(head->key == 1);
    assert(head->next != NULL);
    assert(head->next->key == 2);
    assert(head->next->next == head);
}
