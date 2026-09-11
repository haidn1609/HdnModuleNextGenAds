package com.hdn.adsmodule.ads.inter

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.appcompat.app.AppCompatActivity

import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.common.AdValue as SdkAdValue
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.hdn.adsmodule.ads.AdUnitParser
import com.hdn.adsmodule.ads.AdsController
import com.hdn.adsmodule.ads.AdsIdConfig
import com.hdn.adsmodule.ads.AdsManager
import com.hdn.adsmodule.base.ui.LoadingDialog
import com.hdn.adsmodule.model.AdValue
import com.hdn.adsmodule.model.AdsLog

import java.util.Date
import kotlin.collections.ifEmpty
import kotlin.run

typealias Callback = (() -> Unit)
// Callback show inter: true = show thành công, false = không show được
typealias InterCallback = ((Boolean) -> Unit)

@Suppress("unused")
object InterAds {
    private val interIdDefault: List<String>
        get() = AdUnitParser.parse(
            if (AdsController.isDebug) {
                AdsIdConfig.Debug.INTERSTITIAL
            } else {
                AdsIdConfig.Remote.INTERSTITIAL
            },
            if (AdsController.isDebug) AdsIdConfig.Debug.INTERSTITIAL else AdsIdConfig.Release.BANNER
        )

    private var currentAdUnitIds: List<String> = emptyList()
    private var mInterstitialAd: InterstitialAd? = null
    private var isLoading = false
    var isShowing: Boolean = false
    private var isCoolingDown: Boolean = false
    private var loadTimeAd: Long = 0
    var interAdsTime = 45000L
        private set

    // Cấu hình thời gian cooldown giữa 2 lần show inter (ms)
    @JvmStatic
    fun setInterAdsTime(timeMillis: Long) {
        interAdsTime = timeMillis
    }

    @JvmStatic
    @JvmOverloads
    fun initInterAds(
        adUnitIds: List<String> = interIdDefault,
        isForceReload: Boolean = false,
        onLoadSuccess: (() -> Unit)? = null,
        onLoadError: (() -> Unit)? = null,
        useWithoutVip: Boolean = false
    ) {
        currentAdUnitIds = adUnitIds.ifEmpty { interIdDefault }
        if (!AdsController.canShowAds(useWithoutVip)) {
            clear()
            onLoadSuccess?.invoke()
            return
        }

        if (!isCanLoadAds && !isForceReload) {
            onLoadError?.invoke()
            return
        }

        if (isLoading) {
            return
        }


        mInterstitialAd = null
        isLoading = true

        loadInterstitialByIndex(
            ids = currentAdUnitIds,
            index = 0,
            onLoadSuccess = onLoadSuccess,
            onLoadError = onLoadError
        )
    }

    private fun loadInterstitialByIndex(
        ids: List<String>,
        index: Int,
        onLoadSuccess: (() -> Unit)?,
        onLoadError: (() -> Unit)?
    ) {
        if (index >= ids.size) {
            mInterstitialAd = null
            isLoading = false
            onLoadError?.invoke()
            return
        }
        AdsManager.onAdsLog(AdsLog(AdsLog.Type.INTER, ids[index], AdsLog.Action.LOAD, AdsLog.Mess.START_LOAD, null))
        // NextGen: load static, adUnitId nằm trong AdRequest, không cần context
        InterstitialAd.load(
            AdRequest.Builder(ids[index]).build(),
            object : AdLoadCallback<InterstitialAd> {
                override fun onAdLoaded(ad: InterstitialAd) {
                    AdsManager.onAdsLog(AdsLog(AdsLog.Type.INTER, ids[index], AdsLog.Action.LOAD, AdsLog.Mess.LOAD_SUCCESS, null))
                    mInterstitialAd = ad
                    isLoading = false
                    loadTimeAd = Date().time
                    onLoadSuccess?.invoke()
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    AdsManager.onAdsLog(AdsLog(AdsLog.Type.INTER, ids[index], AdsLog.Action.LOAD, AdsLog.Mess.LOAD_FAILED, adError))
                    loadInterstitialByIndex(
                        ids = ids,
                        index = index + 1,
                        onLoadSuccess = onLoadSuccess,
                        onLoadError = onLoadError
                    )
                }
            }
        )
    }

    private val isCanLoadAds: Boolean
        get() {
            if (isLoading || isShowing) return false
            if (currentAdUnitIds.isEmpty()) return false
            if (mInterstitialAd == null) return true
            return isAdsOverdue
        }

    val isCanShowAds: Boolean
        get() {
            if (isLoading || isShowing || isCoolingDown) return false
            if (mInterstitialAd == null) return false
            return !isAdsOverdue
        }

    val isCanForceShowAds: Boolean
        get() {
            if (isLoading || isShowing) return false
            if (mInterstitialAd == null) return false
            return !isAdsOverdue
        }

    private val isAdsOverdue: Boolean
        get() = Date().time - loadTimeAd > 3600000 * 4

