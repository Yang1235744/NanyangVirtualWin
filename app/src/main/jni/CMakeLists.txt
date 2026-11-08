# CMakeLists.txt - 南阳虚拟Win 原生层（QEMU集成 + USB OTG）
cmake_minimum_required(VERSION 3.22.1)
project(nanyang_virtualwin)

# 引入 prebuilt QEMU ARM/x86 二进制
set(QEMU_DIR ${CMAKE_SOURCE_DIR}/jni/libs/${ANDROID_ABI})
file(GLOB QEMU_BIN "${QEMU_DIR}/qemu-system-*")

# 仅构建 ARM64
if(NOT ANDROID_ABI STREQUAL "arm64-v8a")
    message(WARNING "Only arm64-v8a is supported for Windows VM")
endif()

add_library(nvw_native SHARED
    native_interface.cpp
)

target_include_directories(nvw_native PRIVATE
    ${CMAKE_SOURCE_DIR}/include
)

target_link_libraries(nvw_native
    android
    log
    jnigraphics
)

# 将 QEMU 二进制复制到 APK assets/lib 目录
add_custom_command(TARGET nvw_native POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E make_directory
        ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/../lib/${ANDROID_ABI}
    COMMAND ${CMAKE_COMMAND} -E copy
        ${QEMU_BIN}
        ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/../lib/${ANDROID_ABI}/
    COMMENT "Packaging QEMU binary into APK"
)
