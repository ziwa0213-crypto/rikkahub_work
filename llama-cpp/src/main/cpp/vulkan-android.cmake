# Keep the pinned checkout intact. Android API 26's loader stub only exports
# Vulkan 1.0 entry points, so route this pin's direct 1.1 calls through the
# instance dispatch table it already initializes at runtime.
set(vk_source_dir "${LLAMA_ROOT}/ggml/src/ggml-vulkan")
set(vk_source "${vk_source_dir}/ggml-vulkan.cpp")
set(vk_android_source "${CMAKE_CURRENT_BINARY_DIR}/ggml-vulkan-android.cpp")
set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${vk_source}")
file(READ "${vk_source}" vk_code)
string(REGEX MATCHALL "vkGetPhysicalDeviceFeatures2\\(" vk_static_calls "${vk_code}")
list(LENGTH vk_static_calls vk_static_call_count)
set(vk_create_line "    vk_instance.instance = vk::createInstance(instance_create_info);")
set(vk_dispatch_line "    VULKAN_HPP_DEFAULT_DISPATCHER.init(vk_instance.instance);")
string(FIND "${vk_code}" "${vk_create_line}" vk_create_pos)
string(FIND "${vk_code}" "${vk_dispatch_line}" vk_dispatch_pos)
if(NOT vk_static_call_count EQUAL 3 OR vk_create_pos EQUAL -1 OR vk_dispatch_pos EQUAL -1)
    message(FATAL_ERROR "Recheck the Android Vulkan dispatch adaptation for the updated llama.cpp pin")
endif()
string(REPLACE "vkGetPhysicalDeviceFeatures2(" "VULKAN_HPP_DEFAULT_DISPATCHER.vkGetPhysicalDeviceFeatures2(" vk_code "${vk_code}")
string(REPLACE "${vk_dispatch_line}" "" vk_code "${vk_code}")
string(CONCAT vk_create_checked "${vk_create_line}\n${vk_dispatch_line}\n" [=[    const auto & dispatch = VULKAN_HPP_DEFAULT_DISPATCHER;
    if (!dispatch.vkGetPhysicalDeviceFeatures2 || !dispatch.vkGetPhysicalDeviceProperties2 ||
        !dispatch.vkGetPhysicalDeviceMemoryProperties2) {
        vk_instance.instance.destroy();
        vk_instance.instance = nullptr;
        throw vk::SystemError(vk::Result::eErrorFeatureNotPresent, "Vulkan device queries unavailable");
    }]=])
string(REPLACE "${vk_create_line}" "${vk_create_checked}" vk_code "${vk_code}")
file(CONFIGURE OUTPUT "${vk_android_source}" CONTENT "${vk_code}" @ONLY)
set_source_files_properties("${vk_source}" TARGET_DIRECTORY ggml-vulkan PROPERTIES HEADER_FILE_ONLY TRUE)
target_sources(ggml-vulkan PRIVATE "${vk_android_source}")
target_include_directories(ggml-vulkan PRIVATE "${vk_source_dir}")

# JNI registers Vulkan on explicit GPU loads. Keep the automatic ggml registry
# on the original CPU path so it never probes a graphics driver with GPU off.
foreach(property IN ITEMS COMPILE_DEFINITIONS INTERFACE_COMPILE_DEFINITIONS)
    get_target_property(ggml_definitions ggml "${property}")
    list(REMOVE_ITEM ggml_definitions "GGML_USE_VULKAN")
    set_property(TARGET ggml PROPERTY "${property}" "${ggml_definitions}")
endforeach()
