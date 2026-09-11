package com.hdn.adsmodule.ads

import android.os.Handler
import android.os.Looper

// NextGen SDK có thể gọi callback trên background thread -> helper chạy block trên main.
// Đã ở main thì chạy luôn (không trễ 1 frame), else post về main.
internal object MainThread {
    private val handler = Handler(Looper.getMainLooper())

    fun run(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block()
        else handler.post(block)
    }
}
