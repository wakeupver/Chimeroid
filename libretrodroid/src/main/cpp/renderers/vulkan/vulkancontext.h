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

#ifndef LIBRETRODROID_VULKANCONTEXT_H
#define LIBRETRODROID_VULKANCONTEXT_H

#define VK_NO_PROTOTYPES
#define VK_USE_PLATFORM_ANDROID_KHR
#include <vulkan/vulkan.h>
#include <vulkan/vulkan_symbol_wrapper.h>

#include <android/native_window.h>

#include <cstdint>
#include <mutex>
#include <vector>

#include "libretro_vulkan.h"

namespace libretrodroid {

// Self-contained Vulkan presentation backend, parallel to (and mutually
// exclusive with) the existing EGL/GLES Video pipeline. A single instance
// handles BOTH of the cases a loaded core can produce:
//
//  - Software-rendered cores (the overwhelming majority): raw pixel buffers
//    arrive via onNewFrame() and are uploaded into a persistently-allocated
//    sampled image, mirroring what Renderer::onNewFrame()/glTexSubImage2D
//    does for the GLES path.
//  - HW-accelerated Vulkan cores (rare, opt-in via RETRO_HW_CONTEXT_VULKAN):
//    the core drives rendering itself against the device/queue exposed via
//    getHwRenderInterface() and hands back a finished VkImageView per frame
//    through the retro_hw_render_interface_vulkan::set_image callback.
//
// Both cases converge on the same swapchain + blit-to-present path, so the
// two upload mechanisms are the only real branch in the class.
//
// Every public entry point that touches the swapchain/queue is safe to call
// only from the single dedicated Vulkan present thread EXCEPT the
// retro_hw_render_interface_vulkan thunks (set_image/lock_queue/unlock_queue/
// etc.), which the loaded core is free to call from its own thread(s); those
// are guarded by queueMutex.
class VulkanContext {
public:
    VulkanContext() = default;
    VulkanContext(const VulkanContext&) = delete;
    VulkanContext& operator=(const VulkanContext&) = delete;
    ~VulkanContext();

    // Brings up instance/device/surface/swapchain against `window`.
    // `preferHwInterface` requests the retro_hw_render_interface_vulkan path;
    // it is only honoured if `hwAccelerated` ends up true after negotiation
    // (the caller decides that from Environment's accepted context type).
    // Returns false, with all partial state already released, on ANY
    // failure — callers must treat false as "fall back to OpenGL ES", never
    // as fatal.
    bool initialize(ANativeWindow* window, bool preferHwInterface);

    // Releases every Vulkan/window resource. Safe to call multiple times and
    // safe to call on a partially-initialized instance.
    void shutdown();

    // Recreates the swapchain at the new size. No-op if not initialized.
    void onSurfaceResized(unsigned width, unsigned height);

    // Software-core path. `data == nullptr` is the libretro duplicate-frame
    // signal (and RETRO_HW_FRAME_BUFFER_VALID must never reach this method —
    // callers route that sentinel around it, since a HW-Vulkan frame is
    // already available via set_image()); both are safe no-ops here.
    void onNewFrame(const void* data, unsigned width, unsigned height, size_t pitch, int pixelFormat);

    // Acquires a swapchain image, blits the current frame (software-uploaded
    // or HW-core-provided) onto it, and presents. No-op if the context is
    // not initialized or the swapchain is temporarily out of date; the next
    // resize will recover it. Never allocates: all command buffers, fences
    // and semaphores are pre-allocated in initialize()/createSwapchain().
    void presentFrame();

    // Populates *outInterface for RETRO_ENVIRONMENT_GET_HW_RENDER_INTERFACE.
    // Returns false (leaving *outInterface untouched) unless this context
    // was initialized with hwAccelerated == true.
    bool getHwRenderInterface(const struct retro_hw_render_interface_vulkan** outInterface);

