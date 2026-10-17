package com.lovelev.mediadock.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo

/**
 * 媒体流的错误重试策略：**401 / 429 一次都不重试**。
 *
 * 这不是普通的“省点请求”。OpenList 的登录失败锁定按来源 IP 计数，而 ExoPlayer 默认策略
 * 把 HTTP 响应错误当成可重试错误（点播默认 3 次）—— 一次播放就会变成 4 个失败请求。
 * 浏览 / 扫描那几道防线管不到媒体流，所以在这里单独拦。
 *
 * 其它错误（超时、断流等）仍交给 [DefaultLoadErrorHandlingPolicy]，保持原有的退避重试。
 */
@OptIn(UnstableApi::class)
internal class WebDavLoadErrorPolicy(
    private val delegate: LoadErrorHandlingPolicy = DefaultLoadErrorHandlingPolicy(),
) : LoadErrorHandlingPolicy by delegate {

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorInfo): Long =
        if (isAuthBlocked(loadErrorInfo.exception)) {
            // C.TIME_UNSET = 不再重试，直接把这一个错误报给上层。
            C.TIME_UNSET
        } else {
            delegate.getRetryDelayMsFor(loadErrorInfo)
        }

    // Kotlin 的接口委托不会转发 Java 接口的 default 方法，这里显式转给 delegate。
    override fun onLoadTaskConcluded(loadTaskId: Long) {
        delegate.onLoadTaskConcluded(loadTaskId)
    }

    internal companion object {

        private val AUTH_BLOCK_CODES = setOf(401, 429)

        /** 401/429 可能被 Loader / DataSource 逐层包进 cause 链，要一路往下看。 */
        fun isAuthBlocked(error: Throwable?): Boolean {
            var current = error
            var depth = 0
            while (current != null && depth++ < MAX_CAUSE_DEPTH) {
                if (current is HttpDataSource.InvalidResponseCodeException &&
                    current.responseCode in AUTH_BLOCK_CODES
                ) {
                    return true
                }
                current = current.cause
            }
            return false
        }

        /** 只防异常链异常长（正常不会超过三五层）。 */
        private const val MAX_CAUSE_DEPTH = 16
    }
}
