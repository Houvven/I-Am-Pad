package com.houvven.impad

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.hasClass
import com.highcapable.kavaref.extension.toClass
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import com.houvven.impad.finder.TargetMethodFinder
import com.houvven.impad.finder.TargetMethodFinder.custom_wework_is_pad_judge
import com.houvven.impad.finder.TargetMethodFinder.dingtalk_is_fold_device
import com.houvven.impad.finder.TargetMethodFinder.wechat_check_login_as_pad
import com.houvven.impad.finder.TargetMethodFinder.wechat_is_fold_device
import com.houvven.impad.finder.TargetMethodFinder.wework_is_pad
import java.io.File

@InjectYukiHookWithXposed
object HookEntrance : IYukiHookXposedInit {

    private val SystemPropertiesClass = "android.os.SystemProperties".toClass()

    @Suppress("SpellCheckingInspection")
    private val customWeWorkPackages = arrayOf(
        "com.airchina.wecompro",
        "com.zwfw.YueZhengYi",
        "com.cscec.portal",
        "cn.powerchina.pact"
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
                isCustomWeWorkPackage(packageName) -> processCustomWeWork()
                isCustomWeWorkPackage(appClassLoader) -> processCustomWeWork()
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

    private fun PackageParam.processWeChat() = afterApplicationAttach { context ->
        wechat_is_fold_device(context).hook().replaceToTrue()
        wechat_check_login_as_pad(context).hook().replaceToTrue()
    }

    private fun PackageParam.processWeWork() = afterApplicationAttach { context ->
        wework_is_pad(context).hookAll().replaceToTrue()
    }

    private fun PackageParam.processDingTalk() = afterApplicationAttach { context ->
        dingtalk_is_fold_device(context).hook().replaceToTrue()
    }

    @SuppressLint("DuplicateCreateDexKit")
    private fun PackageParam.processCustomWeWork() = afterApplicationAttach { context ->
        custom_wework_is_pad_judge(context).hook().replaceToTrue()
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

    private fun isCustomWeWorkPackage(pkg: String) = pkg in customWeWorkPackages

    private fun isCustomWeWorkPackage(classLoader: ClassLoader?) =
        classLoader?.hasClass("com.tencent.wework.common.utils.WwUtil") == true

    private fun PackageParam.afterApplicationAttach(action: (Context) -> Unit) {
        val actionWrapper: (Context) -> Unit = { context ->
            runCatching { action(context) }.onFailure {
                YLog.error("Failed to execute afterApplicationAttach hook", it)
            }
        }
        val tinkerApplicationAttach = TargetMethodFinder.tinker_application_attach()
        if (tinkerApplicationAttach != null) {
            tinkerApplicationAttach.hook().after { actionWrapper(args[0] as Context) }
        } else {
            Application::class.resolve()
                .firstMethod { name("onCreate") }.hook()
                .before { actionWrapper(instance()) }
        }
    }
}
