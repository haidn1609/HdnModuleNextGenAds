package com.hdn.adsmodule.model

import com.google.android.libraries.ads.mobile.sdk.common.AdSourceResponseInfo
import com.google.android.libraries.ads.mobile.sdk.common.AdValue

class AdValue(
    val adValue: AdValue,
    // NextGen: thay AdapterResponseInfo cũ bằng AdSourceResponseInfo (từ responseInfo.loadedAdSourceResponseInfo)
    val adInfo: AdSourceResponseInfo?
)