    // HW-accelerated core path only: the frontend cannot infer the core's
    // current render resolution from the VkImage handed back via set_image()
    // (retro_vulkan_image only describes a view, not an extent), so
    // LibretroDroid pushes it in explicitly whenever SET_SYSTEM_AV_INFO /
    // SET_GEOMETRY fires — mirroring how it already pushes the equivalent
    // size into Video::updateRendererSize() for the GLES path.
    void onHwFrameGeometryChanged(unsigned width, unsigned height);

    bool isHwAccelerated() const { return hwAccelerated; }
    bool isInitialized() const { return initialized; }

private:
    // Presentation is a straight vkCmdBlitImage from the current source image
    // (software-uploaded or HW-core-provided) onto the swapchain image, scaled
    // to the swapchain extent — so, unlike a render-pass/pipeline approach, no
    // VkImageView/VkFramebuffer is needed per swapchain image, only the raw
    // VkImage plus per-image command buffer and fence.
    struct SwapchainImage {
        VkImage image = VK_NULL_HANDLE;
        VkCommandBuffer commandBuffer = VK_NULL_HANDLE;
        VkFence fence = VK_NULL_HANDLE;
        bool fencePending = false;
    };

    bool loadVulkanLibrary();
    bool createInstance();
    bool createSurface(ANativeWindow* window);
    bool pickPhysicalDeviceAndQueueFamily();
    bool createLogicalDevice(bool preferHwInterface);
    bool createSwapchain(unsigned width, unsigned height);
    void destroySwapchain();
    bool createStagingBuffer(VkDeviceSize size);
    void destroyStagingBuffer();
    bool createSampledImage(unsigned width, unsigned height, VkFormat format);
    void destroySampledImage();
    bool recordAndSubmitBlit(uint32_t swapchainImageIndex, VkImage srcImage, VkImageLayout srcImageLayout, unsigned srcWidth, unsigned srcHeight);

    int32_t findMemoryType(uint32_t typeBits, VkMemoryPropertyFlags properties) const;

    // VK_NO_PROTOTYPES is set (Vulkan is dlopen'd, never link-time linked, so
    // APKs still load on devices/builds without a Vulkan driver present).
    // The vendored vulkan_symbol_wrapper only pre-declares a subset of entry
    // points as convenience macros; every function this class calls is
    // therefore resolved explicitly through these helpers instead of being
    // assumed available under its bare name, so a mismatch with whatever the
    // wrapper happens to pre-declare can never silently compile into a wrong
    // or dangling pointer.
    bool loadInstanceFn(VkInstance forInstance, const char* name, PFN_vkVoidFunction* outFn);
    bool loadDeviceFn(VkDevice forDevice, const char* name, PFN_vkVoidFunction* outFn);
    bool loadGlobalAndInstanceFunctions(VkInstance forInstance);
    bool loadDeviceFunctions(VkDevice forDevice);

    static VkImageMemoryBarrier makeImageBarrier(
        VkImage image,
        VkImageLayout oldLayout,
        VkImageLayout newLayout,
        VkAccessFlags srcAccess,
        VkAccessFlags dstAccess,
        uint32_t srcQueueFamily = VK_QUEUE_FAMILY_IGNORED,
        uint32_t dstQueueFamily = VK_QUEUE_FAMILY_IGNORED
    );

    // retro_hw_render_interface_vulkan thunks. `handle` is always `this`.
    static void thunkSetImage(
        void* handle,
        const struct retro_vulkan_image* image,
        uint32_t numSemaphores,
        const VkSemaphore* semaphores,
        uint32_t srcQueueFamily
    );
    static uint32_t thunkGetSyncIndex(void* handle);
    static uint32_t thunkGetSyncIndexMask(void* handle);
    static void thunkSetCommandBuffers(void* handle, uint32_t numCmd, const VkCommandBuffer* cmd);
    static void thunkWaitSyncIndex(void* handle);
    static void thunkLockQueue(void* handle);
    static void thunkUnlockQueue(void* handle);
    static void thunkSetSignalSemaphore(void* handle, VkSemaphore semaphore);

private:
    void* vulkanLibHandle = nullptr;

