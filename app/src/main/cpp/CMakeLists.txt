cmake_minimum_required(VERSION 3.22.1)

project("hark")

# AddressSanitizer configuration
if (ENABLE_ASAN)
    message(STATUS "Enabling AddressSanitizer")
    set(ASAN_FLAGS "-fsanitize=address -fno-omit-frame-pointer")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${ASAN_FLAGS}")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} ${ASAN_FLAGS}")
    set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} ${ASAN_FLAGS}")
endif()

# Required for Android 16KB page size compatibility
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")

find_package(oboe REQUIRED CONFIG)

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Whisper.cpp configuration
set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper)
include_directories(${WHISPER_DIR})
include_directories(${WHISPER_DIR}/ggml-cpu)

# Source files for Whisper and GGML
set(WHISPER_SRCS
    ${WHISPER_DIR}/whisper.cpp
    ${WHISPER_DIR}/ggml.c
    ${WHISPER_DIR}/ggml-alloc.c
    ${WHISPER_DIR}/ggml-backend.cpp
    ${WHISPER_DIR}/ggml-quants.c
    ${WHISPER_DIR}/ggml-threading.cpp
    ${WHISPER_DIR}/ggml.cpp
    ${WHISPER_DIR}/gguf.cpp
    ${WHISPER_DIR}/ggml-backend-reg.cpp

    # CPU Backend
    ${WHISPER_DIR}/ggml-cpu/ggml-cpu.c
    ${WHISPER_DIR}/ggml-cpu/ggml-cpu.cpp
    ${WHISPER_DIR}/ggml-cpu/ops.cpp
    ${WHISPER_DIR}/ggml-cpu/traits.cpp
    ${WHISPER_DIR}/ggml-cpu/repack.cpp
    ${WHISPER_DIR}/ggml-cpu/hbm.cpp
    ${WHISPER_DIR}/ggml-cpu/binary-ops.cpp
    ${WHISPER_DIR}/ggml-cpu/unary-ops.cpp
    ${WHISPER_DIR}/ggml-cpu/vec.cpp
    ${WHISPER_DIR}/ggml-cpu/quants.c
    ${WHISPER_DIR}/ggml-backend-dl.cpp
)

# Architecture-specific GGML-CPU sources
if(CMAKE_SYSTEM_PROCESSOR MATCHES "aarch64|armv8-a|arm")
    list(APPEND WHISPER_SRCS
        ${WHISPER_DIR}/ggml-cpu/arch/arm/cpu-feats.cpp
        ${WHISPER_DIR}/ggml-cpu/arch/arm/quants.c
        ${WHISPER_DIR}/ggml-cpu/arch/arm/repack.cpp
    )
elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "x86_64|i686")
    list(APPEND WHISPER_SRCS
        ${WHISPER_DIR}/ggml-cpu/arch/x86/cpu-feats.cpp
        ${WHISPER_DIR}/ggml-cpu/arch/x86/quants.c
        ${WHISPER_DIR}/ggml-cpu/arch/x86/repack.cpp
    )
endif()

# Build the main Hark engine
add_library(hark SHARED
    hark_audio_engine.cpp
    jni_bridge.cpp
    whisper_bridge.cpp
    ${WHISPER_SRCS}
)

# Add NEON support for ARM or AVX support for x86
if(CMAKE_SYSTEM_PROCESSOR MATCHES "aarch64|armv8-a")
    target_compile_options(hark PRIVATE -O3 -ffast-math -fno-finite-math-only)
    target_compile_definitions(hark PRIVATE
        GGML_USE_CPU
        GGML_USE_NEON
        WHISPER_VERSION=\"1.10.0\"
        GGML_VERSION=\"0.9.8\"
        GGML_COMMIT=\"unknown\"
    )
elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "x86_64")
    # For emulators, we sometimes need to be careful with AVX
    target_compile_options(hark PRIVATE -O3 -ffast-math -fno-finite-math-only)
    target_compile_definitions(hark PRIVATE
        GGML_USE_CPU
        # Disable AVX/FMA explicitly if it's crashing
        # GGML_NO_ACCEL
        WHISPER_VERSION=\"1.10.0\"
        GGML_VERSION=\"0.9.8\"
        GGML_COMMIT=\"unknown\"
    )
else()
    target_compile_definitions(hark PRIVATE
        GGML_USE_CPU
        WHISPER_VERSION=\"1.10.0\"
        GGML_VERSION=\"0.9.8\"
        GGML_COMMIT=\"unknown\"
    )
endif()

target_link_libraries(hark
    oboe::oboe
    log
    android
    m
    dl
)