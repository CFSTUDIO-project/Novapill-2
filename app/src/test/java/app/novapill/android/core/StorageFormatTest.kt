package app.novapill.android.core

import org.junit.Assert.assertEquals
import org.junit.Test

class StorageFormatTest {
    @Test
    fun smallSizesStayInBytes() {
        assertEquals("0 B", StorageFormat.bytes(0L))
        assertEquals("999 B", StorageFormat.bytes(999L))
    }

    @Test
    fun usesDecimalUnitsWithOneDecimalBelowOneHundred() {
        assertEquals("1.0 KB", StorageFormat.bytes(1_000L))
        assertEquals("12.4 GB", StorageFormat.bytes(12_400_000_000L))
        assertEquals("850 MB", StorageFormat.bytes(850_000_000L))
    }

    @Test
    fun roundingNeverShowsAThousandOfAUnit() {
        assertEquals("1.0 GB", StorageFormat.bytes(999_960_000L))
    }

    @Test
    fun unknownSizeIsADash() {
        assertEquals("–", StorageFormat.bytes(-1L))
    }

    @Test
    fun hugeSizesStopAtTerabytes() {
        assertEquals("2.0 TB", StorageFormat.bytes(2_000_000_000_000L))
        assertEquals("5000 TB", StorageFormat.bytes(5_000_000_000_000_000L))
    }
}