    VkInstance instance = VK_NULL_HANDLE;
    VkSurfaceKHR surface = VK_NULL_HANDLE;
    VkPhysicalDevice physicalDevice = VK_NULL_HANDLE;
    VkPhysicalDeviceMemoryProperties memoryProperties {};
    VkDevice device = VK_NULL_HANDLE;
    VkQueue queue = VK_NULL_HANDLE;
    uint32_t queueFamilyIndex = 0;

    VkSwapchainKHR swapchain = VK_NULL_HANDLE;
    VkFormat swapchainFormat = VK_FORMAT_UNDEFINED;
    VkExtent2D swapchainExtent {0, 0};
    std::vector<SwapchainImage> swapchainImages;
    VkCommandPool commandPool = VK_NULL_HANDLE;
    std::vector<VkSemaphore> imageAvailableSemaphores;
    std::vector<VkSemaphore> renderFinishedSemaphores;
    uint32_t frameIndex = 0;

    unsigned pendingWidth = 0;
    unsigned pendingHeight = 0;
    bool swapchainDirty = false;

    // Software-core upload path (persistent, resized only on demand).
    VkBuffer stagingBuffer = VK_NULL_HANDLE;
    VkDeviceMemory stagingMemory = VK_NULL_HANDLE;
    void* stagingMapped = nullptr;
    VkDeviceSize stagingCapacity = 0;

    VkImage sampledImage = VK_NULL_HANDLE;
    VkDeviceMemory sampledImageMemory = VK_NULL_HANDLE;
    unsigned sampledImageWidth = 0;
    unsigned sampledImageHeight = 0;
    bool sampledImageEverWritten = false;

    // HW-core (retro_hw_render_interface_vulkan) path.
    bool hwAccelerated = false;
    struct retro_hw_render_interface_vulkan hwRenderInterface {};
    VkImage hwCurrentImage = VK_NULL_HANDLE;
    VkImageLayout hwCurrentImageLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    std::vector<VkSemaphore> hwWaitSemaphores;
    unsigned hwImageWidth = 0;
    unsigned hwImageHeight = 0;

    // Guards vkQueueSubmit/vkQueuePresentKHR plus the HW image handoff
    // fields above. The core may call set_image/lock_queue/unlock_queue
    // from its own thread while presentFrame() runs on the dedicated
    // Vulkan present thread; both must never submit to `queue` concurrently,
    // which is undefined behaviour per the Vulkan spec.
    std::mutex queueMutex;

    bool initialized = false;

    // Explicitly-resolved Vulkan entry points (see loadInstanceFn/loadDeviceFn).
    PFN_vkCreateInstance fnCreateInstance = nullptr;
    PFN_vkEnumerateInstanceExtensionProperties fnEnumerateInstanceExtensionProperties = nullptr;
    PFN_vkDestroyInstance fnDestroyInstance = nullptr;
    PFN_vkEnumeratePhysicalDevices fnEnumeratePhysicalDevices = nullptr;
    PFN_vkGetPhysicalDeviceProperties fnGetPhysicalDeviceProperties = nullptr;
    PFN_vkGetPhysicalDeviceQueueFamilyProperties fnGetPhysicalDeviceQueueFamilyProperties = nullptr;
    PFN_vkGetPhysicalDeviceMemoryProperties fnGetPhysicalDeviceMemoryProperties = nullptr;
    PFN_vkEnumerateDeviceExtensionProperties fnEnumerateDeviceExtensionProperties = nullptr;
    PFN_vkCreateDevice fnCreateDevice = nullptr;
    PFN_vkGetDeviceProcAddr fnGetDeviceProcAddr = nullptr;
    PFN_vkGetInstanceProcAddr fnGetInstanceProcAddr = nullptr;
    PFN_vkCreateAndroidSurfaceKHR fnCreateAndroidSurfaceKHR = nullptr;
    PFN_vkDestroySurfaceKHR fnDestroySurfaceKHR = nullptr;
    PFN_vkGetPhysicalDeviceSurfaceSupportKHR fnGetPhysicalDeviceSurfaceSupportKHR = nullptr;
    PFN_vkGetPhysicalDeviceSurfaceCapabilitiesKHR fnGetPhysicalDeviceSurfaceCapabilitiesKHR = nullptr;
    PFN_vkGetPhysicalDeviceSurfaceFormatsKHR fnGetPhysicalDeviceSurfaceFormatsKHR = nullptr;
    PFN_vkGetPhysicalDeviceSurfacePresentModesKHR fnGetPhysicalDeviceSurfacePresentModesKHR = nullptr;

