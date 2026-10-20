package com.lovelev.mediadock.data.source

import com.lovelev.mediadock.core.model.SourceType
import com.lovelev.mediadock.core.model.SourceId
import com.lovelev.mediadock.data.local.MediaLibraryStore
import com.lovelev.mediadock.data.local.SourceDao
import com.lovelev.mediadock.data.local.SourceEntity
import com.lovelev.mediadock.data.prefs.SecureCredentialStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 数据源注册表：加密配置在 [SecureCredentialStore]，这里维护内存快照与 `sources` 表镜像。
 *
 * 播放服务里的 Media3 DataSource 不能挂起，必须从 [cachedAll] 同步取到当前源与凭据；
 * 因此每次读写都会刷新一个 `@Volatile` Map。
 */
class SourceRepository(
    private val store: SecureCredentialStore,
    private val sourceDao: SourceDao,
    private val libraryStore: MediaLibraryStore,
) {

    private val _sources = MutableStateFlow<List<SourceConfig>>(emptyList())
    val sources: StateFlow<List<SourceConfig>> = _sources.asStateFlow()

    @Volatile
    private var snapshot: Map<String, SourceConfig> = emptyMap()

    fun cached(id: String): SourceConfig? = snapshot[id]

    fun cachedAll(): List<SourceConfig> = snapshot.values.toList()

    fun knownIds(): Set<String> = snapshot.keys

    suspend fun refresh(): List<SourceConfig> {
        val list = store.loadSources()
        publish(list)
        return list
    }

    suspend fun get(id: String): SourceConfig? {
        val list = store.loadSources()
        publish(list)
        return list.firstOrNull { it.id == id }
    }

    suspend fun save(config: SourceConfig) {
        store.saveSource(config)
        val existing = sourceDao.byId(config.id)
        sourceDao.upsert(config.toEntity(createdAt = existing?.createdAt ?: System.currentTimeMillis()))
        publish(store.loadSources())
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        store.setSourceEnabled(id, enabled)
        store.loadSources().firstOrNull { it.id == id }?.let { config ->
            val existing = sourceDao.byId(id)
            sourceDao.upsert(config.toEntity(createdAt = existing?.createdAt ?: System.currentTimeMillis()))
        }
        publish(store.loadSources())
    }

    suspend fun remove(id: String) {
        store.removeSource(id)
        sourceDao.delete(id)
        sourceDao.clearDirectoryCache(id)
        sourceDao.clearScanQueue(id)
        libraryStore.removeSourceData(SourceId(id))
        publish(store.loadSources())
    }

    /** 删除源前的影响统计：该源当前可见文件数。 */
    suspend fun visibleFileCount(id: String): Int = sourceDao.visibleFileCount(id)

    private fun publish(list: List<SourceConfig>) {
        _sources.value = list
        snapshot = list.associateBy { it.id }
    }

    private fun SourceConfig.toEntity(createdAt: Long): SourceEntity = SourceEntity(
        id = id,
        type = type.name,
        name = name,
        baseUrl = url.takeIf { it.isNotBlank() },
        rootPath = rootPath.takeIf { it.isNotBlank() },
        credentialRef = id,
        enabled = if (enabled) 1 else 0,
        priority = priority,
        createdAt = createdAt,
    )
}
