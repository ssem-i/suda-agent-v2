#==============================================================================
#
#	Copyright (c) 2022 Qualcomm Technologies, Inc.
#	All Rights Reserved.
#	Confidential and Proprietary - Qualcomm Technologies, Inc.
#
#==============================================================================
cmake_minimum_required(VERSION 3.10.2)

# Compiler Flags
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++14")
set(CMAKE_C_FLAGS_DEBUG "${CMAKE_C_FLAGS_DEBUG} -O3")

# Add headers
include_directories(${CMAKE_CURRENT_SOURCE_DIR})
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/Genie_inc)

# Declares and names the project.
project("Llama2Genie")

set(APP_SOURCES "native-lib.cpp"
		"main.cpp"
		"Logging.cpp"
)

add_library( # Sets the name of the library.
		Llama2Genie

		# Sets the library as a shared library.
		SHARED

		# Provides a relative path to your source file(s).
		${APP_SOURCES})

find_library( # Sets the name of the path variable.
		log-lib
		android-lib
		android
		log)

# Specifies libraries CMake should link to your target library.
target_link_libraries( # Specifies the target library.
		Llama2Genie

		# Add path to dependent libGenie.so
		${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/arm64-v8a/libGenie.so

		# Links the target library to the log library
		# included in the NDK.
		android
		log)

# .so 파일을 상대 경로에 저장
#set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})

target_compile_definitions(Llama2Genie PUBLIC NO_LOGGING)

