package com.houvven.impad

import android.content.Context
import android.os.Process
import androidx.core.content.edit
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.field
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.highcapable.yukihookapi.hook.type.android.ActivityClass
import com.highcapable.yukihookapi.hook.type.android.ApplicationClass
import com.highcapable.yukihookapi.hook.type.android.BuildClass
import com.highcapable.yukihookapi.hook.type.java.BooleanClass
import com.highcapable.yukihookapi.hook.type.java.BooleanType
import com.highcapable.yukihookapi.hook.type.java.StringClass
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.wrap.DexMethod
import java.io.File
import java.lang.reflect.Modifier

@InjectYukiHookWithXposed
object HookEntrance : IYukiHookXposedInit {

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
                packageName.contains(QQ_PACKAGE_NAME) -> processQQ()
                packageName.contains(WECHAT_PACKAGE_NAME) -> processWeChat()
                packageName.contains(WEWORK_PACKAGE_NAME) -> processWeWork()
                packageName.contains(DING_TALK_PACKAGE_NAME) -> processDingTalk()
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
                    SystemPropertiesClass.method {
                        name("get")
                        param(StringClass, StringClass)
                        returnType(StringClass)
                    }.get(null).invoke<String>("ro.product.model", "unknown")?.let { model ->
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
        val targetClassName = "com.tencent.wework.foundation.impl.WeworkServiceImpl"
        ApplicationClass.method {
            name("attach")
        }.hook().after {
            val context = args[0] as Context
            val classLoader = context.classLoader
            val clazz = targetClassName.toClass(classLoader)
            clazz.method {
                name { it.startsWith("isAndroidPad") }
                returnType(BooleanType)
            }.hookAll().replaceToTrue()
        }
    }

    private fun PackageParam.processDingTalk() {
        val ipChangeClass =
            "com.android.alibaba.ip.runtime.IpChange".toClass()
        searchClass(name = "ding_talk_foldable") {
            from("com.alibaba.android.dingtalkbase.foldable")
            field { type = BooleanClass; modifiers { isStatic } }.count { it >= 2 }
            field { type = ipChangeClass; modifiers { isStatic } }.count(1)
            method { returnType = BooleanType }.count { it >= 6 }
        }.wait { target ->
            if (target == null) {
                return@wait YLog.error("not found ding talk target class.")
            }
            YLog.debug("Ding talk target class ${target.name}")
            target.method { param(ActivityClass); returnType(BooleanType) }.hook().replaceToTrue()
        }
    }

    private fun PackageParam.processCustomWeWork() {
        System.loadLibrary("dexkit")

        ApplicationClass.method {
            name("attach")
        }.hook().after {
            val context = args[0] as Context
            val classLoader = context.classLoader
            val prefs = context.getSharedPreferences("dexkit_cache", Context.MODE_PRIVATE)

            @Suppress("DEPRECATION")
            val versionCode = context.packageManager.getPackageInfo(packageName, 0).versionCode

            val cachedSerialized = prefs.getString("isPadJudge_method", null)
            val cachedVersionCode = prefs.getInt("version_code", 0)
            if (cachedSerialized.isNullOrBlank() || cachedVersionCode != versionCode) {
                DexKitBridge.create(classLoader, true).findMethod {
                    matcher {
                        declaredClass("com.tencent.wework.common.utils.WwUtil")
                        returnType(BooleanType)
                        paramCount(0)
                        modifiers(Modifier.STATIC)
                        usingStrings(
                            "isPadJudge",
                            "isPadWhiteListFromServer", "isPadBlackListFromServer",
                            "isPadWhiteListFromLocal", "isPadBlackListFromLocal"
                        )
                    }
                }.single().toDexMethod().run {
                    getMethodInstance(classLoader).hook().replaceToTrue()
                    prefs.edit {
                        putString("isPadJudge_method", serialize())
                        putInt("version_code", versionCode)
                    }
                }
            } else {
                YLog.debug("Wecompro got cached isPadJudge method.")
                DexMethod(cachedSerialized).getMethodInstance(classLoader).hook().replaceToTrue()
            }
        }
    }

    private fun PackageParam.processXhs() {
        onAppLifecycle {
            onCreate {
                "com.xingin.adaptation.device.DeviceInfoContainer".toClass().run {
                    method { name("isPad") }.hookAll().replaceToTrue()
                    method { name("getSavedDeviceType") }.hookAll().replaceTo("pad")
                }
            }
        }
    }

    private fun PackageParam.simulateTabletModel(
        brand: String,
        model: String,
        manufacturer: String = brand
    ) {
        BuildClass.run {
            field { name("MANUFACTURER") }.get(null).set(manufacturer)
            field { name("BRAND") }.get(null).set(brand)
            field { name("MODEL") }.get(null).set(model)
        }
        SystemPropertiesClass.method {
            name("get")
            param(StringClass, StringClass)
            returnType(StringClass)
        }.hook().after {
            when (args[0]) {
                "ro.product.model" -> result = model
                "ro.product.brand" -> result = brand
                "ro.product.manufacturer" -> result = manufacturer
            }
        }
    }

    private fun PackageParam.simulateTabletProperties() {
        SystemPropertiesClass.method {
            name("get")
            returnType(StringClass)
        }.hook().before {
            if (args[0] == "ro.build.characteristics") {
                result = "tablet"
            }
        }
    }
}
