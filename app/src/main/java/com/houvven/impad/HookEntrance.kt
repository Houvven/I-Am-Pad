package com.houvven.impad

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
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
import java.lang.reflect.Modifier

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

    private fun PackageParam.processWeChat() {
        simulateTabletModel("samsung", "SM-F9560")
        onAppLifecycle {
            onCreate {
                try {
                    SystemPropertiesClass.resolve().firstMethod {
                        name("get")
                        parameters(String::class, String::class)
                        returnType(String::class)
                    }.invoke<String>("ro.product.model", "unknown")?.let { model ->
                        YLog.debug("WeChat got default device model: $model")
                        val authCacheDir = File(appInfo.dataDir, ".auth_cache")
                        authCacheDir.listFiles()?.forEach { dir ->
                            if (dir.listFiles()?.any { it.readText().contains(model) } == true) {
                                YLog.debug("WeChat found original device model in auth cache: $model")
                                authCacheDir.deleteRecursively()
                                Process.killProcess(Process.myPid())
                            }
                        }
                    }
                } catch (e: Throwable) {
                    YLog.error("WeChat error: $e")
                }
            }
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
            val dexKit = DexKitBridge.create(classLoader, true)

            try {
                DexMethod(prefs.getString(cacheKey, "")!!).getMethodInstance(classLoader).also {
                    YLog.debug("DingTalk got isMultiLoginFoldableDevice_method: $it")
                }
            } catch (t: Throwable) {
                if (t !is NoSuchMethodException && t !is IllegalAccessError) {
                    throw t
                }
                dexKit.findMethod {
                    searchPackages("com.alibaba.android.dingtalkbase.foldable")
                    matcher {
                        modifiers(Modifier.STATIC)
                        paramTypes(Activity::class.java)
                        returnType(Boolean::class.javaPrimitiveType!!)
                        usingStrings("isMultiLoginFoldableDevice")
                        addUsingField { type("com.android.alibaba.ip.runtime.IpChange") }
                    }
                }.single().toDexMethod().run {
                    prefs.edit { putString(cacheKey, serialize()) }
                    getMethodInstance(context.classLoader)
                }
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
            val dexkit = DexKitBridge.create(classLoader, true)

            try {
                DexMethod(prefs.getString(cacheKey, "")!!).getMethodInstance(classLoader).also {
                    YLog.debug("CustomWeWork[$packageName] got isPadJudge_method: $it")
                }
            } catch (t: Throwable) {
                if (t !is NoSuchMethodException && t !is IllegalAccessError) {
                    throw t
                }
                dexkit.findMethod {
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
                }.single().toDexMethod().run {
                    prefs.edit { putString("isPadJudge_method", serialize()) }
                    getMethodInstance(classLoader)
                }
            }.hook().replaceToTrue()
        }
    }

    private fun PackageParam.processXhs() {
        onAppLifecycle {
            onCreate {
                "com.xingin.adaptation.device.DeviceInfoContainer".toClass().resolve().run {
                    method { name("isPad") }.hookAll().replaceToTrue()
                    method { name("getSavedDeviceType") }.hookAll().replaceTo("pad")
                }
            }
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
}
