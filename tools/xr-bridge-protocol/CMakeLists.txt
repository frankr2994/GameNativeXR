cmake_minimum_required(VERSION 3.20)

project(GameNativeXrBridgeProtocol VERSION 0.1.0 LANGUAGES CXX)

add_library(xr_bridge_protocol STATIC
    src/XrBridgeProtocol.cpp
)

target_include_directories(xr_bridge_protocol PUBLIC include)
target_compile_features(xr_bridge_protocol PUBLIC cxx_std_17)
target_compile_options(xr_bridge_protocol PRIVATE
    $<$<CXX_COMPILER_ID:MSVC>:/W4 /permissive->
    $<$<NOT:$<CXX_COMPILER_ID:MSVC>>:-Wall -Wextra -Wpedantic>
)

add_executable(xr_bridge_mock src/main.cpp)
target_link_libraries(xr_bridge_mock PRIVATE xr_bridge_protocol)

if(WIN32)
    target_link_libraries(xr_bridge_mock PRIVATE ws2_32)
endif()

include(CTest)
if(BUILD_TESTING)
    add_executable(xr_bridge_protocol_tests tests/protocol_tests.cpp)
    target_link_libraries(xr_bridge_protocol_tests PRIVATE xr_bridge_protocol)
    add_test(NAME xr_bridge_protocol_tests COMMAND xr_bridge_protocol_tests)
endif()
