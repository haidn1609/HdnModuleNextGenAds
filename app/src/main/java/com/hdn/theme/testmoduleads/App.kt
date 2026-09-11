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
            // Sample AdMob app ID: ca-app-pub-3940256099942544~3347511713
            InitializationConfig.Builder("ca-app-pub-8048589936179473~2309335696").build()
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
