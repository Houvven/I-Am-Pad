package com.houvven.impad.zygisk;

import static com.houvven.impad.finder.TargetMethodFinder.dingtalk_is_fold_device;
import static com.houvven.impad.finder.TargetMethodFinder.wechat_check_login_as_pad;
import static com.houvven.impad.finder.TargetMethodFinder.wechat_is_fold_device;
import static com.houvven.impad.finder.TargetMethodFinder.wework_is_pad;
import static java.lang.String.format;

import android.annotation.SuppressLint;
import android.util.Log;

import com.houvven.impad.finder.TargetMethodFinder;

import java.lang.reflect.Method;

import top.canyie.pine.PineConfig;


@SuppressWarnings("unused")
public class Main {

    private static final String TAG = "Zygisk-IMPAD";

    @SuppressLint({"UnsafeDynamicallyLoadedCode", "PrivateApi"})
    public static void run(String packageName, String appDataDir) throws ClassNotFoundException {
        Log.i(TAG, format("start zygisk-module, package: %s, data dir: %s", packageName, appDataDir));

        PineConfig.debug = true;
        PineConfig.debuggable = true;
        PineConfig.libLoader = () -> {
            try {
                System.load(appDataDir + "/files/lib/libpine.so");
            } catch (Throwable e) {
                Log.e(TAG, "load libpine.so failed: " + e.getMessage());
            }
        };

        TargetMethodFinder.setDexkitLibLoader(() -> {
            try {
                System.load(appDataDir + "/files/lib/libdexkit.so");
            } catch (Throwable e) {
                Log.e(TAG, "load libdexkit.so failed: " + e.getMessage());
            }
        });

        if ("com.tencent.mm".equals(packageName)) {
            Log.i(TAG, "start wechat hook.");
            HookUtils.afterApplicationAttach(context -> {
                Method check_login_as_pad = wechat_check_login_as_pad(context);
                Method is_fold_device = wechat_is_fold_device(context);
                HookUtils.replace2true(check_login_as_pad, is_fold_device);
            });
        } else if ("com.tencent.wework".equals(packageName)) {
            Log.i(TAG, "start wework hook.");
            HookUtils.afterApplicationAttach(context -> HookUtils.replace2true(wework_is_pad(context)));
        } else if ("com.alibaba.android.rimet".equals(packageName)) {
            Log.i(TAG, "start dingtalk hook.");
            HookUtils.afterApplicationAttach(context -> HookUtils.replace2true(dingtalk_is_fold_device(context)));
        }
    }
}