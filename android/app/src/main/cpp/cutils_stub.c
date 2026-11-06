// Stand-in for the few libcutils functions Turnip (libvulkan_freedreno.so) uses. The real
// libcutils isn't available to apps; this one is found first in the app's native library dir.
#include <stdint.h>
#include <string.h>
#include <sys/system_properties.h>

uint64_t atrace_get_enabled_tags(void) { return 0; } // ATRACE_TAG_NEVER: no tracing
void atrace_init(void) {}
void atrace_begin_body(const char *name) { (void) name; }
void atrace_end_body(void) {}

int property_get(const char *key, char *value, const char *default_value) {
    int len = __system_property_get(key, value);
    if (len > 0) return len;
    if (default_value) {
        len = (int) strlen(default_value);
        if (len >= PROP_VALUE_MAX) len = PROP_VALUE_MAX - 1;
        memcpy(value, default_value, (size_t) len);
        value[len] = 0;
        return len;
    }
    value[0] = 0;
    return 0;
}
