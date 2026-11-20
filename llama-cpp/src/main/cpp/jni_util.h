#pragma once

#include <jni.h>
#include <string>
#include <exception>

inline void throwJava(JNIEnv *env, const char *message) {
    jclass cls = env->FindClass("java/lang/RuntimeException");
    if (cls != nullptr) {
        env->ThrowNew(cls, message);
    }
}

// Paths can contain supplementary Unicode too. Convert UTF-16 to standard UTF-8;
// JNI Modified UTF-8 would make e.g. emoji filenames fail to open on Android.
inline std::string jstringToUtf8(JNIEnv *env, jstring value) {
    if (!value) return {};
    const jchar *chars = env->GetStringChars(value, nullptr);
    if (!chars) return {};
    struct ReleaseGuard {
        JNIEnv *env;
        jstring value;
        const jchar *chars;
        ~ReleaseGuard() { env->ReleaseStringChars(value, chars); }
    } guard{env, value, chars};
    std::string result;
    const jsize length = env->GetStringLength(value);
    for (jsize i = 0; i < length; ++i) {
        uint32_t cp = chars[i];
        if (cp >= 0xd800 && cp <= 0xdbff && i + 1 < length && chars[i + 1] >= 0xdc00 && chars[i + 1] <= 0xdfff)
            cp = 0x10000 + ((cp - 0xd800) << 10) + (chars[++i] - 0xdc00);
        else if (cp >= 0xd800 && cp <= 0xdfff) cp = 0xfffd;
        if (cp < 0x80) result += static_cast<char>(cp);
        else if (cp < 0x800) { result += static_cast<char>(0xc0 | cp >> 6); result += static_cast<char>(0x80 | cp & 0x3f); }
        else if (cp < 0x10000) { result += static_cast<char>(0xe0 | cp >> 12); result += static_cast<char>(0x80 | (cp >> 6) & 0x3f); result += static_cast<char>(0x80 | cp & 0x3f); }
        else { result += static_cast<char>(0xf0 | cp >> 18); result += static_cast<char>(0x80 | (cp >> 12) & 0x3f); result += static_cast<char>(0x80 | (cp >> 6) & 0x3f); result += static_cast<char>(0x80 | cp & 0x3f); }
    }
    return result;
}

// Copies a Java byte[] holding UTF-8 text into a std::string. Unlike jstringToUtf8, this is
// standard UTF-8 exactly as the caller encoded it: no CESU-8, no overlong forms, safe to hand to
// a strict UTF-8 parser such as nlohmann::json.
inline std::string byteArrayToUtf8(JNIEnv *env, jbyteArray value) {
    if (value == nullptr) {
        return std::string();
    }
    const jsize len = env->GetArrayLength(value);
    std::string result(static_cast<size_t>(len), '\0');
    if (len > 0) {
        env->GetByteArrayRegion(value, 0, len, reinterpret_cast<jbyte *>(result.data()));
    }
    return result;
}

// Copies a std::string holding UTF-8 text into a new Java byte[]. The symmetric return path:
// NewStringUTF requires Modified UTF-8, which standard UTF-8 (what nlohmann::json::dump
// produces) is not in general, so text that may contain supplementary-plane characters must
// cross this boundary as bytes, not as a jstring.
inline jbyteArray utf8ToByteArray(JNIEnv *env, const std::string &value) {
    jbyteArray result = env->NewByteArray(static_cast<jsize>(value.size()));
    if (result != nullptr && !value.empty()) {
        env->SetByteArrayRegion(result, 0, static_cast<jsize>(value.size()),
                                 reinterpret_cast<const jbyte *>(value.data()));
    }
    return result;
}

// Wraps a JNI body so no C++ exception can cross the boundary.
#define JNI_GUARD(env, failValue, ...)                          \
    try {                                                        \
        __VA_ARGS__                                              \
    } catch (const std::exception &e) {                          \
        throwJava(env, e.what());                                \
        return failValue;                                        \
    } catch (...) {                                              \
        throwJava(env, "unknown native error");                  \
        return failValue;                                        \
    }

// Same as JNI_GUARD, for void-returning entry points where there is no failValue.
#define JNI_GUARD_VOID(env, ...)                                 \
    try {                                                        \
        __VA_ARGS__                                              \
    } catch (const std::exception &e) {                          \
        throwJava(env, e.what());                                \
    } catch (...) {                                              \
        throwJava(env, "unknown native error");                  \
    }
