// Stand-in for libhardware's hw_get_module, which Turnip uses only for gralloc (buffer sharing
// with the display). Offscreen rendering doesn't need it: report "no such module".
#include <errno.h>

int hw_get_module(const char *id, const void **module) {
    (void) id;
    *module = 0;
    return -ENOENT;
}
