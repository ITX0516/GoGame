cmake_minimum_required(VERSION 3.22.1)

project("gogame")

add_library(
        gogame
        SHARED
        native-lib.cpp
        GtpEngine.cpp
)

find_library(
        log-lib
        log
)

target_link_libraries(
        gogame
        ${log-lib}
)
