cmake_minimum_required(VERSION 3.22.1)
project("minepanel-llama" VERSION 1.0.0 LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED true)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED true)

# llama.cpp 源码目录（不进工程 git）；由 Gradle 从环境变量 LLAMA_SRC_DIR
# 或 local.properties 的 llama.src.dir 传入。未配置则在配置阶段报清晰错误。
if(NOT DEFINED LLAMA_SRC_DIR OR LLAMA_SRC_DIR STREQUAL "")
    message(FATAL_ERROR "LLAMA_SRC_DIR 未配置：请在环境变量 LLAMA_SRC_DIR 或 local.properties 的 llama.src.dir 指定 llama.cpp 源码目录。")
endif()

if(NOT EXISTS "${LLAMA_SRC_DIR}/CMakeLists.txt")
    message(FATAL_ERROR "llama.cpp source not found at: ${LLAMA_SRC_DIR}")
endif()

add_subdirectory(${LLAMA_SRC_DIR} ${CMAKE_BINARY_DIR}/build-llama)

add_library(${CMAKE_PROJECT_NAME} SHARED
    minepanel_llama.cpp)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
    ${LLAMA_SRC_DIR}
    ${LLAMA_SRC_DIR}/common
    ${LLAMA_SRC_DIR}/include
    ${LLAMA_SRC_DIR}/ggml/include
    ${LLAMA_SRC_DIR}/ggml/src)

target_compile_options(${CMAKE_PROJECT_NAME} PRIVATE
    -ffunction-sections -fdata-sections -fvisibility=hidden)

target_link_options(${CMAKE_PROJECT_NAME} PRIVATE
    -Wl,--gc-sections
    -Wl,--exclude-libs,ALL
    -Wl,-s)

target_link_libraries(${CMAKE_PROJECT_NAME}
    llama
    llama-common
    android
    log)
