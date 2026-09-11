package com.hdn.theme.testmoduleads

import android.app.Application
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.common.AdActivity
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.hdn.adsmodule.ads.open.OpenAds.disableAdsOpenForActivity
import com.hdn.adsmodule.ads.open.OpenAdsHelper

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this

        MobileAds.initialize(
            this,
            InitializationConfig.Builder("ca-app-pub-8048589936179473~2309335696")
                .setNativeValidatorDisabled() // tắt overlay "AdMob native ad validator" khi test native
                .build()
        ) {
            // Adapter initialization is complete.
        }

        OpenAdsHelper().setup(this)
        disableAdsOpenForActivity(AdActivity::class.java)

        FirebaseApp.initializeApp(this)
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
    }

    companion object {
        var instance: App? = null
            private set
    }
}
