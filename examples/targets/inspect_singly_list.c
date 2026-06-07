#include <assert.h>
#include <stddef.h>

typedef struct Node {
    int key;
    struct Node *next;
} Node;

void inspect_singly_list(Node *head) {
    assert(head != NULL);
    assert(head->next != NULL);
    assert(head->next->key == 5);
    assert(head->next->next == NULL);
}
