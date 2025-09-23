package com.houvven.impad.zygisk;

import static com.v7878.unsafe.Reflection.getHiddenMethod;

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

import java.lang.reflect.Method;
import java.util.List;

@SuppressWarnings("unused")
@DoNotShrinkType
@DoNotObfuscateType
public class Main {

    private static final String TAG = "Zygisk-IAMPAD";

    private static final String LOADED_APK_CLASS = "android.app.LoadedApk";

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
        System.loadLibrary("dexkit");
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
            String accessTargetPackage = AccessI.INSTANCE.getPackageName(thiz);
            ClassLoader accessTargetClassLoader = AccessI.INSTANCE.mClassLoader(thiz);

        }, EntryPointType.DIRECT);
    }
}