    // forceShow = load-and-show, hiện loading dialog trong module.
    // fakeLoadingTime=0: loading theo thời gian load thật (như cũ).
    // fakeLoadingTime>0: hiện loading, chờ đúng fake time (tranh thủ load) rồi mới show nếu ad đã sẵn sàng.
    @JvmStatic
    @JvmOverloads
    fun forceShowAdsBreak(
        activity: Activity,
        useWithoutVip: Boolean = false,
        autoCache: Boolean = true,
        fakeLoadingTime: Long = 0L,
        callback: InterCallback?
    ) {
        isCoolingDown = false
        AdsManager.onAdsLog(AdsLog(AdsLog.Type.INTER, "", AdsLog.Action.FORCE_SHOW, AdsLog.Mess.CALL_SHOW, null))
        if (!AdsController.canShowAds(useWithoutVip)) {
            callback?.invoke(false)
            return
        }
        if (activity !is AppCompatActivity) {
            AdsManager.onAdsLog(AdsLog(AdsLog.Type.INTER, "", AdsLog.Action.FORCE_SHOW, AdsLog.Mess.ERR_NOT_ACTIVITY, null))
            callback?.invoke(false)
            return
        }
        // Đang show -> không show chồng
        if (isShowing) {
            AdsManager.onAdsLog(AdsLog(AdsLog.Type.INTER, "", AdsLog.Action.FORCE_SHOW, AdsLog.Mess.ERR_BUSY, null))
            callback?.invoke(false)
            return
        }

        // Fake loading: có ad sẵn -> chờ đủ fake time rồi show. Chưa có ad -> load, show khi xong nhưng tối thiểu fake time.
        if (fakeLoadingTime > 0) {
            if (isCanForceShowAds) {
                LoadingDialog.show(activity)
                handler.postDelayed({
                    LoadingDialog.dismiss()
                    if (isCanForceShowAds) {
                        showAdsFull(activity, useWithoutVip, autoCache, callback)
                    } else {
                        callback?.invoke(false)
                    }
                }, fakeLoadingTime)
                return
            }
            if (isLoading) {
                AdsManager.onAdsLog(AdsLog(AdsLog.Type.INTER, "", AdsLog.Action.FORCE_SHOW, AdsLog.Mess.ERR_BUSY, null))
                callback?.invoke(false)
                return
            }
            LoadingDialog.show(activity)
            val startTime = SystemClock.elapsedRealtime()
            initInterAds(
                isForceReload = true,
                onLoadSuccess = {
                    // Đảm bảo loading hiển thị đủ fakeLoadingTime dù ad load nhanh hơn
                    val remaining = fakeLoadingTime - (SystemClock.elapsedRealtime() - startTime)
                    handler.postDelayed({
                        LoadingDialog.dismiss()
                        if (isCanForceShowAds) {
                            showAdsFull(activity, useWithoutVip, autoCache, callback)
                        } else {
                            callback?.invoke(false)
                        }
                    }, remaining.coerceAtLeast(0))
                },
                onLoadError = {
                    LoadingDialog.dismiss()
                    callback?.invoke(false)
                },
                useWithoutVip = useWithoutVip
            )
            return
        }

        // Có ad sẵn -> show luôn, khỏi loading
        if (isCanForceShowAds) {
            showAdsFull(activity, useWithoutVip, autoCache, callback)
            return
        }
        // Đang load dở -> không show chồng
        if (isLoading) {
            AdsManager.onAdsLog(AdsLog(AdsLog.Type.INTER, "", AdsLog.Action.FORCE_SHOW, AdsLog.Mess.ERR_BUSY, null))
            callback?.invoke(false)
            return
        }
        // Chưa có ad -> bắn loading, load xong show luôn
        LoadingDialog.show(activity)
        initInterAds(
            isForceReload = true,
            onLoadSuccess = {
                LoadingDialog.dismiss()
                if (isCanForceShowAds) {
                    showAdsFull(activity, useWithoutVip, autoCache, callback)
                } else {
                    callback?.invoke(false)
                }
            },
            onLoadError = {
                LoadingDialog.dismiss()
                callback?.invoke(false)
            },
            useWithoutVip = useWithoutVip
        )
    }

