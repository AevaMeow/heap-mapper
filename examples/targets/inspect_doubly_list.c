#include <assert.h>
#include <stddef.h>

typedef struct DNode {
    int key;
    struct DNode *next;
    struct DNode *prev;
} DNode;

void inspect_doubly_list(DNode *head) {
    assert(head != NULL);
    assert(head->next != NULL);
    assert(head->next->key == 5);
    assert(head->next->prev == head);
    assert(head->prev == NULL);
    assert(head->next->next == NULL);
}
