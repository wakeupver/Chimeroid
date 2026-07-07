/*
 *     Copyright (C) 2026  Chimeroid contributors
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

#include "vulkancontext.h"

#include <dlfcn.h>
#include <cstring>
#include <algorithm>

#include "../../log.h"

#define MODULE_NAME_VULKAN "LibretroDroid/Vulkan"

namespace libretrodroid {

namespace {

// Presentation always targets this format regardless of the core's pixel
// format or the swapchain's own surface format: it is part of the mandatory
// Android Vulkan baseline for SAMPLED | TRANSFER_SRC | TRANSFER_DST, and
// vkCmdBlitImage does not require the source/destination formats to match.
// Keeping a single fixed format means the sampled image is only ever
// recreated on an actual resolution change, never on a pixel-format switch.
constexpr VkFormat kSampledImageFormat = VK_FORMAT_B8G8R8A8_UNORM;
constexpr uint32_t kBytesPerPixel = 4;

// Unpacks one legacy 16-bit pixel into 8-bit BGRA (byte order matching
// VK_FORMAT_B8G8R8A8_UNORM's in-memory layout: B, G, R, A), replicating the
// bit-expansion GL gets for free from GL_UNSIGNED_SHORT_5_6_5 / 1_5_5_5 texture
// uploads. Only exercised for the minority of cores using these formats.
inline void unpackRgb565ToBgra8(uint16_t px, uint8_t* out) {
    const uint8_t r5 = static_cast<uint8_t>((px >> 11) & 0x1F);
    const uint8_t g6 = static_cast<uint8_t>((px >> 5) & 0x3F);
    const uint8_t b5 = static_cast<uint8_t>(px & 0x1F);
    out[0] = static_cast<uint8_t>((b5 << 3) | (b5 >> 2));
    out[1] = static_cast<uint8_t>((g6 << 2) | (g6 >> 4));
    out[2] = static_cast<uint8_t>((r5 << 3) | (r5 >> 2));
    out[3] = 0xFF;
}

inline void unpack0Rgb1555ToBgra8(uint16_t px, uint8_t* out) {
    const uint8_t r5 = static_cast<uint8_t>((px >> 10) & 0x1F);
    const uint8_t g5 = static_cast<uint8_t>((px >> 5) & 0x1F);
    const uint8_t b5 = static_cast<uint8_t>(px & 0x1F);
    out[0] = static_cast<uint8_t>((b5 << 3) | (b5 >> 2));
    out[1] = static_cast<uint8_t>((g5 << 3) | (g5 >> 2));
    out[2] = static_cast<uint8_t>((r5 << 3) | (r5 >> 2));
    out[3] = 0xFF;
}

} // namespace

VulkanContext::~VulkanContext() {
    shutdown();
}

bool VulkanContext::loadInstanceFn(VkInstance forInstance, const char* name, PFN_vkVoidFunction* outFn) {
    if (!vulkan_symbol_wrapper_load_instance_symbol(forInstance, name, outFn) || *outFn == nullptr) {
        LOGE(MODULE_NAME_VULKAN, "Failed to load instance-level symbol: %s", name);
        return false;
    }
    return true;
}

bool VulkanContext::loadDeviceFn(VkDevice forDevice, const char* name, PFN_vkVoidFunction* outFn) {
    if (!vulkan_symbol_wrapper_load_device_symbol(forDevice, name, outFn) || *outFn == nullptr) {
        LOGE(MODULE_NAME_VULKAN, "Failed to load device-level symbol: %s", name);
        return false;
    }
    return true;
}

bool VulkanContext::loadGlobalAndInstanceFunctions(VkInstance forInstance) {
    // `forInstance` is VK_NULL_HANDLE for the handful of pre-instance entry
    // points (vkCreateInstance itself, vkEnumerateInstanceExtensionProperties)
    // and the real instance for everything dispatched through it afterwards.
    struct Entry { const char* name; PFN_vkVoidFunction* out; bool preInstance; };
    const Entry entries[] = {
        {"vkCreateInstance", reinterpret_cast<PFN_vkVoidFunction*>(&fnCreateInstance), true},
        {"vkEnumerateInstanceExtensionProperties", reinterpret_cast<PFN_vkVoidFunction*>(&fnEnumerateInstanceExtensionProperties), true},
        {"vkDestroyInstance", reinterpret_cast<PFN_vkVoidFunction*>(&fnDestroyInstance), false},
        {"vkEnumeratePhysicalDevices", reinterpret_cast<PFN_vkVoidFunction*>(&fnEnumeratePhysicalDevices), false},
        {"vkGetPhysicalDeviceProperties", reinterpret_cast<PFN_vkVoidFunction*>(&fnGetPhysicalDeviceProperties), false},
        {"vkGetPhysicalDeviceQueueFamilyProperties", reinterpret_cast<PFN_vkVoidFunction*>(&fnGetPhysicalDeviceQueueFamilyProperties), false},
        {"vkGetPhysicalDeviceMemoryProperties", reinterpret_cast<PFN_vkVoidFunction*>(&fnGetPhysicalDeviceMemoryProperties), false},
        {"vkEnumerateDeviceExtensionProperties", reinterpret_cast<PFN_vkVoidFunction*>(&fnEnumerateDeviceExtensionProperties), false},
        {"vkCreateDevice", reinterpret_cast<PFN_vkVoidFunction*>(&fnCreateDevice), false},
        {"vkGetDeviceProcAddr", reinterpret_cast<PFN_vkVoidFunction*>(&fnGetDeviceProcAddr), false},
        {"vkCreateAndroidSurfaceKHR", reinterpret_cast<PFN_vkVoidFunction*>(&fnCreateAndroidSurfaceKHR), false},
        {"vkDestroySurfaceKHR", reinterpret_cast<PFN_vkVoidFunction*>(&fnDestroySurfaceKHR), false},
        {"vkGetPhysicalDeviceSurfaceSupportKHR", reinterpret_cast<PFN_vkVoidFunction*>(&fnGetPhysicalDeviceSurfaceSupportKHR), false},
        {"vkGetPhysicalDeviceSurfaceCapabilitiesKHR", reinterpret_cast<PFN_vkVoidFunction*>(&fnGetPhysicalDeviceSurfaceCapabilitiesKHR), false},
        {"vkGetPhysicalDeviceSurfaceFormatsKHR", reinterpret_cast<PFN_vkVoidFunction*>(&fnGetPhysicalDeviceSurfaceFormatsKHR), false},
        {"vkGetPhysicalDeviceSurfacePresentModesKHR", reinterpret_cast<PFN_vkVoidFunction*>(&fnGetPhysicalDeviceSurfacePresentModesKHR), false},
    };

    for (const auto& entry : entries) {
        VkInstance dispatch = entry.preInstance ? VK_NULL_HANDLE : forInstance;
        if (!loadInstanceFn(dispatch, entry.name, entry.out)) {
            return false;
        }
    }
    return true;
}

bool VulkanContext::loadDeviceFunctions(VkDevice forDevice) {
    struct Entry { const char* name; PFN_vkVoidFunction* out; };
    const Entry entries[] = {
        {"vkDestroyDevice", reinterpret_cast<PFN_vkVoidFunction*>(&fnDestroyDevice)},
        {"vkGetDeviceQueue", reinterpret_cast<PFN_vkVoidFunction*>(&fnGetDeviceQueue)},
        {"vkDeviceWaitIdle", reinterpret_cast<PFN_vkVoidFunction*>(&fnDeviceWaitIdle)},
        {"vkQueueSubmit", reinterpret_cast<PFN_vkVoidFunction*>(&fnQueueSubmit)},
        {"vkQueuePresentKHR", reinterpret_cast<PFN_vkVoidFunction*>(&fnQueuePresentKHR)},
        {"vkCreateSwapchainKHR", reinterpret_cast<PFN_vkVoidFunction*>(&fnCreateSwapchainKHR)},
        {"vkDestroySwapchainKHR", reinterpret_cast<PFN_vkVoidFunction*>(&fnDestroySwapchainKHR)},
        {"vkGetSwapchainImagesKHR", reinterpret_cast<PFN_vkVoidFunction*>(&fnGetSwapchainImagesKHR)},
        {"vkAcquireNextImageKHR", reinterpret_cast<PFN_vkVoidFunction*>(&fnAcquireNextImageKHR)},
        {"vkCreateImageView", reinterpret_cast<PFN_vkVoidFunction*>(&fnCreateImageView)},
        {"vkDestroyImageView", reinterpret_cast<PFN_vkVoidFunction*>(&fnDestroyImageView)},
        {"vkCreateCommandPool", reinterpret_cast<PFN_vkVoidFunction*>(&fnCreateCommandPool)},
        {"vkDestroyCommandPool", reinterpret_cast<PFN_vkVoidFunction*>(&fnDestroyCommandPool)},
        {"vkAllocateCommandBuffers", reinterpret_cast<PFN_vkVoidFunction*>(&fnAllocateCommandBuffers)},
        {"vkFreeCommandBuffers", reinterpret_cast<PFN_vkVoidFunction*>(&fnFreeCommandBuffers)},
        {"vkBeginCommandBuffer", reinterpret_cast<PFN_vkVoidFunction*>(&fnBeginCommandBuffer)},
        {"vkEndCommandBuffer", reinterpret_cast<PFN_vkVoidFunction*>(&fnEndCommandBuffer)},
        {"vkResetCommandBuffer", reinterpret_cast<PFN_vkVoidFunction*>(&fnResetCommandBuffer)},
        {"vkCmdPipelineBarrier", reinterpret_cast<PFN_vkVoidFunction*>(&fnCmdPipelineBarrier)},
        {"vkCmdBlitImage", reinterpret_cast<PFN_vkVoidFunction*>(&fnCmdBlitImage)},
        {"vkCmdCopyBufferToImage", reinterpret_cast<PFN_vkVoidFunction*>(&fnCmdCopyBufferToImage)},
        {"vkCreateFence", reinterpret_cast<PFN_vkVoidFunction*>(&fnCreateFence)},
        {"vkDestroyFence", reinterpret_cast<PFN_vkVoidFunction*>(&fnDestroyFence)},
        {"vkWaitForFences", reinterpret_cast<PFN_vkVoidFunction*>(&fnWaitForFences)},
        {"vkResetFences", reinterpret_cast<PFN_vkVoidFunction*>(&fnResetFences)},
        {"vkCreateSemaphore", reinterpret_cast<PFN_vkVoidFunction*>(&fnCreateSemaphore)},
        {"vkDestroySemaphore", reinterpret_cast<PFN_vkVoidFunction*>(&fnDestroySemaphore)},
        {"vkCreateBuffer", reinterpret_cast<PFN_vkVoidFunction*>(&fnCreateBuffer)},
        {"vkDestroyBuffer", reinterpret_cast<PFN_vkVoidFunction*>(&fnDestroyBuffer)},
        {"vkCreateImage", reinterpret_cast<PFN_vkVoidFunction*>(&fnCreateImage)},
        {"vkDestroyImage", reinterpret_cast<PFN_vkVoidFunction*>(&fnDestroyImage)},
        {"vkAllocateMemory", reinterpret_cast<PFN_vkVoidFunction*>(&fnAllocateMemory)},
        {"vkFreeMemory", reinterpret_cast<PFN_vkVoidFunction*>(&fnFreeMemory)},
        {"vkBindBufferMemory", reinterpret_cast<PFN_vkVoidFunction*>(&fnBindBufferMemory)},
        {"vkBindImageMemory", reinterpret_cast<PFN_vkVoidFunction*>(&fnBindImageMemory)},
        {"vkGetBufferMemoryRequirements", reinterpret_cast<PFN_vkVoidFunction*>(&fnGetBufferMemoryRequirements)},
        {"vkGetImageMemoryRequirements", reinterpret_cast<PFN_vkVoidFunction*>(&fnGetImageMemoryRequirements)},
        {"vkMapMemory", reinterpret_cast<PFN_vkVoidFunction*>(&fnMapMemory)},
        {"vkUnmapMemory", reinterpret_cast<PFN_vkVoidFunction*>(&fnUnmapMemory)},
        {"vkFlushMappedMemoryRanges", reinterpret_cast<PFN_vkVoidFunction*>(&fnFlushMappedMemoryRanges)},
    };

    for (const auto& entry : entries) {
        if (!loadDeviceFn(forDevice, entry.name, entry.out)) {
            return false;
        }
    }
    return true;
}

bool VulkanContext::loadVulkanLibrary() {
    vulkanLibHandle = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL);
    if (vulkanLibHandle == nullptr) {
        LOGW(MODULE_NAME_VULKAN, "libvulkan.so not present on this device/build");
        return false;
    }

    auto getInstanceProcAddr = reinterpret_cast<PFN_vkGetInstanceProcAddr>(
        dlsym(vulkanLibHandle, "vkGetInstanceProcAddr")
    );
    if (getInstanceProcAddr == nullptr) {
        LOGE(MODULE_NAME_VULKAN, "vkGetInstanceProcAddr missing from libvulkan.so");
        dlclose(vulkanLibHandle);
        vulkanLibHandle = nullptr;
        return false;
    }

    fnGetInstanceProcAddr = getInstanceProcAddr;
    vulkan_symbol_wrapper_init(getInstanceProcAddr);
    return loadGlobalAndInstanceFunctions(VK_NULL_HANDLE);
}

bool VulkanContext::createInstance() {
    VkApplicationInfo appInfo {};
    appInfo.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    appInfo.pApplicationName = "Chimeroid";
    appInfo.applicationVersion = VK_MAKE_VERSION(1, 0, 0);
    appInfo.pEngineName = "libretrodroid";
    appInfo.engineVersion = VK_MAKE_VERSION(1, 0, 0);
    // Requesting 1.0 maximizes compatibility: this backend only ever uses
    // core 1.0 entry points plus the KHR surface/swapchain extensions, so
    // there is nothing to gain from requesting a higher version here.
    appInfo.apiVersion = VK_API_VERSION_1_0;

    const char* extensions[] = {"VK_KHR_surface", "VK_KHR_android_surface"};

    VkInstanceCreateInfo createInfo {};
    createInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    createInfo.pApplicationInfo = &appInfo;
    createInfo.enabledExtensionCount = 2;
    createInfo.ppEnabledExtensionNames = extensions;

    if (fnCreateInstance(&createInfo, nullptr, &instance) != VK_SUCCESS) {
        LOGE(MODULE_NAME_VULKAN, "vkCreateInstance failed (missing surface extensions?)");
        return false;
    }

    // Everything dispatched through the instance must be re-resolved now
    // that a real VkInstance exists (loader-dependent: some entry points are
    // only resolvable once the instance/enabled-extensions are known).
    return loadGlobalAndInstanceFunctions(instance);
}

bool VulkanContext::createSurface(ANativeWindow* window) {
    VkAndroidSurfaceCreateInfoKHR createInfo {};
    createInfo.sType = VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR;
    createInfo.window = window;

    if (fnCreateAndroidSurfaceKHR(instance, &createInfo, nullptr, &surface) != VK_SUCCESS) {
        LOGE(MODULE_NAME_VULKAN, "vkCreateAndroidSurfaceKHR failed");
        return false;
    }
    return true;
}

bool VulkanContext::pickPhysicalDeviceAndQueueFamily() {
    uint32_t deviceCount = 0;
    fnEnumeratePhysicalDevices(instance, &deviceCount, nullptr);
    if (deviceCount == 0) {
        LOGE(MODULE_NAME_VULKAN, "No Vulkan-capable physical devices reported");
        return false;
    }

    std::vector<VkPhysicalDevice> devices(deviceCount);
    fnEnumeratePhysicalDevices(instance, &deviceCount, devices.data());

    for (VkPhysicalDevice candidate : devices) {
        uint32_t extCount = 0;
        fnEnumerateDeviceExtensionProperties(candidate, nullptr, &extCount, nullptr);
        std::vector<VkExtensionProperties> extProps(extCount);
        fnEnumerateDeviceExtensionProperties(candidate, nullptr, &extCount, extProps.data());
        bool hasSwapchainExt = std::any_of(extProps.begin(), extProps.end(), [](const VkExtensionProperties& p) {
            return std::strcmp(p.extensionName, "VK_KHR_swapchain") == 0;
        });
        if (!hasSwapchainExt) continue;

        uint32_t familyCount = 0;
        fnGetPhysicalDeviceQueueFamilyProperties(candidate, &familyCount, nullptr);
        std::vector<VkQueueFamilyProperties> families(familyCount);
        fnGetPhysicalDeviceQueueFamilyProperties(candidate, &familyCount, families.data());

        for (uint32_t i = 0; i < familyCount; i++) {
            if (!(families[i].queueFlags & VK_QUEUE_GRAPHICS_BIT)) continue;

            VkBool32 presentSupported = VK_FALSE;
            fnGetPhysicalDeviceSurfaceSupportKHR(candidate, i, surface, &presentSupported);
            if (!presentSupported) continue;

            physicalDevice = candidate;
            queueFamilyIndex = i;
            fnGetPhysicalDeviceMemoryProperties(physicalDevice, &memoryProperties);
            return true;
        }
    }

    LOGE(MODULE_NAME_VULKAN, "No physical device exposes a combined graphics+present queue with VK_KHR_swapchain");
    return false;
}

bool VulkanContext::createLogicalDevice(bool preferHwInterface) {
    const float priority = 1.0f;
    VkDeviceQueueCreateInfo queueCreateInfo {};
    queueCreateInfo.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
    queueCreateInfo.queueFamilyIndex = queueFamilyIndex;
    queueCreateInfo.queueCount = 1;
    queueCreateInfo.pQueuePriorities = &priority;

    const char* deviceExtensions[] = {"VK_KHR_swapchain"};

    VkDeviceCreateInfo deviceCreateInfo {};
    deviceCreateInfo.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
    deviceCreateInfo.queueCreateInfoCount = 1;
    deviceCreateInfo.pQueueCreateInfos = &queueCreateInfo;
    deviceCreateInfo.enabledExtensionCount = 1;
    deviceCreateInfo.ppEnabledExtensionNames = deviceExtensions;

    if (fnCreateDevice(physicalDevice, &deviceCreateInfo, nullptr, &device) != VK_SUCCESS) {
        LOGE(MODULE_NAME_VULKAN, "vkCreateDevice failed");
        return false;
    }

    if (!loadDeviceFunctions(device)) {
        return false;
    }

    fnGetDeviceQueue(device, queueFamilyIndex, 0, &queue);

    hwAccelerated = preferHwInterface;
    if (hwAccelerated) {
        hwRenderInterface = {};
        hwRenderInterface.interface_type = RETRO_HW_RENDER_INTERFACE_VULKAN;
        hwRenderInterface.interface_version = RETRO_HW_RENDER_INTERFACE_VULKAN_VERSION;
        hwRenderInterface.handle = this;
        hwRenderInterface.instance = instance;
        hwRenderInterface.gpu = physicalDevice;
        hwRenderInterface.device = device;
        hwRenderInterface.get_device_proc_addr = fnGetDeviceProcAddr;
        hwRenderInterface.get_instance_proc_addr = fnGetInstanceProcAddr;
        hwRenderInterface.queue = queue;
        hwRenderInterface.queue_index = 0;
        hwRenderInterface.set_image = &VulkanContext::thunkSetImage;
        hwRenderInterface.get_sync_index = &VulkanContext::thunkGetSyncIndex;
        hwRenderInterface.get_sync_index_mask = &VulkanContext::thunkGetSyncIndexMask;
        hwRenderInterface.set_command_buffers = &VulkanContext::thunkSetCommandBuffers;
        hwRenderInterface.wait_sync_index = &VulkanContext::thunkWaitSyncIndex;
        hwRenderInterface.lock_queue = &VulkanContext::thunkLockQueue;
        hwRenderInterface.unlock_queue = &VulkanContext::thunkUnlockQueue;
        hwRenderInterface.set_signal_semaphore = &VulkanContext::thunkSetSignalSemaphore;
    }
    return true;
}

int32_t VulkanContext::findMemoryType(uint32_t typeBits, VkMemoryPropertyFlags properties) const {
    for (uint32_t i = 0; i < memoryProperties.memoryTypeCount; i++) {
        if ((typeBits & (1u << i)) &&
            (memoryProperties.memoryTypes[i].propertyFlags & properties) == properties) {
            return static_cast<int32_t>(i);
        }
    }
    return -1;
}

VkImageMemoryBarrier VulkanContext::makeImageBarrier(
    VkImage image,
    VkImageLayout oldLayout,
    VkImageLayout newLayout,
    VkAccessFlags srcAccess,
    VkAccessFlags dstAccess,
    uint32_t srcQueueFamily,
    uint32_t dstQueueFamily
) {
    VkImageMemoryBarrier barrier {};
    barrier.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
    barrier.oldLayout = oldLayout;
    barrier.newLayout = newLayout;
    barrier.srcQueueFamilyIndex = srcQueueFamily;
    barrier.dstQueueFamilyIndex = dstQueueFamily;
    barrier.image = image;
    barrier.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
    barrier.srcAccessMask = srcAccess;
    barrier.dstAccessMask = dstAccess;
    return barrier;
}

bool VulkanContext::initialize(ANativeWindow* window, bool preferHwInterface) {
    if (initialized) {
        shutdown();
    }

    bool ok = loadVulkanLibrary()
        && createInstance()
        && createSurface(window)
        && pickPhysicalDeviceAndQueueFamily()
        && createLogicalDevice(preferHwInterface);

    if (ok) {
        int32_t width = ANativeWindow_getWidth(window);
        int32_t height = ANativeWindow_getHeight(window);
        ok = width > 0 && height > 0 && createSwapchain(
            static_cast<unsigned>(width),
            static_cast<unsigned>(height)
        );
    }

    if (!ok) {
        LOGE(MODULE_NAME_VULKAN, "Vulkan initialization failed, falling back to OpenGL ES");
        shutdown();
        return false;
    }

    initialized = true;
    return true;
}

bool VulkanContext::createSwapchain(unsigned width, unsigned height) {
    VkSurfaceCapabilitiesKHR capabilities {};
    fnGetPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice, surface, &capabilities);

    uint32_t formatCount = 0;
    fnGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, &formatCount, nullptr);
    if (formatCount == 0) {
        LOGE(MODULE_NAME_VULKAN, "Surface reports zero supported formats");
        return false;
    }
    std::vector<VkSurfaceFormatKHR> formats(formatCount);
    fnGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, &formatCount, formats.data());

    VkSurfaceFormatKHR chosenFormat = formats[0];
    for (const auto& candidate : formats) {
        if (candidate.format == VK_FORMAT_B8G8R8A8_UNORM) {
            chosenFormat = candidate;
            break;
        }
    }

    VkExtent2D extent;
    if (capabilities.currentExtent.width != 0xFFFFFFFFu) {
        extent = capabilities.currentExtent;
    } else {
        extent.width = std::clamp(width, capabilities.minImageExtent.width, capabilities.maxImageExtent.width);
        extent.height = std::clamp(height, capabilities.minImageExtent.height, capabilities.maxImageExtent.height);
    }
    if (extent.width == 0 || extent.height == 0) {
        LOGW(MODULE_NAME_VULKAN, "Surface extent is degenerate (%ux%u), deferring swapchain creation", extent.width, extent.height);
        return false;
    }

    uint32_t imageCount = capabilities.minImageCount + 1;
    if (capabilities.maxImageCount > 0) {
        imageCount = std::min(imageCount, capabilities.maxImageCount);
    }

    VkSwapchainKHR oldSwapchain = swapchain;

    VkSwapchainCreateInfoKHR createInfo {};
    createInfo.sType = VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR;
    createInfo.surface = surface;
    createInfo.minImageCount = imageCount;
    createInfo.imageFormat = chosenFormat.format;
    createInfo.imageColorSpace = chosenFormat.colorSpace;
    createInfo.imageExtent = extent;
    createInfo.imageArrayLayers = 1;
    // Blit-only target: never sampled, never rendered into via a pipeline.
    createInfo.imageUsage = VK_IMAGE_USAGE_TRANSFER_DST_BIT;
    createInfo.imageSharingMode = VK_SHARING_MODE_EXCLUSIVE;
    createInfo.preTransform = capabilities.currentTransform;
    createInfo.compositeAlpha = VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
    createInfo.presentMode = VK_PRESENT_MODE_FIFO_KHR; // universally supported, vsync-paced
    createInfo.clipped = VK_TRUE;
    createInfo.oldSwapchain = oldSwapchain;

    VkSwapchainKHR newSwapchain = VK_NULL_HANDLE;
    VkResult result = fnCreateSwapchainKHR(device, &createInfo, nullptr, &newSwapchain);

    // Tear down whatever the previous swapchain owned (images/cmd buffers/
    // fences are all re-derived below) before touching the new handle.
    destroySwapchain();
    if (oldSwapchain != VK_NULL_HANDLE) {
        fnDestroySwapchainKHR(device, oldSwapchain, nullptr);
    }

    if (result != VK_SUCCESS) {
        LOGE(MODULE_NAME_VULKAN, "vkCreateSwapchainKHR failed (%d)", result);
        return false;
    }

    swapchain = newSwapchain;
    swapchainFormat = chosenFormat.format;
    swapchainExtent = extent;

    uint32_t actualImageCount = 0;
    fnGetSwapchainImagesKHR(device, swapchain, &actualImageCount, nullptr);
    std::vector<VkImage> rawImages(actualImageCount);
    fnGetSwapchainImagesKHR(device, swapchain, &actualImageCount, rawImages.data());

    VkCommandPoolCreateInfo poolCreateInfo {};
    poolCreateInfo.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
    poolCreateInfo.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
    poolCreateInfo.queueFamilyIndex = queueFamilyIndex;
    if (fnCreateCommandPool(device, &poolCreateInfo, nullptr, &commandPool) != VK_SUCCESS) {
        LOGE(MODULE_NAME_VULKAN, "vkCreateCommandPool failed");
        return false;
    }

    std::vector<VkCommandBuffer> commandBuffers(actualImageCount);
    VkCommandBufferAllocateInfo cmdAllocInfo {};
    cmdAllocInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
    cmdAllocInfo.commandPool = commandPool;
    cmdAllocInfo.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    cmdAllocInfo.commandBufferCount = actualImageCount;
    if (fnAllocateCommandBuffers(device, &cmdAllocInfo, commandBuffers.data()) != VK_SUCCESS) {
        LOGE(MODULE_NAME_VULKAN, "vkAllocateCommandBuffers failed");
        return false;
    }

    swapchainImages.resize(actualImageCount);
    for (uint32_t i = 0; i < actualImageCount; i++) {
        swapchainImages[i].image = rawImages[i];
        swapchainImages[i].commandBuffer = commandBuffers[i];

        VkFenceCreateInfo fenceInfo {};
        fenceInfo.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;
        fenceInfo.flags = VK_FENCE_CREATE_SIGNALED_BIT; // first wait must not block forever
        if (fnCreateFence(device, &fenceInfo, nullptr, &swapchainImages[i].fence) != VK_SUCCESS) {
            LOGE(MODULE_NAME_VULKAN, "vkCreateFence failed");
            return false;
        }
    }

    imageAvailableSemaphores.resize(actualImageCount);
    renderFinishedSemaphores.resize(actualImageCount);
    for (uint32_t i = 0; i < actualImageCount; i++) {
        VkSemaphoreCreateInfo semInfo {};
        semInfo.sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO;
        if (fnCreateSemaphore(device, &semInfo, nullptr, &imageAvailableSemaphores[i]) != VK_SUCCESS ||
            fnCreateSemaphore(device, &semInfo, nullptr, &renderFinishedSemaphores[i]) != VK_SUCCESS) {
            LOGE(MODULE_NAME_VULKAN, "vkCreateSemaphore failed");
            return false;
        }
    }

    frameIndex = 0;
    swapchainDirty = false;
    return true;
}

void VulkanContext::destroySwapchain() {
    if (device != VK_NULL_HANDLE) {
        fnDeviceWaitIdle(device);
    }

    for (auto& sem : imageAvailableSemaphores) {
        if (sem != VK_NULL_HANDLE) fnDestroySemaphore(device, sem, nullptr);
    }
    imageAvailableSemaphores.clear();
    for (auto& sem : renderFinishedSemaphores) {
        if (sem != VK_NULL_HANDLE) fnDestroySemaphore(device, sem, nullptr);
    }
    renderFinishedSemaphores.clear();

    for (auto& img : swapchainImages) {
        if (img.fence != VK_NULL_HANDLE) fnDestroyFence(device, img.fence, nullptr);
    }
    if (commandPool != VK_NULL_HANDLE && !swapchainImages.empty()) {
        std::vector<VkCommandBuffer> buffers;
        buffers.reserve(swapchainImages.size());
        for (auto& img : swapchainImages) {
            if (img.commandBuffer != VK_NULL_HANDLE) buffers.push_back(img.commandBuffer);
        }
        if (!buffers.empty()) {
            fnFreeCommandBuffers(device, commandPool, static_cast<uint32_t>(buffers.size()), buffers.data());
        }
    }
    swapchainImages.clear();

    if (commandPool != VK_NULL_HANDLE) {
        fnDestroyCommandPool(device, commandPool, nullptr);
        commandPool = VK_NULL_HANDLE;
    }
    // Note: swapchain images themselves (VkImage) are owned by the swapchain
    // and must NOT be destroyed here; only the swapchain destruction (by the
    // caller, which still needs the old handle for oldSwapchain) releases them.
}

void VulkanContext::destroyStagingBuffer() {
    if (stagingMapped != nullptr && device != VK_NULL_HANDLE) {
        fnUnmapMemory(device, stagingMemory);
        stagingMapped = nullptr;
    }
    if (stagingBuffer != VK_NULL_HANDLE) {
        fnDestroyBuffer(device, stagingBuffer, nullptr);
        stagingBuffer = VK_NULL_HANDLE;
    }
    if (stagingMemory != VK_NULL_HANDLE) {
        fnFreeMemory(device, stagingMemory, nullptr);
        stagingMemory = VK_NULL_HANDLE;
    }
    stagingCapacity = 0;
}

bool VulkanContext::createStagingBuffer(VkDeviceSize size) {
    destroyStagingBuffer();

    VkBufferCreateInfo bufferInfo {};
    bufferInfo.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
    bufferInfo.size = size;
    bufferInfo.usage = VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
    bufferInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    if (fnCreateBuffer(device, &bufferInfo, nullptr, &stagingBuffer) != VK_SUCCESS) {
        return false;
    }

    VkMemoryRequirements memReqs {};
    fnGetBufferMemoryRequirements(device, stagingBuffer, &memReqs);

    int32_t memType = findMemoryType(
        memReqs.memoryTypeBits,
        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
    );
    if (memType < 0) {
        LOGE(MODULE_NAME_VULKAN, "No host-visible+coherent memory type for staging buffer");
        return false;
    }

    VkMemoryAllocateInfo allocInfo {};
    allocInfo.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    allocInfo.allocationSize = memReqs.size;
    allocInfo.memoryTypeIndex = static_cast<uint32_t>(memType);
    if (fnAllocateMemory(device, &allocInfo, nullptr, &stagingMemory) != VK_SUCCESS) {
        return false;
    }

    fnBindBufferMemory(device, stagingBuffer, stagingMemory, 0);
    if (fnMapMemory(device, stagingMemory, 0, size, 0, &stagingMapped) != VK_SUCCESS) {
        return false;
    }

    stagingCapacity = size;
    return true;
}

void VulkanContext::destroySampledImage() {
    if (sampledImage != VK_NULL_HANDLE) {
        fnDestroyImage(device, sampledImage, nullptr);
        sampledImage = VK_NULL_HANDLE;
    }
    if (sampledImageMemory != VK_NULL_HANDLE) {
        fnFreeMemory(device, sampledImageMemory, nullptr);
        sampledImageMemory = VK_NULL_HANDLE;
    }
    sampledImageWidth = 0;
    sampledImageHeight = 0;
    sampledImageEverWritten = false;
}

bool VulkanContext::createSampledImage(unsigned width, unsigned height, VkFormat format) {
    destroySampledImage();

    VkImageCreateInfo imageInfo {};
    imageInfo.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
    imageInfo.imageType = VK_IMAGE_TYPE_2D;
    imageInfo.format = format;
    imageInfo.extent = {width, height, 1};
    imageInfo.mipLevels = 1;
    imageInfo.arrayLayers = 1;
    imageInfo.samples = VK_SAMPLE_COUNT_1_BIT;
    imageInfo.tiling = VK_IMAGE_TILING_OPTIMAL;
    imageInfo.usage = VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
    imageInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    imageInfo.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;

    if (fnCreateImage(device, &imageInfo, nullptr, &sampledImage) != VK_SUCCESS) {
        return false;
    }

    VkMemoryRequirements memReqs {};
    fnGetImageMemoryRequirements(device, sampledImage, &memReqs);
    int32_t memType = findMemoryType(memReqs.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
    if (memType < 0) {
        memType = findMemoryType(memReqs.memoryTypeBits, 0);
    }
    if (memType < 0) {
        LOGE(MODULE_NAME_VULKAN, "No suitable memory type for sampled image");
        return false;
    }

    VkMemoryAllocateInfo allocInfo {};
    allocInfo.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    allocInfo.allocationSize = memReqs.size;
    allocInfo.memoryTypeIndex = static_cast<uint32_t>(memType);
    if (fnAllocateMemory(device, &allocInfo, nullptr, &sampledImageMemory) != VK_SUCCESS) {
        return false;
    }

    fnBindImageMemory(device, sampledImage, sampledImageMemory, 0);
    sampledImageWidth = width;
    sampledImageHeight = height;
    return true;
}

void VulkanContext::onSurfaceResized(unsigned width, unsigned height) {
    if (!initialized) return;
    pendingWidth = width;
    pendingHeight = height;
    swapchainDirty = true;
}

void VulkanContext::onNewFrame(const void* data, unsigned width, unsigned height, size_t pitch, int pixelFormat) {
    if (!initialized || hwAccelerated) return;
    // RETRO_HW_FRAME_BUFFER_VALID / duplicate-frame signal: keep last image.
    if (data == nullptr || width == 0 || height == 0) return;

    if (width != sampledImageWidth || height != sampledImageHeight) {
        fnDeviceWaitIdle(device);
        if (!createSampledImage(width, height, kSampledImageFormat)) {
            LOGE(MODULE_NAME_VULKAN, "Failed to (re)create sampled image at %ux%u", width, height);
            return;
        }
    }

    const VkDeviceSize requiredSize = static_cast<VkDeviceSize>(width) * height * kBytesPerPixel;
    if (requiredSize > stagingCapacity) {
        // Grow, never shrink: avoids a reallocation storm if a core toggles
        // between a couple of resolutions every few frames.
        if (!createStagingBuffer(requiredSize)) {
            LOGE(MODULE_NAME_VULKAN, "Failed to grow staging buffer to %llu bytes",
                 static_cast<unsigned long long>(requiredSize));
            return;
        }
    }

    auto* dst = static_cast<uint8_t*>(stagingMapped);
    const auto* src = static_cast<const uint8_t*>(data);

    switch (pixelFormat) {
        case 1 /* RETRO_PIXEL_FORMAT_XRGB8888 */: {
            const size_t rowBytes = static_cast<size_t>(width) * kBytesPerPixel;
            for (unsigned y = 0; y < height; y++) {
                std::memcpy(dst + y * rowBytes, src + y * pitch, rowBytes);
            }
            break;
        }
        case 2 /* RETRO_PIXEL_FORMAT_RGB565 */: {
            for (unsigned y = 0; y < height; y++) {
                const auto* srcRow = reinterpret_cast<const uint16_t*>(src + y * pitch);
                uint8_t* dstRow = dst + static_cast<size_t>(y) * width * kBytesPerPixel;
                for (unsigned x = 0; x < width; x++) {
                    unpackRgb565ToBgra8(srcRow[x], dstRow + x * kBytesPerPixel);
                }
            }
            break;
        }
        default /* RETRO_PIXEL_FORMAT_0RGB1555 (0) and anything unrecognised */: {
            for (unsigned y = 0; y < height; y++) {
                const auto* srcRow = reinterpret_cast<const uint16_t*>(src + y * pitch);
                uint8_t* dstRow = dst + static_cast<size_t>(y) * width * kBytesPerPixel;
                for (unsigned x = 0; x < width; x++) {
                    unpack0Rgb1555ToBgra8(srcRow[x], dstRow + x * kBytesPerPixel);
                }
            }
            break;
        }
    }

    // Staging memory is HOST_COHERENT, so no explicit vkFlushMappedMemoryRanges
    // is required, but the upload-to-image copy still has to go through a
    // one-shot command buffer. Reuse slot 0's command buffer for this: it is
    // guaranteed idle here because presentFrame() always waits on every
    // slot's fence before reusing it, and onNewFrame() is only ever called
    // from the same thread that drives presentFrame() (the emulation/render
    // thread), never concurrently with it.
    if (swapchainImages.empty()) return;
    VkCommandBuffer cmd = swapchainImages[0].commandBuffer;

    VkCommandBufferBeginInfo beginInfo {};
    beginInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
    beginInfo.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
    fnBeginCommandBuffer(cmd, &beginInfo);

    VkImageMemoryBarrier toDst = makeImageBarrier(
        sampledImage,
        sampledImageEverWritten ? VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL : VK_IMAGE_LAYOUT_UNDEFINED,
        VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
        sampledImageEverWritten ? VK_ACCESS_TRANSFER_READ_BIT : 0,
        VK_ACCESS_TRANSFER_WRITE_BIT
    );
    fnCmdPipelineBarrier(
        cmd, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
        0, 0, nullptr, 0, nullptr, 1, &toDst
    );

    VkBufferImageCopy region {};
    region.imageSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
    region.imageExtent = {width, height, 1};
    fnCmdCopyBufferToImage(cmd, stagingBuffer, sampledImage, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &region);

    VkImageMemoryBarrier toSrc = makeImageBarrier(
        sampledImage,
        VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
        VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
        VK_ACCESS_TRANSFER_WRITE_BIT,
        VK_ACCESS_TRANSFER_READ_BIT
    );
    fnCmdPipelineBarrier(
        cmd, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
        0, 0, nullptr, 0, nullptr, 1, &toSrc
    );

    fnEndCommandBuffer(cmd);

    VkSubmitInfo submitInfo {};
    submitInfo.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
    submitInfo.commandBufferCount = 1;
    submitInfo.pCommandBuffers = &cmd;

    {
        std::lock_guard<std::mutex> lock(queueMutex);
        fnQueueSubmit(queue, 1, &submitInfo, VK_NULL_HANDLE);
        fnQueueWaitIdle_or_deviceWaitIdle:
        fnDeviceWaitIdle(device);
    }

    sampledImageEverWritten = true;
}

