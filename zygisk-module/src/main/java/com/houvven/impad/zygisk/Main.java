package com.houvven.impad.zygisk;

import static com.houvven.impad.finder.TargetMethodFinder.dingtalk_is_fold_device;
import static com.houvven.impad.finder.TargetMethodFinder.wechat_is_fold_device;
import static com.houvven.impad.finder.TargetMethodFinder.wechat_check_login_as_pad;
import static com.v7878.unsafe.Reflection.getHiddenMethod;

import android.os.Build;
import android.util.Log;

import com.v7878.r8.annotations.DoNotObfuscate;
import com.v7878.r8.annotations.DoNotObfuscateType;
import com.v7878.r8.annotations.DoNotOptimize;
import com.v7878.r8.annotations.DoNotShrink;
import com.v7878.r8.annotations.DoNotShrinkType;
import com.v7878.unsafe.ClassUtils;
import com.v7878.unsafe.access.AccessLinker;
import com.v7878.unsafe.invoke.Transformers;
import com.v7878.vmtools.Hooks;
import com.v7878.vmtools.Hooks.EntryPointType;
import com.v7878.zygisk.ZygoteLoader;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.List;

@SuppressWarnings("unused")
@DoNotShrinkType
@DoNotObfuscateType
public class Main {

    private static final String TAG = "Zygisk-IAMPAD";

    private static final String LOADED_APK_CLASS = "android.app.LoadedApk";

    private static byte[] dexkitBytes;


    @DoNotShrinkType
    @DoNotOptimize
    private abstract static class AccessI {
        @AccessLinker.FieldAccess(kind = AccessLinker.FieldAccessKind.INSTANCE_GETTER, klass = LOADED_APK_CLASS, name = "mClassLoader")
        abstract ClassLoader mClassLoader(Object instance);

        @AccessLinker.ExecutableAccess(kind = AccessLinker.ExecutableAccessKind.VIRTUAL, klass = LOADED_APK_CLASS, name = "getPackageName", args = {})
        abstract String getPackageName(Object instance);

        static final AccessI INSTANCE = AccessLinker.generateImpl(AccessI.class);
    }

    @DoNotShrink
    @DoNotObfuscate
    public static void premain() {
        String moduleDir = ZygoteLoader.getModuleDir();
        String packageName = ZygoteLoader.getPackageName();
        String abi = Build.SUPPORTED_ABIS[0];
        File libDir = new File(moduleDir, "lib/" + abi);
        File dexkitLib = new File(libDir, "libdexkit.so");
        try {
            dexkitBytes = Files.readAllBytes(dexkitLib.toPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("ConfusingMainMethod")
    @DoNotShrink
    @DoNotObfuscate
    public static void main() {
        Log.i(TAG, "Injected into " + ZygoteLoader.getPackageName());
        try {
            start();
        } catch (Throwable e) {
            Log.e(TAG, "Error", e);
        }
    }

    private static void start() {
        Class<?> loadApkClass = ClassUtils.sysClass(LOADED_APK_CLASS);
        Method target = getHiddenMethod(loadApkClass, "createOrUpdateClassLoaderLocked", List.class);

        Hooks.hook(target, EntryPointType.CURRENT, (original, frame) -> {
            Transformers.invokeExact(original, frame);

            var thiz = frame.accessor().getReference(0);
            String packageName = AccessI.INSTANCE.getPackageName(thiz);
            ClassLoader defClassLoader = AccessI.INSTANCE.mClassLoader(thiz);

            if ("com.tencent.mm".equals(packageName)) {
                Utils.afterHookTinkerApplicationAttach(defClassLoader, context -> {
                    Hooks.hook(wechat_is_fold_device(context, dexkitBytes), EntryPointType.CURRENT, Utils.replace2true, EntryPointType.DIRECT);
                    Hooks.hook(wechat_check_login_as_pad(context, dexkitBytes), EntryPointType.CURRENT, Utils.replace2true, EntryPointType.DIRECT);
                });
            } else if ("com.tencent.wework".equals(packageName)) {
                Utils.afterHookTinkerApplicationAttach(defClassLoader, context -> {
                    ClassLoader loader = context.getClassLoader();
                    try {
                        Class<?> targetClass = loader.loadClass("com.tencent.wework.foundation.impl.WeworkServiceImpl");
                        Method isAndroidPad = targetClass.getDeclaredMethod("isAndroidPad");
                        Method isAndroidPadNew = targetClass.getDeclaredMethod("isAndroidPadNew");
                        Hooks.hook(isAndroidPadNew, EntryPointType.CURRENT, Utils.replace2true, EntryPointType.DIRECT);
                        Hooks.hook(isAndroidPad, EntryPointType.CURRENT, Utils.replace2true, EntryPointType.DIRECT);
                    } catch (ClassNotFoundException | NoSuchMethodException e) {
                        throw new RuntimeException(e);
                    }
                });
            } else if ("com.xingin.xhs".equals(packageName)) {
                Class<?> targetClass = defClassLoader.loadClass("com.xingin.adaptation.device.DeviceInfoContainer");
                Method isPad = targetClass.getDeclaredMethod("isPad");
                Method getSavedDeviceType = targetClass.getDeclaredMethod("getSavedDeviceType");
                Hooks.hook(isPad, EntryPointType.CURRENT, Utils.replace2true, EntryPointType.DIRECT);
                Hooks.hook(getSavedDeviceType, EntryPointType.CURRENT, Utils.replace2true, EntryPointType.DIRECT);
            } else if ("com.alibaba.android.rimet".equals(packageName)) {
                Utils.afterHookApplicationAttach(defClassLoader, context -> Hooks.hook(dingtalk_is_fold_device(context, dexkitBytes), EntryPointType.CURRENT, Utils.replace2true, EntryPointType.DIRECT));
            }
        }, EntryPointType.DIRECT);
    }
}