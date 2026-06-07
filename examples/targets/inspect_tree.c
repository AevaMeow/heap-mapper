#include <assert.h>
#include <stddef.h>

typedef struct TreeNode {
    int value;
    struct TreeNode *left;
    struct TreeNode *right;
} TreeNode;

void inspect_tree(TreeNode *root) {
    assert(root != NULL);
    assert(root->value == 7);
    assert(root->left != NULL);
    assert(root->left->value == 3);
    assert(root->right != NULL);
    assert(root->right->value == 9);
}
