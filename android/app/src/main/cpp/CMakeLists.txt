cmake_minimum_required(VERSION 3.22)
project(rlm C)

add_library(rlm SHARED rlm_native.c)
target_compile_options(rlm PRIVATE -Wall -Wextra -Wno-unused-parameter)
# 16 KB page alignment (also set via ANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES)
target_link_options(rlm PRIVATE -Wl,-z,max-page-size=16384)
target_link_libraries(rlm log dl)
