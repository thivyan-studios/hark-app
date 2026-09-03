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

# Build the main Hark engine
add_library(hark SHARED
    hark_audio_engine.cpp
    jni_bridge.cpp
)

# Compile options for performance
if(CMAKE_SYSTEM_PROCESSOR MATCHES "aarch64|armv8-a")
    target_compile_options(hark PRIVATE -O3 -ffast-math -fno-finite-math-only)
elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "x86_64")
    target_compile_options(hark PRIVATE -O3 -ffast-math -fno-finite-math-only)
endif()

target_link_libraries(hark
    oboe::oboe
    log
    android
    m
    dl
)
