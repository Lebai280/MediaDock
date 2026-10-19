package com.lovelev.mediadock.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 库表结构版本。
 *
 * **v9 一次性切到 UUID 身份 + 多集/版本模型**：旧表改名为 `*_legacy` 保留，
 * 旧 UI 读的 `media_items` 等改为兼容 VIEW；写路径全部落到新表。
 *
 * 加表/加列仍必须写真迁移，并放进 [MIGRATIONS]；版本号单一来源 [VERSION]。
 * 升级方向的破坏性回退**绝不启用**（AppContainer 只留降级回退）。
 */
@Database(
    entities = [
        SourceEntity::class,
        MediaFileEntity::class,
        WorkEntity::class,
        EpisodeEntity::class,
        MovieVersionEntity::class,
        EpisodeVersionEntity::class,
        FileSegmentEntity::class,
        MovieProgressEntity::class,
        EpisodeProgressEntity::class,
        FileProgressEntity::class,
        FavoriteEntity::class,
        Playlist::class,
        PlaylistItemEntity::class,
        TitleOverrideEntity::class,
        WorkDetailEntity::class,
        ContentTombstoneEntity::class,
        SubtitleEntity::class,
        ProviderHealthEntity::class,
        ScrapeTaskEntity::class,
        EpisodeMeta::class,
        ScanRunEntity::class,
        DirectoryCacheEntity::class,
        ScanQueueEntity::class,
    ],
    views = [
        MediaItemEntity::class,
        PlaybackProgress::class,
        Favorite::class,
        PlaylistItem::class,
        TitleOverride::class,
        TitleDetail::class,
    ],
    version = MediaDatabase.VERSION,
    exportSchema = true,
)
abstract class MediaDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun progressDao(): ProgressDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun titleOverrideDao(): TitleOverrideDao
    abstract fun titleDetailDao(): TitleDetailDao
    abstract fun episodeMetaDao(): EpisodeMetaDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun writeDao(): MediaLibraryWriteDao
    abstract fun scanDao(): ScanDao
    abstract fun scrapeTaskDao(): ScrapeTaskDao
    abstract fun worksCardDao(): WorksCardDao

    companion object {
        /** 当前库表版本。**单一来源**：`@Database` 与迁移覆盖单测都读它。 */
        const val VERSION = 11

        /** v3 → v4：加收藏表。纯新增，不动既有数据。 */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `favorites` " +
                        "(`path` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`path`))",
                )
            }
        }

        /** v4 → v5：加手工改名的覆盖表。纯新增。 */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `title_overrides` " +
                        "(`path` TEXT NOT NULL, `title` TEXT NOT NULL, `year` INTEGER, " +
                        "PRIMARY KEY(`path`))",
                )
            }
        }

        /** v5 → v6：加详情补充表（类型/国家/时长/演职人员）。纯新增。 */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `title_details` (" +
                        "`path` TEXT NOT NULL, `tmdbId` INTEGER NOT NULL, " +
                        "`releaseDate` TEXT, `runtimeMinutes` INTEGER, " +
                        "`genres` TEXT NOT NULL, `country` TEXT, " +
                        "`castJson` TEXT NOT NULL, `fetchedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`path`))",
                )
            }
        }

        /**
         * v6 → v7：详情表补总集数/总季数；新增分集元数据表（剧照/分集名/时长）。
         * 都是纯新增，不动既有用户数据。
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `title_details` ADD COLUMN `totalEpisodes` INTEGER")
                db.execSQL("ALTER TABLE `title_details` ADD COLUMN `totalSeasons` INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `episode_meta` (" +
                        "`tmdbId` INTEGER NOT NULL, `season` INTEGER NOT NULL, " +
                        "`episode` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
                        "`stillPath` TEXT, `runtimeMinutes` INTEGER, " +
                        "`fetchedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`tmdbId`, `season`, `episode`))",
                )
            }
        }

        /** v7 → v8：新增用户片单与片单条目表。纯新增。 */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playlists` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playlist_items` (" +
                        "`playlistId` INTEGER NOT NULL, `path` TEXT NOT NULL, " +
                        "`addedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`playlistId`, `path`))",
                )
            }
        }

        /**
         * v8 → v9：身份与多集模型切换。实现与校验在 [MigrationV8ToV9]，
         * 这里只挂到迁移链上。
         */
        val MIGRATION_8_9: Migration = MigrationV8ToV9.build()

        /** v9 → v10：扫描代际、目录缓存与持久 BFS 队列（Phase 1 增量扫描）。 */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `scan_runs` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`source_id` TEXT NOT NULL, `mode` TEXT NOT NULL, `status` TEXT NOT NULL, " +
                        "`started_at` INTEGER NOT NULL, `finished_at` INTEGER, " +
                        "`dirs` INTEGER NOT NULL, `files` INTEGER NOT NULL, `error` TEXT)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `directory_cache` (" +
                        "`source_id` TEXT NOT NULL, `remote_key` TEXT NOT NULL, " +
                        "`etag` TEXT, `last_modified` TEXT, `last_scan_run_id` INTEGER, " +
                        "`child_count` INTEGER NOT NULL, PRIMARY KEY(`source_id`, `remote_key`))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_directory_cache_source_id_last_scan_run_id` " +
                        "ON `directory_cache` (`source_id`, `last_scan_run_id`)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `scan_queue` (" +
                        "`source_id` TEXT NOT NULL, `scan_run_id` INTEGER NOT NULL, " +
                        "`remote_key` TEXT NOT NULL, `depth` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`source_id`, `remote_key`))",
                )
            }
        }

        /** v10 → v11：刮削任务按 (content, kind, provider, task_kind) 唯一，重复入队合并。 */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "DELETE FROM `scrape_tasks` WHERE rowid NOT IN (" +
                        "SELECT MIN(rowid) FROM `scrape_tasks` " +
                        "GROUP BY `content_id`, `content_kind`, `provider`, `task_kind`)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_scrape_tasks_content_id_content_kind_provider_task_kind` " +
                        "ON `scrape_tasks` (`content_id`, `content_kind`, `provider`, `task_kind`)",
                )
            }
        }

        /**
         * **所有迁移都放这个数组里，容器整批注册**。
         * 注意数组要放在各迁移定义**之后**（companion 是顺序初始化的）。
         */
        val MIGRATIONS = arrayOf(
            MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
            MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
        )
    }
}
