package com.houvven.impad

import android.content.Context
import android.os.Process
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.field
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.highcapable.yukihookapi.hook.type.android.ApplicationClass
import com.highcapable.yukihookapi.hook.type.android.BuildClass
import com.highcapable.yukihookapi.hook.type.java.StringClass
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import java.io.File

@InjectYukiHookWithXposed
object HookEntrance : IYukiHookXposedInit {

    override fun onInit() = YukiHookAPI.configs {
        isDebug = BuildConfig.DEBUG
    }

    override fun onHook() = YukiHookAPI.encase {
        processQQ()
        processWeChat()
        processWeWork()
    }

    private fun PackageParam.processQQ() = loadApp(QQ_PACKAGE_NAME) {
        simulateTabletModel()
        simulateTabletProperties()

        withProcess(mainProcessName) {
            dataChannel.wait(ClearCacheKey) {
                YLog.info("Clear QQ cache")
                File("${appInfo.dataDir}/files/mmkv/Pandora").deleteRecursively()
                File("${appInfo.dataDir}/files/mmkv/Pandora.crc").deleteRecursively()
                Process.killProcess(Process.myPid())
            }
        }
    }

    private fun PackageParam.processWeChat() = loadApp(WECHAT_PACKAGE_NAME) {
        simulateTabletModel()

        withProcess(mainProcessName) {
            dataChannel.wait(ClearCacheKey) {
                YLog.info("Clear WeChat cache")
                File(appInfo.dataDir, ".auth_cache").deleteRecursively()
                Process.killProcess(Process.myPid())
            }
        }
    }

    private fun PackageParam.processWeWork() = loadApp(WEWORK_PACKAGE_NAME) {
        val targetClassName = "com.tencent.wework.foundation.impl.WeworkServiceImpl"
        val targetMethodName = "isAndroidPad"
        ApplicationClass.method {
            name("attach")
        }.hook().after {
            val context = args[0] as Context
            val classLoader = context.classLoader
            val clazz = targetClassName.toClass(classLoader)
            clazz.method { name(targetMethodName) }.hook().replaceToTrue()
        }
    }

    private fun simulateTabletModel() {
        BuildClass.run {
            field { name("MANUFACTURER") }.get(null).set("Xiaomi")
            field { name("BRAND") }.get(null).set("Xiaomi")
            field { name("MODEL") }.get(null).set("23046RP50C")
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