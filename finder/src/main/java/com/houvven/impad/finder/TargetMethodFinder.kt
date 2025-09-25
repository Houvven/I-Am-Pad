package com.houvven.impad.finder

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.toClass
import com.highcapable.kavaref.extension.toClassOrNull
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.coroutines.Continuation

@Suppress("FunctionName")
object TargetMethodFinder {

    private const val TAG = "TargetMethodFinder"

    private lateinit var dexkit: DexKitBridge

    private lateinit var dexkitPrefs: SharedPreferences

    private const val DEX_KIT_PREFS_NAME = "i.am.pad.dexkit"

    @JvmStatic
    var dexkitLibLoader: Runnable = Runnable {
        System.loadLibrary("dexkit")
    }

    @JvmStatic
    fun wechat_is_fold_device(context: Context) =
        findOrLoadMethod(context, "is_fold_device") {
            findMethod {
                searchPackages("com.tencent.mm.ui")
                matcher {
                    modifiers(Modifier.PUBLIC or Modifier.STATIC)
                    paramCount(0)
                    usingStrings("royole", "tecno", "ro.os_foldable_screen_support")
                    returnType(Boolean::class.javaPrimitiveType!!)
                }
            }.single().toDexMethod()
        }

    @JvmStatic
    fun wechat_check_login_as_pad(context: Context) =
        findOrLoadMethod(context, "check_login_as_pad") {
            findMethod {
                excludePackages("android", "androidx", "com")
                matcher {
                    modifiers(Modifier.PUBLIC or Modifier.FINAL)
                    paramCount(3)
                    paramTypes(
                        String::class.java,
                        String::class.java,
                        Continuation::class.java
                    )
                    usingStrings(
                        "MicroMsg.CgiCheckLoginAsPad",
                        "/cgi-bin/micromsg-bin/checkloginaspad"
                    )
                }
            }.single().toDexMethod()
        }

    @JvmStatic
    fun dingtalk_is_fold_device(context: Context) =
        findOrLoadMethod(context, "is_fold_device") {
            findMethod {
                searchPackages("com.alibaba.android.dingtalkbase.foldable")
                matcher {
                    modifiers(Modifier.STATIC)
                    paramCount(1)
                    paramTypes(Activity::class.java)
                    returnType(Boolean::class.javaPrimitiveType!!)
                    usingStrings("isMultiLoginFoldableDevice")
                }
            }.single().toDexMethod()
        }

    @JvmStatic
    fun custom_wework_is_pad_judge(context: Context) =
        findOrLoadMethod(context, "is_pad_judge") {
            findMethod {
                matcher {
                    declaredClass("com.tencent.wework.common.utils.WwUtil")
                    returnType(Boolean::class.javaPrimitiveType!!)
                    paramCount(0)
                    modifiers(Modifier.STATIC)
                    usingStrings(
                        "isPadJudge",
                        "isPadWhiteListFromServer", "isPadBlackListFromServer",
                        "isPadWhiteListFromLocal", "isPadBlackListFromLocal"
                    )
                }
            }.single().toDexMethod()
        }

    @JvmStatic
    fun wework_is_pad(context: Context) =
        "com.tencent.wework.foundation.impl.WeworkServiceImpl".toClass(context.classLoader)
            .resolve()
            .method {
                name { it.startsWith("isAndroidPad") }
                returnType(Boolean::class)
            }.map { it.self }

    @JvmStatic
    fun application_attachBase() =
        Application::class.resolve()
            .firstMethod {
                name = "attachBaseContext"
                superclass()
            }.self

    @JvmStatic
    @JvmOverloads
    fun tinker_application_attach(classLoader: ClassLoader? = null) =
        "com.tencent.tinker.loader.app.TinkerApplication".toClassOrNull(classLoader)?.run {
            resolve().firstMethod {
                name = "onBaseContextAttached"
            }.self
        }

    @SuppressLint("UseKtx", "UnsafeDynamicallyLoadedCode")
    private fun findOrLoadMethod(
        context: Context,
        cacheKey: String,
        finder: DexKitBridge.() -> DexMethod
    ): Method {
        val classLoader = context.classLoader
        if (!::dexkitPrefs.isInitialized) {
            dexkitPrefs = context.getSharedPreferences(DEX_KIT_PREFS_NAME, Context.MODE_PRIVATE)
        }
        return try {
            DexMethod(dexkitPrefs.getString(cacheKey, "")!!)
                .getMethodInstance(classLoader).also {
                    Log.d(TAG, "Loaded cached method [$cacheKey]: $it")
                }
        } catch (t: Throwable) {
            if (t !is NoSuchMethodException && t !is IllegalAccessError) throw t
            if (!::dexkit.isInitialized) {
                dexkitLibLoader.run()
                dexkit = DexKitBridge.create(classLoader, true)
            }
            dexkit.finder().run {
                dexkitPrefs.edit().putString(cacheKey, serialize()).apply()
                getMethodInstance(classLoader).also {
                    Log.d(TAG, "Found new method [$cacheKey]: $it")
                }
            }
        }
    }
}