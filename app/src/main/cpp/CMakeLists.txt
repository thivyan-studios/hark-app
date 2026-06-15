cmake_minimum_required(VERSION 3.22.1)
project("hark")

# Required for Android 16KB page size compatibility
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")

find_package(oboe REQUIRED CONFIG)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(hark SHARED
    hark_audio_engine.cpp
    jni_bridge.cpp
)

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
