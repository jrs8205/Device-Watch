package com.example.modernwidget.data

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate

class NotificationLogImplTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val today = LocalDate.of(2026, 7, 4)

    private fun entry(millis: Long, title: String = "t") = NotificationLogEntry(
        timeMillis = millis, packageName = "p", appLabel = "a", title = title, text = "x",
    )

    private fun log(day: LocalDate = today) = NotificationLogImpl(tmp.root) { day }

    @Test
    fun `append then read returns entries newest first`() {
        val log = log()
        log.append(entry(1L, "first"))
        log.append(entry(2L, "second"))
        assertThat(log.entriesNewestFirst().map { it.title })
            .containsExactly("second", "first").inOrder()
    }

    @Test
    fun `append purges files older than retention`() {
        val old = tmp.newFile(NotificationLogCodec.fileNameFor(today.minusDays(10)))
        log().append(entry(1L))
        assertThat(old.exists()).isFalse()
    }

    @Test
    fun `read skips corrupted lines`() {
        tmp.newFile(NotificationLogCodec.fileNameFor(today))
            .writeText("garbage\n" + NotificationLogCodec.encode(entry(5L, "ok")) + "\n")
        assertThat(log().entriesNewestFirst().map { it.title }).containsExactly("ok")
    }

    @Test
    fun `per-day cap stops appending`() {
        val log = log()
        repeat(NotificationLogCodec.MAX_ENTRIES_PER_DAY + 5) { log.append(entry(it.toLong())) }
        assertThat(log.entriesNewestFirst()).hasSize(NotificationLogCodec.MAX_ENTRIES_PER_DAY)
    }

    @Test
    fun `entries from several days interleave newest day first`() {
        val yesterdayLog = log(today.minusDays(1))
        yesterdayLog.append(entry(10L, "old-day"))
        val todayLog = log(today)
        todayLog.append(entry(20L, "new-day"))
        assertThat(todayLog.entriesNewestFirst().map { it.title })
            .containsExactly("new-day", "old-day").inOrder()
    }

    @Test
    fun `entries are ordered by post time even when appended out of order`() {
        val log = log()
        log.append(entry(20L, "second"))
        log.append(entry(10L, "first"))
        assertThat(log.entriesNewestFirst().map { it.title })
            .containsExactly("second", "first").inOrder()
    }
}