    @JvmStatic
    @JvmOverloads
    fun showAdsBreak(
        activity: Activity?,
        useWithoutVip: Boolean = false,
        fakeLoadingTime: Long = 0L,
        callback: InterCallback?
    ) {
        AdsManager.onAdsLog(AdsLog(AdsLog.Type.INTER, "", AdsLog.Action.SHOW, AdsLog.Mess.CALL_SHOW, null))

        // VIP/tắt ads mà không useWithoutVip -> không hiện loading, trả callback false luôn
        if (!AdsController.canShowAds(useWithoutVip)) {
            callback?.invoke(false)
            return
        }

        // Fake loading: có ad sẵn -> chờ đủ fake time rồi show. Chưa có ad -> load, show khi xong nhưng tối thiểu fake time.
        // Đang trong cooldown interAdsTime -> bỏ qua, không show loading (rơi xuống nhánh dưới -> callback false).
        if (fakeLoadingTime > 0 && activity is AppCompatActivity && !isCoolingDown) {
            if (isCanShowAds) {
                LoadingDialog.show(activity)
                handler.postDelayed({
                    LoadingDialog.dismiss()
                    if (isCanShowAds) {
                        showAdsFull(activity, useWithoutVip, callback = callback)
                    } else {
                        callback?.invoke(false)
                    }
                }, fakeLoadingTime)
                return
            }
            if (isLoading) {
                AdsManager.onAdsLog(AdsLog(AdsLog.Type.INTER, "", AdsLog.Action.SHOW, AdsLog.Mess.ERR_CALL_SHOW, null))
                callback?.invoke(false)
                return
            }
            LoadingDialog.show(activity)
            val startTime = SystemClock.elapsedRealtime()
            initInterAds(
                useWithoutVip = useWithoutVip,
                onLoadSuccess = {
                    val remaining = fakeLoadingTime - (SystemClock.elapsedRealtime() - startTime)
                    handler.postDelayed({
                        LoadingDialog.dismiss()
                        if (isCanShowAds) {
                            showAdsFull(activity, useWithoutVip, callback = callback)
                        } else {
                            callback?.invoke(false)
                        }
                    }, remaining.coerceAtLeast(0))
                },
                onLoadError = {
                    LoadingDialog.dismiss()
                    callback?.invoke(false)
                }
            )
            return
        }

        if (isCanShowAds && activity is AppCompatActivity) {
            showAdsFull(activity, useWithoutVip, callback = callback)
        } else {
            AdsManager.onAdsLog(AdsLog(AdsLog.Type.INTER, "", AdsLog.Action.SHOW, AdsLog.Mess.ERR_CALL_SHOW, null))
            initInterAds(useWithoutVip = useWithoutVip)
            callback?.invoke(false)
        }
    }

    private fun showAdsFull(
        context: AppCompatActivity,
        useWithoutVip: Boolean,
        autoCache: Boolean = true,
        callback: InterCallback?
    ) {
        if (!AdsController.canShowAds(useWithoutVip)) {
            callback?.invoke(false)
            return
        }

        val currentAd = mInterstitialAd ?: run {
            AdsManager.onAdsLog(AdsLog(AdsLog.Type.INTER, "", AdsLog.Action.SHOW_ADS_FULL, AdsLog.Mess.SHOW_FAILED_AD_NULL, null))
            callback?.invoke(false)
            return
        }

        // NextGen: gộp paid + full-screen events vào adEventCallback
        currentAd.adEventCallback = object : InterstitialAdEventCallback {
            override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                AdsManager.onAdsLog(AdsLog(AdsLog.Type.INTER, "", AdsLog.Action.SHOW_ADS_FULL, AdsLog.Mess.SHOW_FAILED, fullScreenContentError))
                mInterstitialAd = null
                isShowing = false
                if (autoCache) initInterAds(useWithoutVip = useWithoutVip)
                callback?.invoke(false)
            }

            override fun onAdShowedFullScreenContent() {
                AdsManager.onAdsLog(AdsLog(AdsLog.Type.INTER, "", AdsLog.Action.SHOW_ADS_FULL, AdsLog.Mess.SHOW_SUCCESS, null))
                isShowing = true
            }

            override fun onAdClicked() {
                AdsManager.onAdsLog(AdsLog(AdsLog.Type.INTER, "", AdsLog.Action.ADS_CLICK, AdsLog.Action.SHOW_ADS_FULL, null))
            }

            override fun onAdPaid(value: SdkAdValue) {
                AdsManager.onAdsPair(
                    AdValue(value, currentAd.getResponseInfo().loadedAdSourceResponseInfo)
                )
            }

            override fun onAdDismissedFullScreenContent() {
                AdsManager.onAdsLog(AdsLog(AdsLog.Type.INTER, "", AdsLog.Action.SHOW_ADS_FULL, AdsLog.Mess.SHOW_DISMISS, null))
                isShowing = false
                mInterstitialAd = null
                startDelay()
                if (autoCache) initInterAds(useWithoutVip = useWithoutVip)
                callback?.invoke(true)
            }
        }

        currentAd.show(context)
    }

    private val handler = Handler(Looper.getMainLooper())
    private val resetCooldownRunnable = Runnable {
        isCoolingDown = false
    }

    fun startDelay() {
        isCoolingDown = true
        handler.removeCallbacks(resetCooldownRunnable)
        handler.postDelayed(resetCooldownRunnable, interAdsTime)
    }

    private fun clear() {
        mInterstitialAd = null
        isLoading = false
        isShowing = false
        isCoolingDown = false
        loadTimeAd = 0
    }
}