    PFN_vkDestroyDevice fnDestroyDevice = nullptr;
    PFN_vkGetDeviceQueue fnGetDeviceQueue = nullptr;
    PFN_vkDeviceWaitIdle fnDeviceWaitIdle = nullptr;
    PFN_vkQueueSubmit fnQueueSubmit = nullptr;
    PFN_vkQueuePresentKHR fnQueuePresentKHR = nullptr;
    PFN_vkCreateSwapchainKHR fnCreateSwapchainKHR = nullptr;
    PFN_vkDestroySwapchainKHR fnDestroySwapchainKHR = nullptr;
    PFN_vkGetSwapchainImagesKHR fnGetSwapchainImagesKHR = nullptr;
    PFN_vkAcquireNextImageKHR fnAcquireNextImageKHR = nullptr;
    PFN_vkCreateImageView fnCreateImageView = nullptr;
    PFN_vkDestroyImageView fnDestroyImageView = nullptr;
    PFN_vkCreateCommandPool fnCreateCommandPool = nullptr;
    PFN_vkDestroyCommandPool fnDestroyCommandPool = nullptr;
    PFN_vkAllocateCommandBuffers fnAllocateCommandBuffers = nullptr;
    PFN_vkFreeCommandBuffers fnFreeCommandBuffers = nullptr;
    PFN_vkBeginCommandBuffer fnBeginCommandBuffer = nullptr;
    PFN_vkEndCommandBuffer fnEndCommandBuffer = nullptr;
    PFN_vkResetCommandBuffer fnResetCommandBuffer = nullptr;
    PFN_vkCmdPipelineBarrier fnCmdPipelineBarrier = nullptr;
    PFN_vkCmdBlitImage fnCmdBlitImage = nullptr;
    PFN_vkCmdCopyBufferToImage fnCmdCopyBufferToImage = nullptr;
    PFN_vkCreateFence fnCreateFence = nullptr;
    PFN_vkDestroyFence fnDestroyFence = nullptr;
    PFN_vkWaitForFences fnWaitForFences = nullptr;
    PFN_vkResetFences fnResetFences = nullptr;
    PFN_vkCreateSemaphore fnCreateSemaphore = nullptr;
    PFN_vkDestroySemaphore fnDestroySemaphore = nullptr;
    PFN_vkCreateBuffer fnCreateBuffer = nullptr;
    PFN_vkDestroyBuffer fnDestroyBuffer = nullptr;
    PFN_vkCreateImage fnCreateImage = nullptr;
    PFN_vkDestroyImage fnDestroyImage = nullptr;
    PFN_vkAllocateMemory fnAllocateMemory = nullptr;
    PFN_vkFreeMemory fnFreeMemory = nullptr;
    PFN_vkBindBufferMemory fnBindBufferMemory = nullptr;
    PFN_vkBindImageMemory fnBindImageMemory = nullptr;
    PFN_vkGetBufferMemoryRequirements fnGetBufferMemoryRequirements = nullptr;
    PFN_vkGetImageMemoryRequirements fnGetImageMemoryRequirements = nullptr;
    PFN_vkMapMemory fnMapMemory = nullptr;
    PFN_vkUnmapMemory fnUnmapMemory = nullptr;
    PFN_vkFlushMappedMemoryRanges fnFlushMappedMemoryRanges = nullptr;
};

}

#endif //LIBRETRODROID_VULKANCONTEXT_H
