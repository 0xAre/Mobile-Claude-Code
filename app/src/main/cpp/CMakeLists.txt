# Native PTY library for Claude Terminal (Fase 2).
# Enable by uncommenting the externalNativeBuild block in app/build.gradle.
cmake_minimum_required(VERSION 3.22.1)
project(claudepty C)

add_library(claudepty SHARED pty.c)

# forkpty lives in libc on Android (bionic); no extra link libs needed beyond log.
find_library(log-lib log)
target_link_libraries(claudepty ${log-lib})
