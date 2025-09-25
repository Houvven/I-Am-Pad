package com.houvven.impad.zygisk;

import static com.houvven.impad.finder.TargetMethodFinder.application_attachBase;

import android.content.Context;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;
import top.canyie.pine.callback.MethodReplacement;

public final class HookUtils {

    public static void afterApplicationAttach(Consumer<Context> consumer) {
        Pine.hook(application_attachBase(), new MethodHook() {
            @Override
            public void afterCall(Pine.CallFrame callFrame) throws Throwable {
                Context context = (Context) callFrame.thisObject;
                consumer.accept(context);
            }
        });
    }

    public static void replace2true(Method... method) {
        replace2true(Arrays.stream(method).collect(Collectors.toList()));
    }

    public static void replace2true(Collection<Method> methods) {
        for (Method m : methods) {
            Pine.hook(m, new MethodReplacement() {
                @Override
                protected Object replaceCall(Pine.CallFrame callFrame) throws Throwable {
                    return true;
                }
            });
        }
    }
}
