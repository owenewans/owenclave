
#include <string_view>

#include <fcntl.h>
#include <termios.h>
#include <unistd.h>
#include <dirent.h>
#include <sys/wait.h>

#include <jni.h>

#define TERMUX_UNUSED(x) x __attribute__((__unused__))
#ifdef __APPLE__
# define LACKS_PTSNAME_R
#endif

static int throw_runtime_exception(JNIEnv *env, const char *message) {
    jclass exceptionClass = env->FindClass("java/lang/RuntimeException");
    env->ThrowNew(exceptionClass, message);
    return -1;
}

static int create_subprocess(JNIEnv *env,
                             std::string_view cmd,
                             std::string_view cwd,
                             char *const argv[],
                             char **envp,
                             int *pProcessId,
                             jint rows,
                             jint columns) {

    int ptm = open("/dev/ptmx", O_RDWR | O_CLOEXEC);
    if (ptm < 0) {
        return throw_runtime_exception(env, "Cannot open /dev/ptmx");
    }

#ifdef LACKS_PTSNAME_R
    char *devname;
    bool flag = grantpt(ptm) || unlockpt(ptm) || (devname = ptsname(ptm)) == NULL;
#else
    char dev_name[64];
    bool flag = grantpt(ptm) || unlockpt(ptm) || ptsname_r(ptm, dev_name, sizeof(dev_name));
#endif

    if (flag) {
        return throw_runtime_exception(env, "Cannot grantpt()/unlockpt()/ptsname_r() on /dev/ptmx");
    }

    // Enable UTF-8 mode and disable flow control to prevent Ctrl+S from locking up the display.
    termios tios{};
    tcgetattr(ptm, &tios);

    tios.c_iflag |= IUTF8;
    tios.c_iflag &= ~(IXON | IXOFF);

    tcsetattr(ptm, TCSANOW, &tios);

    /** Set initial winsize. */
    winsize sz = {
        .ws_row = static_cast<unsigned short>(rows),
        .ws_col = static_cast<unsigned short>(columns)
    };
    ioctl(ptm, TIOCSWINSZ, &sz);

    pid_t pid = fork();

    if (pid < 0) {
        return throw_runtime_exception(env, "Fork failed");
    }

    if (pid > 0) {
        *pProcessId = static_cast<int>(pid);
        return ptm;
    }

    sigset_t signals_to_unblock;
    sigfillset(&signals_to_unblock);
    sigprocmask(SIG_UNBLOCK, &signals_to_unblock, nullptr);

    close(ptm);
    setsid();

    int pts = open(dev_name, O_RDWR);
    if (pts < 0) {
        exit(-1);
    }

    dup2(pts, 0);
    dup2(pts, 1);
    dup2(pts, 2);

    DIR *self_dir = opendir("/proc/self/fd");
    if (self_dir) {
        int self_dir_fd = dirfd(self_dir);

        dirent *entry;
        while ((entry = readdir(self_dir))) {
            int fd = atoi(entry->d_name);
            if (fd > 2 && fd != self_dir_fd) {
                close(fd);
            }
        }

        closedir(self_dir);
    }

    clearenv();
    if (envp) {
        for (; *envp; ++envp) {
            putenv(*envp);
        }
    }

    char *error_message;
    if (chdir(cwd.data())) {
        if (asprintf(&error_message, "chdir(\"%s\")", cwd.data()) != -1) {
            perror(error_message);
        } else {
            perror("chdir()");
        }

        fflush(stderr);
    }

    execvp(cmd.data(), argv);

    if (asprintf(&error_message, "exec(\"%s\")", cmd.data()) != -1) {
        perror(error_message);
    } else {
        perror("chdir()");
    }

    _exit(1);
}

static char **create_char_array(JNIEnv *env, jobjectArray array) {

    jsize size = array ? env->GetArrayLength(array) : 0;
    if (size <= 0) {
        return nullptr;
    }

    auto result = new (std::nothrow) char*[size + 1];
    if (!result) {
        throw_runtime_exception(env, "Couldn't allocate char array");
        return nullptr;
    }

    for (int i = 0; i < size; ++i) {
        auto element_jstring = reinterpret_cast<jstring>(env->GetObjectArrayElement(array, i));
        auto element_utf8 = env->GetStringUTFChars(element_jstring, nullptr);

        if (!element_utf8) {
            throw_runtime_exception(env, "GetStringUTFChars() failed");
            return nullptr;
        }

        result[i] = strdup(element_utf8);
        env->ReleaseStringUTFChars(element_jstring, element_utf8);
    }

    result[size] = nullptr;

    return result;
}

static void free_char_array(char **array) {
    for (; *array; ++array) {
        free(*array);
    }
}

extern "C" {

JNIEXPORT jint JNICALL Java_com_termux_terminal_JNI_createSubprocess(
        JNIEnv *env,
        jclass clazz,
        jstring cmd,
        jstring cwd,
        jobjectArray args,
        jobjectArray env_vars,
        jintArray process_ids,
        jint rows,
        jint columns) {

    auto argv = create_char_array(env, args);
    auto envp = create_char_array(env, env_vars);

    int proc_id = 0;
    auto cmd_cwd = env->GetStringUTFChars(cwd, nullptr);
    auto cmd_utf8 = env->GetStringUTFChars(cmd, nullptr);

    int ptm = create_subprocess(env, cmd_utf8, cmd_cwd, argv, envp, &proc_id, rows, columns);

    env->ReleaseStringUTFChars(cmd, cmd_utf8);
    env->ReleaseStringUTFChars(cwd, cmd_cwd);

    if (argv) {
        free_char_array(argv);
    }

    if (envp) {
        free_char_array(envp);
    }

    int *proc_ids = reinterpret_cast<int *>(env->GetPrimitiveArrayCritical(process_ids, nullptr));
    if (!proc_ids) {
        return throw_runtime_exception(env, "JNI call GetPrimitiveArrayCritical(process_ids, &isCopy) failed");
    }

    *proc_ids = proc_id;
    env->ReleasePrimitiveArrayCritical(process_ids, proc_ids, 0);

    return ptm;
}

JNIEXPORT void JNICALL Java_com_termux_terminal_JNI_setPtyWindowSize(
        JNIEnv *TERMUX_UNUSED(env),
        jclass TERMUX_UNUSED(clazz),
        jint fd,
        jint rows,
        jint cols) {

    winsize size = {
            .ws_row = static_cast<unsigned short>(rows),
            .ws_col = static_cast<unsigned short>(cols)
    };

    ioctl(fd, TIOCSWINSZ, &size);
}

JNIEXPORT jint JNICALL Java_com_termux_terminal_JNI_waitFor(
        JNIEnv *TERMUX_UNUSED(env),
        jclass TERMUX_UNUSED(clazz),
        jint process_id) {

    int status;
    waitpid(process_id, &status, 0);

    if (WIFEXITED(status)) {
        return WEXITSTATUS(status);
    }

    if (WIFSIGNALED(status)) {
        return -WTERMSIG(status);
    }

    return 0;
}

JNIEXPORT void JNICALL Java_com_termux_terminal_JNI_close(
        JNIEnv *TERMUX_UNUSED(env),
        jclass TERMUX_UNUSED(clazz),
        jint file_descriptor) {

    close(file_descriptor);
}

}