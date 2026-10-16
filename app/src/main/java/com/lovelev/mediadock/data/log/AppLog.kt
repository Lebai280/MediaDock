package com.lovelev.mediadock.data.log

import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 落盘的日志。
 *
 * 为什么要它：App 装在朋友手机上，出问题时他只能截个图或者连电脑看 logcat ——
 * 前者常常截不全，后者对他来说根本不现实。有了这个，**设置里直接能看、能复制**。
 *
 * 两条纪律：
 *
 *  - **只记该记的**。这个文件会留在用户手机里、也可能被他复制给别人，
 *    所以**绝不写凭据、URL 里的密码、文件内容** —— 只写「发生了什么」。
 *  - 单个文件上限 [MAX_BYTES]，超了就滚动一份旧的。不做多份 —— 那点历史不如省点空间。
 */
object AppLog {

    private const val TAG = "MediaDock"
    private const val FILE_NAME = "app.log"
    private const val ROTATED_NAME = "app.1.log"
    private const val MAX_BYTES = 512L * 1024

    private val stamp = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private var dir: File? = null
    private val lock = Any()

    /** 在 Application 里调一次。传 null 就退回「只打 logcat」（单测/异常环境）。 */
    fun init(logDir: File?) {
        synchronized(lock) {
            dir = logDir?.also { if (!it.exists()) it.mkdirs() }
        }
        installCrashHandler()
    }

    fun d(tag: String, message: String) {
        Log.d(TAG, "[$tag] $message")
        write("D", tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(TAG, "[$tag] $message", throwable)
        val detail = if (throwable == null) message else "$message\n${throwable.stackTraceToString()}"
        write("E", tag, detail)
    }

    /** 供日志页读。文件不存在返回空串。 */
    fun read(): String {
        val f = file() ?: return ""
        return synchronized(lock) { runCatching { f.readText() }.getOrDefault("") }
    }

    fun clear() {
        val f = file() ?: return
        synchronized(lock) {
            runCatching { f.writeText("") }
            runCatching { file(ROTATED_NAME)?.delete() }
        }
    }

    fun fileSizeBytes(): Long = file()?.let { runCatching { it.length() }.getOrDefault(0L) } ?: 0L

    private fun file(name: String = FILE_NAME): File? = dir?.let { File(it, name) }

    private fun write(level: String, tag: String, body: String) {
        val f = file() ?: return
        synchronized(lock) {
            runCatching {
                rotateIfNeeded(f)
                f.appendText("${stamp.format(Date())} $level/$tag $body\n")
            }
        }
    }

    private fun rotateIfNeeded(f: File) {
        if (!f.exists() || f.length() < MAX_BYTES) return
        val rotated = file(ROTATED_NAME) ?: return
        runCatching {
            if (rotated.exists()) rotated.delete()
            f.renameTo(rotated)
        }
    }

    /**
     * 未捕获异常也记一份。
     *
     * **这是这个功能最值钱的部分** —— 朋友那边崩了，日志里就有堆栈，
     * 不用他描述「点了什么就闪退了」。记完照旧交给系统默认处理器（该崩还是崩，不掩盖）。
     */
    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            e("CRASH", "未捕获异常 @ ${thread.name}", error)
            previous?.uncaughtException(thread, error)
        }
    }
}
