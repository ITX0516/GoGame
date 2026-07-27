#include <jni.h>
#include <string>
#include <memory>
#include <android/log.h>
#include "GtpEngine.h"

#define LOG_TAG "NativeGoGame"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::unique_ptr<GtpEngine> g_engine;
static JavaVM* g_jvm = nullptr;
static jobject g_callbackObj = nullptr;
static jmethodID g_onResponseMethod = nullptr;

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_jvm = vm;
    LOGD("JNI_OnLoad called");
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_gogame_engine_LeelaManager_nativeInitEngine(JNIEnv *env, jobject thiz) {
    LOGD("nativeInitEngine called");
    g_engine = std::make_unique<GtpEngine>();

    jclass callbackClass = env->GetObjectClass(thiz);
    g_onResponseMethod = env->GetMethodID(callbackClass, "onNativeResponse", "(Ljava/lang/String;)V");
    g_callbackObj = env->NewGlobalRef(thiz);

    g_engine->setResponseCallback([](const std::string& response) {
        JNIEnv* env;
        bool attached = false;
        int status = g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);

        if (status == JNI_EDETACHED) {
            if (g_jvm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
                LOGE("Failed to attach current thread");
                return;
            }
            attached = true;
        }

        if (g_callbackObj && g_onResponseMethod) {
            jstring jResponse = env->NewStringUTF(response.c_str());
            env->CallVoidMethod(g_callbackObj, g_onResponseMethod, jResponse);
            env->DeleteLocalRef(jResponse);
        }

        if (attached) {
            g_jvm->DetachCurrentThread();
        }
    });
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_gogame_engine_LeelaManager_nativeStartEngine(JNIEnv *env, jobject thiz) {
    LOGD("nativeStartEngine called");
    if (!g_engine) {
        return JNI_FALSE;
    }
    return g_engine->start() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_gogame_engine_LeelaManager_nativeStopEngine(JNIEnv *env, jobject thiz) {
    LOGD("nativeStopEngine called");
    if (g_engine) {
        g_engine->stop();
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_gogame_engine_LeelaManager_nativeIsEngineReady(JNIEnv *env, jobject thiz) {
    if (!g_engine) {
        return JNI_FALSE;
    }
    return g_engine->isReady() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_gogame_engine_LeelaManager_nativeSendCommand(JNIEnv *env, jobject thiz, jstring command) {
    if (!g_engine || !command) {
        return;
    }

    const char* cmdStr = env->GetStringUTFChars(command, nullptr);
    std::string cmd(cmdStr);
    env->ReleaseStringUTFChars(command, cmdStr);

    g_engine->sendCommand(cmd);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_gogame_engine_LeelaManager_nativeSendCommandSync(JNIEnv *env, jobject thiz, jstring command) {
    if (!g_engine || !command) {
        return env->NewStringUTF("? engine not ready\n\n");
    }

    const char* cmdStr = env->GetStringUTFChars(command, nullptr);
    std::string cmd(cmdStr);
    env->ReleaseStringUTFChars(command, cmdStr);

    std::string response = g_engine->sendCommandSync(cmd);
    return env->NewStringUTF(response.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_gogame_engine_LeelaManager_nativeDestroyEngine(JNIEnv *env, jobject thiz) {
    LOGD("nativeDestroyEngine called");
    if (g_engine) {
        g_engine->stop();
        g_engine.reset();
    }
    if (g_callbackObj) {
        env->DeleteGlobalRef(g_callbackObj);
        g_callbackObj = nullptr;
    }
    g_onResponseMethod = nullptr;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_gogame_engine_LeelaManager_nativeGetEngineName(JNIEnv *env, jobject thiz) {
    return env->NewStringUTF("GoGame Mock Engine");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_gogame_engine_LeelaManager_nativeGetEngineVersion(JNIEnv *env, jobject thiz) {
    return env->NewStringUTF("1.0");
}
