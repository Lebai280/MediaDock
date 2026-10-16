package com.lovelev.mediadock.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 库表结构版本。
 *
 * **加表/加列要写真迁移，而且必须进 [MIGRATIONS] 数组**（容器整批注册，见 AppContainer）。
 * `media_items` 是缓存（丢了重扫就行），但 `playback_progress`、`favorites`、
 * `title_overrides`、`title_details` 都是用户数据，丢一次就是真的丢了。
 *
 * **升级方向的破坏性兜底已经去掉**（AppContainer 只留了 `OnDowngrade`）：
 * 漏写迁移的后果现在是「打开库时抛异常」，而不是「静默清库」。
 * 动 `media_items`（缓存）结构时，写一条显式的命名迁移（里面可以 DROP TABLE 再建），
 * 别指望兜底 —— 兜底一旦生效，用户数据也跟着一起没。
 *
 * 版本号是**单一来源** [VERSION]，`@Database` 与 [MigrationCoverageTest] 都用它 ——
 * 改了版本却没补迁移，单测会红。
 */
@Database(
    entities = [
        MediaItemEntity::class,
        PlaybackProgress::class,
        Favorite::class,
        TitleOverride::class,
        TitleDetail::class,
    ],
    version = MediaDatabase.VERSION,
    exportSchema = false,
)
abstract class MediaDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun progressDao(): ProgressDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun titleOverrideDao(): TitleOverrideDao
    abstract fun titleDetailDao(): TitleDetailDao

    companion object {
        /** 当前库表版本。**单一来源**：`@Database` 与迁移覆盖单测都读它。 */
        const val VERSION = 6

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
         * **所有迁移都放这个数组里，容器整批注册** ——
         * 之前把迁移分开写、容器只注册了其中一个，结果升级走了 destructive 兜底，
         * 把库和进度一起清了。加新迁移时只改这里，容器会自动带上。
         * 注意数组要放在各迁移定义**之后**（companion 是顺序初始化的）。
         */
        val MIGRATIONS = arrayOf(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
    }
}
