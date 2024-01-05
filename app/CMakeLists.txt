cmake_minimum_required(VERSION 3.4.1)

get_filename_component(BLELOC_ROOT ../../blelocpp ABSOLUTE)
set(BLESRC_ROOT ${BLELOC_ROOT}/ble-cpp/src)
set(PODS_ROOT ${BLELOC_ROOT}/platform/ios/Pods)
set(OPENCV_LIBS src/main/jniLibs/${ANDROID_ABI})
set(ANDROID_STL "c++_shared")

add_definitions("-std=c++11 -fexceptions -frtti -O3")
add_definitions("-DANDROID_STL=c++_shared")
add_definitions("-DANDROID_JNI")

include_directories(${BLESRC_ROOT}/beacon)
include_directories(${BLESRC_ROOT}/core)
include_directories(${BLESRC_ROOT}/data)
include_directories(${BLESRC_ROOT}/filter)
include_directories(${BLESRC_ROOT}/impl)
include_directories(${BLESRC_ROOT}/localizer)
include_directories(${BLESRC_ROOT}/map)
include_directories(${BLESRC_ROOT}/mc)
include_directories(${BLESRC_ROOT}/model)
include_directories(${BLESRC_ROOT}/sensor)
include_directories(${BLESRC_ROOT}/utils)
include_directories(${BLESRC_ROOT}/jni)
include_directories(${BLELOC_ROOT}/platform/android/utils)

include_directories(${PODS_ROOT}/boost)
include_directories(${PODS_ROOT}/cereal/include)
include_directories(${PODS_ROOT}/eigen)

# Use OpenCV via CocoaPods
if(EXISTS ${PODS_ROOT}/Headers/Public/OpenCV)
    include_directories(${PODS_ROOT}/Headers/Public/OpenCV)
elseif(EXISTS ${PODS_ROOT}/Headers/Public/OpenCV-Dynamic)
    include_directories(${PODS_ROOT}/Headers/Public/OpenCV-Dynamic)
endif()

# Use OpenCV via Homebrew
#include_directories(/usr/local/opt/opencv/include/opencv4)

include_directories(${PODS_ROOT}/Headers/Public/cereal)
include_directories(${PODS_ROOT}/Headers/Public/picojson)

link_directories(${OPENCV_LIBS})

file(GLOB_RECURSE Library_SOURCES ${BLESRC_ROOT}/*.cpp ${BLELOC_ROOT}/platform/android/utils/*.cpp)
list(REMOVE_ITEM Library_SOURCES ${BLESRC_ROOT}/log/main.cpp)
list(REMOVE_ITEM Library_SOURCES ${BLESRC_ROOT}/log/NavCogLogPlayer.cpp)
list(REMOVE_ITEM Library_SOURCES ${BLESRC_ROOT}/log/StreamParticleFilterBuilder.cpp)

add_library(bleloc-lib SHARED ${Library_SOURCES})

target_link_libraries(bleloc-lib opencv_java4 log)
