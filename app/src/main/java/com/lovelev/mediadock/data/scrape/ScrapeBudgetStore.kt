package com.lovelev.mediadock.data.scrape

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lovelev.mediadock.core.data.scrape.ScrapeBudget
import java.time.LocalDate

private val Context.scrapeBudgetDataStore by preferencesDataStore("scrape_budget")

/**
 * TMDB 每日刮削预算。默认 1000 次/天（可在后续设置页开放配置）。
 *
 * **只按“任务数”计数**：一次任务内部可能因去年份重试发两次请求，
 * 预算仍按一次算；这是保守估计的近似值。
 */
class ScrapeBudgetStore(private val context: Context) : ScrapeBudget {

    override suspend fun tryConsume(): Boolean {
        var allowed = false
        context.scrapeBudgetDataStore.edit { prefs ->
            val today = LocalDate.now().toString()
            val storedDay = prefs[KEY_DAY]
            val used = if (storedDay == today) prefs[KEY_COUNT] ?: 0 else 0
            if (used < DAILY_LIMIT) {
                prefs[KEY_DAY] = today
                prefs[KEY_COUNT] = used + 1
                allowed = true
            }
        }
        return allowed
    }

    companion object {
        const val DAILY_LIMIT = 1000
        private val KEY_DAY = stringPreferencesKey("day")
        private val KEY_COUNT = intPreferencesKey("count")
    }
}
