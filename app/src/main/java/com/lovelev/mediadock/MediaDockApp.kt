package com.lovelev.mediadock

import android.app.Application
import java.io.File
import com.lovelev.mediadock.data.local.SearchIndexer
import com.lovelev.mediadock.data.log.AppLog
import com.lovelev.mediadock.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MediaDockApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // 日志落盘：出问题时用户能在设置里直接看、直接复制（见 AppLog 的注释）
        AppLog.init(File(filesDir, "logs"))
        container = AppContainer(this)
        // FTS 索引：首次启动或索引版本变化时在后台重建（不阻塞首屏）。
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { SearchIndexer(container.database).ensureFresh() }
        }
    }
}
