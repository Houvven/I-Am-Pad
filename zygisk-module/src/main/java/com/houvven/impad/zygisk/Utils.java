package com.houvven.impad.zygisk;

import android.annotation.SuppressLint;
import android.content.Context;

import com.v7878.r8.annotations.DoNotObfuscate;
import com.v7878.r8.annotations.DoNotShrink;
import com.v7878.unsafe.ClassUtils;
import com.v7878.unsafe.Reflection;
import com.v7878.unsafe.invoke.EmulatedStackFrame;
import com.v7878.unsafe.invoke.Transformers;
import com.v7878.vmtools.HookTransformer;
import com.v7878.vmtools.Hooks;

import java.util.function.Consumer;

public class Utils {

    public static HookTransformer replace2true = (original, frame) -> frame.accessor().setBoolean(EmulatedStackFrame.RETURN_VALUE_IDX, true);

    @DoNotShrink
    @DoNotObfuscate
    public static void afterHookTinkerApplicationAttach(ClassLoader loader, Consumer<Context> action) {
        try {
            var cls = loader.loadClass("com.tencent.tinker.loader.app.TinkerApplication");
            var method = cls.getDeclaredMethod("onBaseContextAttached", Context.class, long.class, long.class);
            Hooks.hook(method, Hooks.EntryPointType.CURRENT, (original, frame) -> {
                Transformers.invokeExact(original, frame);
                var context = ((Context) frame.accessor().getReference(1));
                action.accept(context);
            }, Hooks.EntryPointType.DIRECT);
        } catch (NoSuchMethodException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @DoNotShrink
    @DoNotObfuscate
    @SuppressLint("DiscouragedPrivateApi")
    public static void afterHookApplicationAttach(ClassLoader loader, Consumer<Context> action) {
        try {
            var cls = ClassUtils.forName("android.app.Application", loader);
            var method = Reflection.getHiddenMethod(cls, "attach", Context.class);
            Hooks.hook(method, Hooks.EntryPointType.CURRENT, (original, frame) -> {
                Transformers.invokeExact(original, frame);
                var context = ((Context) frame.accessor().getReference(1));
                action.accept(context);
            }, Hooks.EntryPointType.DIRECT);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
