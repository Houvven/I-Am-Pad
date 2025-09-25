#include <cstdlib>
#include <unistd.h>
#include <fcntl.h>
#include <android/log.h>
#include <cstring>
#include <sys/stat.h>
#include <vector>
#include <stdexcept>
#include <dlfcn.h>


#include "zygisk.hpp"

using zygisk::Api;
using zygisk::AppSpecializeArgs;
using zygisk::ServerSpecializeArgs;

#define TAG "Zygisk-IMPAD"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static bool s_is_hook_enable = false;
static char *s_app_data_dir = nullptr;
static constexpr const char *s_hook_targets[] = {
        "com.tencent.mm",
        "com.tencent.mobileqq", "com.tencent.wework",
        "com.alibaba.android.rimet"
};
static std::vector<uint8_t> s_lib_dexkit;
static std::vector<uint8_t> s_lib_pine;
static std::vector<uint8_t> s_dex;

static bool read_module_file(int module_fd, const char *src, std::vector<uint8_t> &out) {
    auto src_fd = openat(module_fd, src, O_RDONLY);
    struct stat st{};
    if (fstat(src_fd, &st) == -1) {
        close(src_fd);
        return false;
    }
    out.resize(st.st_size);

    size_t total = 0;
    while (total < out.size()) {
        ssize_t n = read(src_fd, out.data() + total, out.size() - total);
        if (n < 0) {
            close(src_fd);
            return false;
        }
        if (n == 0) break;
        total += n;
    }
    close(src_fd);
    return true;
}

static bool write_file(const char *path, const std::vector<uint8_t> &data) {
    char dir[512];
    strncpy(dir, path, sizeof(dir));
    dir[sizeof(dir) - 1] = 0;
    char *slash = strrchr(dir, '/');
    if (slash) {
        *slash = 0;
        struct stat st{};
        if (stat(dir, &st) == -1) {
            if (mkdir(dir, 0755) == -1) {
                return false;
            }
        }
    }
    chmod(path, 0644);
    int fd = open(path, O_CREAT | O_WRONLY | O_TRUNC, 0644);
    if (fd == -1) {
        LOGE("Failed to open %s: %s", path, strerror(errno));
        return false;
    }

    size_t total = 0;
    while (total < data.size()) {
        ssize_t n = write(fd, data.data() + total, data.size() - total);
        if (n < 0) {
            close(fd);
            return false;
        }
        total += n;
    }

    fchmod(fd, (strstr(path, ".so") != nullptr) ? 0511 : 0400);
    close(fd);
    LOGI("Wrote %s, size = %zu", path, data.size());
    return true;
}

static void load_dex(JNIEnv *env, const char *dex_path, jstring jarg1_package_name,
                     jstring jarg2_app_data_dir) {

    auto j_dex_path = env->NewStringUTF(dex_path);
    auto target_cls_name = env->NewStringUTF("com.houvven.impad.zygisk.Main");

    // @formatter:off
    auto loader_clz = env->FindClass("java/lang/ClassLoader");
    auto load_class_m = env->GetMethodID(loader_clz, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
    auto get_sys_cls_loader_m = env->GetStaticMethodID(loader_clz, "getSystemClassLoader", "()Ljava/lang/ClassLoader;");
    auto sys_cls_loader_obj = env->CallStaticObjectMethod(loader_clz, get_sys_cls_loader_m);

    auto path_dex_cls_loader_clz = env->FindClass("dalvik/system/PathClassLoader");
    auto path_dex_cls_loader_ctor = env->GetMethodID(path_dex_cls_loader_clz, "<init>", "(Ljava/lang/String;Ljava/lang/ClassLoader;)V");
    auto path_dex_cls_loader_obj = env->NewObject(path_dex_cls_loader_clz, path_dex_cls_loader_ctor, j_dex_path, sys_cls_loader_obj);

    auto target_cls = (jclass) env->CallObjectMethod(path_dex_cls_loader_obj, load_class_m, target_cls_name);
    auto target_m = env->GetStaticMethodID(target_cls, "run", "(Ljava/lang/String;Ljava/lang/String;)V");

    env->CallStaticVoidMethod(target_cls, target_m, jarg1_package_name, jarg2_app_data_dir);

    // @formatter:on
}


class ZygiskModule : public zygisk::ModuleBase {
public:
    void onLoad(Api *api, JNIEnv *env) override {
        this->api = api;
        this->env = env;
    }

    void preAppSpecialize(AppSpecializeArgs *args) override {
        auto module_dir_fd = api->getModuleDir();
        if (module_dir_fd == -1) {
            LOGE("Failed to get module dir");
            return;
        }

        auto j_nice_name = args->nice_name;
        auto j_app_data_dir = args->app_data_dir;
        auto nice_name = env->GetStringUTFChars(j_nice_name, nullptr);
        auto app_data_dir = env->GetStringUTFChars(j_app_data_dir, nullptr);

        for (const auto &target: s_hook_targets) {
            if (strcmp(nice_name, target) == 0) {
                s_is_hook_enable = true;
                s_app_data_dir = strdup(app_data_dir);
                LOGI("Hook enable for %s", target);

                if (s_lib_dexkit.empty())
                    read_module_file(module_dir_fd, "lib/libdexkit.so", s_lib_dexkit);
                if (s_lib_pine.empty())
                    read_module_file(module_dir_fd, "lib/libpine.so", s_lib_pine);
                if (s_dex.empty())
                    read_module_file(module_dir_fd, "impad.dex", s_dex);

                break;
            }
        }
        env->ReleaseStringUTFChars(j_nice_name, nice_name);
        env->ReleaseStringUTFChars(j_app_data_dir, app_data_dir);
    }

    void postAppSpecialize(const zygisk::AppSpecializeArgs *args) override {
        if (!s_is_hook_enable) return;
        if (!args->is_top_app) return;

        char lib_path[PATH_MAX];
        char lib_dexkit_path[PATH_MAX];
        char lib_pine_path[PATH_MAX];
        char dex_path[PATH_MAX];

        snprintf(lib_path, sizeof(lib_path), "%s/lib", s_app_data_dir);
        snprintf(lib_dexkit_path, sizeof(lib_dexkit_path), "%s/libdexkit.so", lib_path);
        snprintf(lib_pine_path, sizeof(lib_pine_path), "%s/libpine.so", lib_path);
        snprintf(dex_path, sizeof(dex_path), "%s/impad.dex", s_app_data_dir);

        if (!s_lib_dexkit.empty()) write_file(lib_dexkit_path, s_lib_dexkit);
        if (!s_lib_pine.empty()) write_file(lib_pine_path, s_lib_pine);
        if (!s_dex.empty()) write_file(dex_path, s_dex);
        load_dex(env, dex_path, args->nice_name, args->app_data_dir);
    }


private:
    Api *api;
    JNIEnv *env;
};

REGISTER_ZYGISK_MODULE(ZygiskModule);