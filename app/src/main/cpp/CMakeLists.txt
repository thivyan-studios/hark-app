cmake_minimum_required(VERSION 3.22.1)

project("hark")

# Required for Android 16KB page size compatibility
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")

find_package(oboe REQUIRED CONFIG)

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Configure include directories
include_directories(
    dsp
    engine
    jni
)

# Build the main Hark engine
add_library(hark SHARED
    dsp/pink_noise_generator.cpp
    engine/hark_audio_engine.cpp
    jni/jni_bridge.cpp
)

target_link_libraries(hark
    oboe::oboe
    log
    android
    m
)