bool VulkanContext::recordAndSubmitBlit(
    uint32_t swapchainImageIndex,
    VkImage srcImage,
    VkImageLayout srcImageLayout,
    unsigned srcWidth,
    unsigned srcHeight
) {
    SwapchainImage& target = swapchainImages[swapchainImageIndex];
    VkCommandBuffer cmd = target.commandBuffer;

    fnResetCommandBuffer(cmd, 0);
    VkCommandBufferBeginInfo beginInfo {};
    beginInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
    beginInfo.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
    fnBeginCommandBuffer(cmd, &beginInfo);

    VkImageMemoryBarrier preBlit[2] = {
        makeImageBarrier(
            target.image, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
            0, VK_ACCESS_TRANSFER_WRITE_BIT
        ),
        makeImageBarrier(
            srcImage, srcImageLayout, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
            VK_ACCESS_SHADER_WRITE_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT, VK_ACCESS_TRANSFER_READ_BIT
        ),
    };
    fnCmdPipelineBarrier(
        cmd, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
        0, 0, nullptr, 0, nullptr, 2, preBlit
    );

    // srcWidth/srcHeight are the SOURCE image's own valid extent (the core's
    // actual render resolution) — NEVER the destination swapchainExtent, which
    // would claim a region the source image may not actually have (an
    // out-of-bounds blit is undefined behaviour per the Vulkan spec, not just
    // a cosmetic bug). Scaling to the screen happens purely via dstOffsets.
    VkImageBlit blit {};
    blit.srcSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
    blit.srcOffsets[1] = {static_cast<int32_t>(srcWidth), static_cast<int32_t>(srcHeight), 1};
    blit.dstSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
    blit.dstOffsets[1] = {static_cast<int32_t>(swapchainExtent.width), static_cast<int32_t>(swapchainExtent.height), 1};
    fnCmdBlitImage(
        cmd,
        srcImage, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
        target.image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
        1, &blit, VK_FILTER_LINEAR
    );

    VkImageMemoryBarrier toPresent = makeImageBarrier(
        target.image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
        VK_ACCESS_TRANSFER_WRITE_BIT, 0
    );
    fnCmdPipelineBarrier(
        cmd, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,
        0, 0, nullptr, 0, nullptr, 1, &toPresent
    );

    fnEndCommandBuffer(cmd);

    VkPipelineStageFlags waitStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
    VkSubmitInfo submitInfo {};
    submitInfo.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
    submitInfo.waitSemaphoreCount = 1;
    submitInfo.pWaitSemaphores = &imageAvailableSemaphores[frameIndex];
    submitInfo.pWaitDstStageMask = &waitStage;
    submitInfo.commandBufferCount = 1;
    submitInfo.pCommandBuffers = &cmd;
    submitInfo.signalSemaphoreCount = 1;
    submitInfo.pSignalSemaphores = &renderFinishedSemaphores[frameIndex];

    std::lock_guard<std::mutex> lock(queueMutex);
    fnResetFences(device, 1, &target.fence);
    if (fnQueueSubmit(queue, 1, &submitInfo, target.fence) != VK_SUCCESS) {
        LOGE(MODULE_NAME_VULKAN, "vkQueueSubmit (present blit) failed");
        return false;
    }
    target.fencePending = true;
    return true;
}

