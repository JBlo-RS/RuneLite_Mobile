// Turnip (Mesa's open-source Vulkan driver for Qualcomm Adreno GPUs) as Zink's Vulkan, for
// phones whose own Vulkan driver Mesa's Zink 23.0 doesn't work with. See vk_turnip.c.
#pragma once
#include <stddef.h>
#include <stdint.h>
#include <vulkan/vulkan.h>

#define VENDOR_QUALCOMM 0x5143

/**
 * Loads Turnip (libvulkan_freedreno.so) and makes this library's exported vkGetInstanceProcAddr
 * lead to it. Returns NULL, or what went wrong.
 */
const char *turnip_load(const char *path);

/** A dlopen handle of this library (whose vkGetInstanceProcAddr is Turnip's), for $VULKAN_PTR. */
void *turnip_handle(void);

/** This library's vkGetInstanceProcAddr (leading to Turnip once loaded). */
PFN_vkGetInstanceProcAddr turnip_gipa(void);

/**
 * The first Vulkan device's vendor ID (0 if none) and name, through a vkGetInstanceProcAddr.
 * If err is given, it gets what failed.
 */
uint32_t vk_first_device(PFN_vkGetInstanceProcAddr gipa, char *name, size_t nameLen, uint32_t *apiVersion,
                         char *err, size_t errLen);
