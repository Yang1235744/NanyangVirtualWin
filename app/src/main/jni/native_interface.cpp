#include <jni.h>
#include <android/log.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define LOG_TAG "NVW_Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

// 启动 QEMU 进程，返回子进程 PID；失败返回 -1
JNIEXPORT jlong JNICALL
Java_com_nanyang_virtualwin_service_VMService_startQemuNative
  (JNIEnv *env, jobject /*this*/, jstring argsArray, jstring vmDir) {

    const char *argsStr = env->GetStringUTFChars(argsArray, nullptr);
    const char *dir     = env->GetStringUTFChars(vmDir,   nullptr);

    // 解析参数数组（逗号分隔）
    char *buf = (char *)malloc(strlen(argsStr) + 1);
    strcpy(buf, argsStr);
    env->ReleaseStringUTFChars(argsArray, argsStr);

    char *argv[64] = {nullptr};
    int argc = 0;
    char *tok = strtok(buf, ",");
    while (tok && argc < 63) { argv[argc++] = tok; tok = strtok(nullptr, ","); }
    argv[argc] = nullptr;
    free(buf);

    pid_t pid = fork();
    if (pid == 0) {
        // 子进程：切换到 VM 工作目录并执行 QEMU
        chdir(dir);
        execv(argv[0], argv);
        _exit(127);
    } else if (pid > 0) {
        LOGI("QEMU started with PID %d", (int)pid);
        return (jlong)pid;
    } else {
        LOGE("fork() failed");
        return -1L;
    }
}

// 向 QEMU 发送监控命令（qmp/mon）
JNIEXPORT void JNICALL
Java_com_nanyang_virtualwin_service_VMService_sendMonitorCmd
  (JNIEnv *env, jobject /*this*/, jlong pid, jstring cmd) {
    // TODO: 通过 Unix domain socket 或 gdbstub 发送命令
    LOGI("send monitor cmd to PID %lld: %s", (long long)pid,
         env->GetStringUTFChars(cmd, nullptr));
}

// 终止 QEMU 进程
JNIEXPORT void JNICALL
Java_com_nanyang_virtualwin_service_VMService_killQemuNative
  (JNIEnv *env, jobject /*this*/, jlong pid) {
    kill((pid_t)pid, SIGTERM);
    LOGI("QEMU PID %lld terminated", (long long)pid);
}

} // extern "C"