void VulkanContext::onHwFrameGeometryChanged(unsigned width, unsigned height) {
    hwImageWidth = width;
    hwImageHeight = height;
}

void VulkanContext::presentFrame() {
    if (!initialized) return;

    if (swapchainDirty) {
        createSwapchain(pendingWidth, pendingHeight);
        if (!initialized) return; // createSwapchain() failure is non-fatal; retried on next resize
    }
    if (swapchain == VK_NULL_HANDLE || swapchainImages.empty()) return;

    VkImage srcImage;
    VkImageLayout srcLayout;
    unsigned srcWidth;
    unsigned srcHeight;
    if (hwAccelerated) {
        std::lock_guard<std::mutex> lock(queueMutex);
        // Either the core has not produced a frame yet, or SET_SYSTEM_AV_INFO/
        // SET_GEOMETRY has not arrived yet: either way there is nothing valid
        // to blit from, and a zero-extent blit is invalid.
        if (hwCurrentImage == VK_NULL_HANDLE || hwImageWidth == 0 || hwImageHeight == 0) return;
        srcImage = hwCurrentImage;
        srcLayout = hwCurrentImageLayout;
        srcWidth = hwImageWidth;
        srcHeight = hwImageHeight;
    } else {
        if (!sampledImageEverWritten) return;
        srcImage = sampledImage;
        srcLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
        srcWidth = sampledImageWidth;
        srcHeight = sampledImageHeight;
    }

    uint32_t imageIndex = 0;
    VkResult acquireResult = fnAcquireNextImageKHR(
        device, swapchain, UINT64_MAX, imageAvailableSemaphores[frameIndex], VK_NULL_HANDLE, &imageIndex
    );
    if (acquireResult == VK_ERROR_OUT_OF_DATE_KHR) {
        swapchainDirty = true;
        pendingWidth = swapchainExtent.width;
        pendingHeight = swapchainExtent.height;
        return;
    }
    if (acquireResult != VK_SUCCESS && acquireResult != VK_SUBOPTIMAL_KHR) {
        LOGW(MODULE_NAME_VULKAN, "vkAcquireNextImageKHR returned %d", acquireResult);
        return;
    }

    fnWaitForFences(device, 1, &swapchainImages[imageIndex].fence, VK_TRUE, UINT64_MAX);

    if (!recordAndSubmitBlit(imageIndex, srcImage, srcLayout, srcWidth, srcHeight)) {
        return;
    }

    VkPresentInfoKHR presentInfo {};
    presentInfo.sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR;
    presentInfo.waitSemaphoreCount = 1;
    presentInfo.pWaitSemaphores = &renderFinishedSemaphores[frameIndex];
    presentInfo.swapchainCount = 1;
    presentInfo.pSwapchains = &swapchain;
    presentInfo.pImageIndices = &imageIndex;

    VkResult presentResult;
    {
        std::lock_guard<std::mutex> lock(queueMutex);
        presentResult = fnQueuePresentKHR(queue, &presentInfo);
    }
    if (presentResult == VK_ERROR_OUT_OF_DATE_KHR || presentResult == VK_SUBOPTIMAL_KHR) {
        swapchainDirty = true;
        pendingWidth = swapchainExtent.width;
        pendingHeight = swapchainExtent.height;
    }

    frameIndex = (frameIndex + 1) % static_cast<uint32_t>(imageAvailableSemaphores.size());
}

