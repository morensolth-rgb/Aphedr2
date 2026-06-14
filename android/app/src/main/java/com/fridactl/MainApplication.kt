package com.fridactl

import android.app.Application
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactNativeHost
import com.facebook.react.ReactPackage
import com.facebook.react.defaults.DefaultReactNativeHost
import com.facebook.soloader.SoLoader

class MainApplication : Application(), ReactApplication {

    override val reactNativeHost: ReactNativeHost =
        object : DefaultReactNativeHost(this) {

            override fun getPackages(): List<ReactPackage> {
                return PackageList(this).packages.apply {
                    add(RootBridgePackage())
                }
            }

            override fun getJSMainModuleName() = "index"

            override fun getUseDeveloperSupport() = BuildConfig.DEBUG
        }

    override fun onCreate() {
        super.onCreate()
        SoLoader.init(this, false)
    }
}
