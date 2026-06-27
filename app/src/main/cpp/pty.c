/*
 * Native PTY bridge for Claude Terminal (Fase 2).
 *
 * Provides a real pseudo-terminal via forkpty(3) so interactive programs — bash,
 * vim, and the `claude` CLI's OAuth login prompt — work. Modeled on Termux's
 * terminal-emulator JNI (GPLv3); this file is part of the GPLv3 app.
 *
 * Status: committed for reference. It is wired into the build only when
 * externalNativeBuild is enabled in app/build.gradle (see docs/FASE2.md).
 *
 * JNI methods back com.zeroxare.claudemobile.engine.PtyProcess.
 */
#include <jni.h>
#include <pty.h>
#include <unistd.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <termios.h>

static char **to_argv(JNIEnv *env, jobjectArray arr) {
    jsize n = (*env)->GetArrayLength(env, arr);
    char **out = (char **) calloc(n + 1, sizeof(char *));
    for (jsize i = 0; i < n; i++) {
        jstring s = (jstring) (*env)->GetObjectArrayElement(env, arr, i);
        const char *c = (*env)->GetStringUTFChars(env, s, 0);
        out[i] = strdup(c);
        (*env)->ReleaseStringUTFChars(env, s, c);
    }
    out[n] = NULL;
    return out;
}

static void free_argv(char **argv) {
    if (!argv) return;
    for (int i = 0; argv[i]; i++) free(argv[i]);
    free(argv);
}

JNIEXPORT jint JNICALL
Java_com_zeroxare_claudemobile_engine_PtyProcess_nativeCreateSubprocess(
        JNIEnv *env, jobject thiz,
        jstring cmd, jstring cwd, jobjectArray argsArr, jobjectArray envArr,
        jintArray outPid, jint rows, jint cols) {

    const char *cmd_c = (*env)->GetStringUTFChars(env, cmd, 0);
    const char *cwd_c = (*env)->GetStringUTFChars(env, cwd, 0);
    char **argv = to_argv(env, argsArr);
    char **envp = to_argv(env, envArr);

    struct winsize ws = { .ws_row = (unsigned short) rows, .ws_col = (unsigned short) cols };

    int master;
    pid_t pid = forkpty(&master, NULL, NULL, &ws);
    if (pid < 0) {
        (*env)->ReleaseStringUTFChars(env, cmd, cmd_c);
        (*env)->ReleaseStringUTFChars(env, cwd, cwd_c);
        free_argv(argv);
        free_argv(envp);
        return -1;
    }

    if (pid == 0) {
        // Child: set cwd, env, then exec the target command.
        if (chdir(cwd_c) != 0) { /* ignore */ }
        execve(cmd_c, argv, envp);
        _exit(127); // exec failed
    }

    // Parent
    jint *pidOut = (*env)->GetIntArrayElements(env, outPid, 0);
    pidOut[0] = pid;
    (*env)->ReleaseIntArrayElements(env, outPid, pidOut, 0);

    fcntl(master, F_SETFL, O_NONBLOCK & ~O_NONBLOCK); // keep blocking reads
    (*env)->ReleaseStringUTFChars(env, cmd, cmd_c);
    (*env)->ReleaseStringUTFChars(env, cwd, cwd_c);
    free_argv(argv);
    free_argv(envp);
    return master;
}

JNIEXPORT void JNICALL
Java_com_zeroxare_claudemobile_engine_PtyProcess_nativeSetWinSize(
        JNIEnv *env, jobject thiz, jint fd, jint rows, jint cols) {
    struct winsize ws = { .ws_row = (unsigned short) rows, .ws_col = (unsigned short) cols };
    ioctl(fd, TIOCSWINSZ, &ws);
}

JNIEXPORT jint JNICALL
Java_com_zeroxare_claudemobile_engine_PtyProcess_nativeWaitFor(
        JNIEnv *env, jobject thiz, jint pid) {
    int status = 0;
    if (waitpid((pid_t) pid, &status, 0) < 0) return -1;
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return 128 + WTERMSIG(status);
    return -1;
}