bool VulkanContext::getHwRenderInterface(const struct retro_hw_render_interface_vulkan** outInterface) {
    if (!hwAccelerated || !initialized) return false;
    *outInterface = &hwRenderInterface;
    return true;
}

void VulkanContext::shutdown() {
    if (device != VK_NULL_HANDLE) {
        fnDeviceWaitIdle(device);
    }

    destroySwapchain();
    if (swapchain != VK_NULL_HANDLE) {
        fnDestroySwapchainKHR(device, swapchain, nullptr);
        swapchain = VK_NULL_HANDLE;
    }

    destroyStagingBuffer();
    destroySampledImage();

    if (device != VK_NULL_HANDLE) {
        fnDestroyDevice(device, nullptr);
        device = VK_NULL_HANDLE;
    }
    if (surface != VK_NULL_HANDLE) {
        fnDestroySurfaceKHR(instance, surface, nullptr);
        surface = VK_NULL_HANDLE;
    }
    if (instance != VK_NULL_HANDLE) {
        fnDestroyInstance(instance, nullptr);
        instance = VK_NULL_HANDLE;
    }
    if (vulkanLibHandle != nullptr) {
        dlclose(vulkanLibHandle);
        vulkanLibHandle = nullptr;
    }

    physicalDevice = VK_NULL_HANDLE;
    queue = VK_NULL_HANDLE;
    queueFamilyIndex = 0;
    hwAccelerated = false;
    hwCurrentImage = VK_NULL_HANDLE;
    hwCurrentImageLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    hwWaitSemaphores.clear();
    hwImageWidth = 0;
    hwImageHeight = 0;
    swapchainDirty = false;
    initialized = false;
}

