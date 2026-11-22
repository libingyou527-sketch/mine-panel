// 移植自 llama.cpp 官方 examples/llama.android/lib/src/main/cpp/logging.h
// Android 日志桥：把 ggml/llama 日志级别映射到 __android_log。
// 注：__android_log_is_loggable 需 API 30，本工程 minSdk 26，故直接输出不做可判过滤。

#ifndef MINEPANEL_LOGGING_H
#define MINEPANEL_LOGGING_H

#pragma once
#include <android/log.h>
#include "ggml.h"

#ifndef LOG_TAG
#define LOG_TAG "minepanel-llama"
#endif

#define LOGv(...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)
#define LOGd(...) __android_log_print(ANDROID_LOG_DEBUG,   LOG_TAG, __VA_ARGS__)
#define LOGi(...) __android_log_print(ANDROID_LOG_INFO,    LOG_TAG, __VA_ARGS__)
#define LOGw(...) __android_log_print(ANDROID_LOG_WARN,    LOG_TAG, __VA_ARGS__)
#define LOGe(...) __android_log_print(ANDROID_LOG_ERROR,  LOG_TAG, __VA_ARGS__)

static inline int mp_log_prio_from_ggml(enum ggml_log_level level) {
    switch (level) {
        case GGML_LOG_LEVEL_ERROR: return ANDROID_LOG_ERROR;
        case GGML_LOG_LEVEL_WARN:  return ANDROID_LOG_WARN;
        case GGML_LOG_LEVEL_INFO:  return ANDROID_LOG_INFO;
        case GGML_LOG_LEVEL_DEBUG: return ANDROID_LOG_DEBUG;
        default:                   return ANDROID_LOG_DEFAULT;
    }
}

static inline void mp_android_log_callback(enum ggml_log_level level,
                                           const char *text,
                                           void * /*user*/) {
    __android_log_write(mp_log_prio_from_ggml(level), LOG_TAG, text);
}

#endif // MINEPANEL_LOGGING_H
