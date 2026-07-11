package org.jarsi.devicewatch.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class NotificationLogCodecTest {

    private val entry = NotificationLogEntry(
        timeMillis = 1_783_000_000_000L,
        packageName = "com.whatsapp",
        appLabel = "WhatsApp",
        title = "Matti",
        text = "Moro!\nTuletko käymään?\tKlo 18",
    )

    @Test
    fun `encode-decode round-trips tabs newlines and backslashes`() {
        val decoded = NotificationLogCodec.decodeOrNull(NotificationLogCodec.encode(entry))
        assertThat(decoded).isEqualTo(entry)
    }

    @Test
    fun `encode produces a single line`() {
        assertThat(NotificationLogCodec.encode(entry)).doesNotContain("\n")
    }

    @Test
    fun `decode rejects malformed lines`() {
        assertThat(NotificationLogCodec.decodeOrNull("")).isNull()
        assertThat(NotificationLogCodec.decodeOrNull("garbage")).isNull()
        assertThat(NotificationLogCodec.decodeOrNull("v9\t1\ta\tb\tc\td")).isNull()
        assertThat(NotificationLogCodec.decodeOrNull("v1\tNaN\ta\tb\tc\td")).isNull()
    }

    @Test
    fun `file names are epoch days and retention keeps seven days`() {
        val today = LocalDate.of(2026, 7, 4)
        val name = NotificationLogCodec.fileNameFor(today)
        assertThat(name).isEqualTo("${today.toEpochDay()}.log")
        assertThat(NotificationLogCodec.isRetainedFileName(name, today)).isTrue()
        assertThat(
            NotificationLogCodec.isRetainedFileName(
                NotificationLogCodec.fileNameFor(today.minusDays(6)), today
            )
        ).isTrue()
        assertThat(
            NotificationLogCodec.isRetainedFileName(
                NotificationLogCodec.fileNameFor(today.minusDays(7)), today
            )
        ).isFalse()
        assertThat(NotificationLogCodec.isRetainedFileName("junk.log", today)).isFalse()
    }
}