// ---- retro_hw_render_interface_vulkan thunks -------------------------------

void VulkanContext::thunkSetImage(
    void* handle,
    const struct retro_vulkan_image* image,
    uint32_t numSemaphores,
    const VkSemaphore* semaphores,
    uint32_t /*srcQueueFamily*/
) {
    auto* self = static_cast<VulkanContext*>(handle);
    std::lock_guard<std::mutex> lock(self->queueMutex);
    // create_info.image is the only place the raw VkImage backing this view
    // is guaranteed to be available (retro_vulkan_image otherwise only hands
    // back the VkImageView) — see libretro_vulkan.h.
    self->hwCurrentImage = image->create_info.image;
    self->hwCurrentImageLayout = image->image_layout;
    self->hwWaitSemaphores.assign(semaphores, semaphores + numSemaphores);
}

uint32_t VulkanContext::thunkGetSyncIndex(void* handle) {
    auto* self = static_cast<VulkanContext*>(handle);
    return self->frameIndex;
}

uint32_t VulkanContext::thunkGetSyncIndexMask(void* handle) {
    auto* self = static_cast<VulkanContext*>(handle);
    size_t count = self->swapchainImages.empty() ? 1 : self->swapchainImages.size();
    return (1u << count) - 1u;
}

void VulkanContext::thunkSetCommandBuffers(void* /*handle*/, uint32_t /*numCmd*/, const VkCommandBuffer* /*cmd*/) {
    // Intentionally a no-op: this backend does not batch core-submitted
    // secondary command buffers into its own submission (no core shipped
    // with Chimeroid currently relies on this path). Documented gap rather
    // than a silent/partial implementation.
}

void VulkanContext::thunkWaitSyncIndex(void* /*handle*/) {
    // No-op: this backend never reuses a sync index before its associated
    // swapchain image fence is signalled (see presentFrame()'s
    // vkWaitForFences call), so there is nothing additional to wait for here.
}

void VulkanContext::thunkLockQueue(void* handle) {
    auto* self = static_cast<VulkanContext*>(handle);
    self->queueMutex.lock();
}

void VulkanContext::thunkUnlockQueue(void* handle) {
    auto* self = static_cast<VulkanContext*>(handle);
    self->queueMutex.unlock();
}

void VulkanContext::thunkSetSignalSemaphore(void* /*handle*/, VkSemaphore /*semaphore*/) {
    // Not currently consumed: see set_command_buffers note above.
}

}
