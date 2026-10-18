package com.lovelev.mediadock.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 迁移链的护栏单测。
 *
 * 为什么需要它：库表版本一旦 +1 而忘了补迁移，`MIGRATIONS` 就断了链。以前（还挂着
 * destructive 回退时）会导致**静默清库** —— 编译、单测、启动全都正常，只有
 * 「用户数据没了」这一个症状。移除回退后，问题仅在运行期暴露（打开 App 时），
 * 本单测把这件事提前到「跑单测就红」，不依赖真机、不依赖装机。
 *
 * 加迁移 / 改版本号时，它会告诉你链断在哪一级。
 */
class MigrationCoverageTest {

    @Test
    fun `迁移链从最低版本逐级连续 一直到当前版本`() {
        val chain = MediaDatabase.MIGRATIONS
            .map { it.startVersion to it.endVersion }
            .sortedBy { it.first }

        assertTrue("至少要有一条迁移（现有 3 → … → ${MediaDatabase.VERSION}）", chain.isNotEmpty())

        // 每条都必须是单步：多级跳（3→6）会让中间版本没法逐级升上来
        chain.forEach { (from, to) ->
            assertEquals("迁移 $from→$to 不是单步", from + 1, to)
        }

        // 逐级接上，且链尾要正好落在当前版本
        var expected = chain.first().first
        for ((from, to) in chain) {
            assertEquals("迁移链在 $expected 处断了（缺 $expected→${expected + 1}）", expected, from)
            expected = to
        }
        assertEquals(
            "迁移链没走到当前版本 ${MediaDatabase.VERSION}，请补一条迁移再改 VERSION",
            MediaDatabase.VERSION,
            expected,
        )
    }
}
