package com.houvven.impad

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Process
import androidx.core.content.edit
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.toClass
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.wrap.DexMethod
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.coroutines.Continuation

@InjectYukiHookWithXposed
object HookEntrance : IYukiHookXposedInit {

    private val SystemPropertiesClass = "android.os.SystemProperties".toClass()

    private val Context.dexkitPrefs
        get() = getSharedPreferences("dexkit", Context.MODE_PRIVATE)

    @Suppress("SpellCheckingInspection")
    private val customWeWorkPackages = arrayOf(
        "com.airchina.wecompro",
        "com.zwfw.YueZhengYi"
    )

    private lateinit var dexkit: DexKitBridge

    override fun onInit() = YukiHookAPI.configs {
        isDebug = BuildConfig.DEBUG
        debugLog {
            tag = BuildConfig.APPLICATION_ID
        }
    }

    override fun onHook() = YukiHookAPI.encase {
        loadApp {
            when {
                packageName.contains("com.tencent.mobileqq") -> processQQ()
                packageName.contains("com.tencent.mm") -> processWeChat()
                packageName.contains("com.tencent.wework") -> processWeWork()
                packageName.contains("com.alibaba.android.rimet") -> processDingTalk()
                packageName in customWeWorkPackages -> processCustomWeWork()
                packageName.contains("com.xingin.xhs") -> processXhs()
            }
        }
    }

    private fun PackageParam.processQQ() {
        val targetModel = "23046RP50C"
        simulateTabletModel("Xiaomi", targetModel)
        simulateTabletProperties()

        onAppLifecycle {
            onCreate {
                val preferences = getSharedPreferences("BUGLY_COMMON_VALUES", Context.MODE_PRIVATE)
                val storedModel = preferences.getString("model", targetModel)
                YLog.debug("QQ got default device model: $storedModel")
                if (storedModel != targetModel) {
                    YLog.debug("clear qq cache.")
                    File("${appInfo.dataDir}/files/mmkv/Pandora").deleteRecursively()
                    File("${appInfo.dataDir}/files/mmkv/Pandora.crc").deleteRecursively()
                    Process.killProcess(Process.myPid())
                }
            }
        }
    }

    private fun PackageParam.processWeChat() = onAppLifecycle {
        attachBaseContext { context, hasCalledSuper ->
            System.loadLibrary("dexkit")
            val prefs = context.dexkitPrefs
            val classLoader = context.classLoader

            findOrLoadMethod(prefs, "checkLoginAsPad_method", classLoader) {
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
            }.hook().replaceToTrue()

            findOrLoadMethod(prefs, "isFoldableDevice_method", classLoader) {
                findMethod {
                    searchPackages("com.tencent.mm.ui")
                    matcher {
                        modifiers(Modifier.PUBLIC or Modifier.STATIC)
                        paramCount(0)
                        usingStrings("royole", "tecno", "ro.os_foldable_screen_support")
                        returnType(Boolean::class.javaPrimitiveType!!)
                    }
                }.single().toDexMethod()
            }.hook().replaceToTrue()
        }
    }

    private fun PackageParam.processWeWork() {
        Application::class.resolve().firstMethod {
            name("attach")
        }.hook().after {
            val context = args[0] as Context
            val classLoader = context.classLoader
            "com.tencent.wework.foundation.impl.WeworkServiceImpl".toClass(classLoader)
                .resolve()
                .method {
                    name { it.startsWith("isAndroidPad") }
                    returnType(Boolean::class)
                }
                .hookAll().replaceToTrue()
        }
    }

    private fun PackageParam.processDingTalk() = onAppLifecycle {
        attachBaseContext { context, hasCalledSuper ->
            System.loadLibrary("dexkit")
            val classLoader = context.classLoader
            val prefs = context.dexkitPrefs
            val cacheKey = "isMultiLoginFoldableDevice_method"

            findOrLoadMethod(prefs, cacheKey, classLoader) {
                findMethod {
                    searchPackages("com.alibaba.android.dingtalkbase.foldable")
                    matcher {
                        modifiers(Modifier.STATIC)
                        paramTypes(Activity::class.java)
                        returnType(Boolean::class.javaPrimitiveType!!)
                        usingStrings("isMultiLoginFoldableDevice")
                        addUsingField { type("com.android.alibaba.ip.runtime.IpChange") }
                    }
                }.single().toDexMethod()
            }.hook().replaceToTrue()
        }
    }

    @SuppressLint("DuplicateCreateDexKit")
    private fun PackageParam.processCustomWeWork() = onAppLifecycle {
        attachBaseContext { context, hasCalledSuper ->
            System.loadLibrary("dexkit")
            val classLoader = context.classLoader
            val prefs = context.dexkitPrefs
            val cacheKey = "isPadJudge_method"

            findOrLoadMethod(prefs, cacheKey, classLoader) {
                findMethod {
                    matcher {
                        declaredClass("com.tencent.wework.common.utils.WwUtil")
                        returnType(Boolean::class.javaPrimitiveType!!)
                        paramCount(0)
                        modifiers(Modifier.STATIC)
                        usingStrings("isPadJudge", "isPadWhiteList")
                    }
                }.single().toDexMethod()
            }.hook().replaceToTrue()
        }
    }

    private fun PackageParam.processXhs() {
        "com.xingin.adaptation.device.DeviceInfoContainer".toClass().resolve().run {
            method { name("isPad") }.hookAll().replaceToTrue()
            method { name("getSavedDeviceType") }.hookAll().replaceTo("pad")
        }
    }

    private fun simulateTabletModel(
        brand: String,
        model: String,
        manufacturer: String = brand
    ) {
        Build::class.resolve().run {
            firstField { name("MANUFACTURER") }.set(manufacturer)
            firstField { name("BRAND") }.set(brand)
            firstField { name("MODEL") }.set(model)
        }
    }

    private fun PackageParam.simulateTabletProperties() {
        SystemPropertiesClass.resolve().firstMethod {
            name("get")
            returnType(String::class)
        }.hook().before {
            if (args[0] == "ro.build.characteristics") {
                result = "tablet"
            }
        }
    }

    private fun findOrLoadMethod(
        prefs: SharedPreferences,
        cacheKey: String,
        classLoader: ClassLoader,
        finder: DexKitBridge.() -> DexMethod
    ): Method {
        return try {
            DexMethod(prefs.getString(cacheKey, "")!!)
                .getMethodInstance(classLoader).also {
                    YLog.debug("Loaded cached method [$cacheKey]: $it")
                }
        } catch (t: Throwable) {
            if (t !is NoSuchMethodException && t !is IllegalAccessError) throw t
            if (!::dexkit.isInitialized) {
                System.loadLibrary("dexkit")
                dexkit = DexKitBridge.create(classLoader, true)
            }
            dexkit.finder().run {
                prefs.edit { putString(cacheKey, serialize()) }
                getMethodInstance(classLoader).also {
                    YLog.debug("Found new method [$cacheKey]: $it")
                }
            }
        }
    }
}
