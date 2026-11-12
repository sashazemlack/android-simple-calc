package com.scalc.calculator.calc.history

import androidx.datastore.core.CorruptionException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class HistorySerializerTest {

    private suspend fun roundTrip(data: HistoryData): HistoryData {
        val bytes = ByteArrayOutputStream().also { HistorySerializer.writeTo(data, it) }.toByteArray()
        return HistorySerializer.readFrom(ByteArrayInputStream(bytes))
    }

    private suspend fun assertCorrupt(bytes: ByteArray) {
        try {
            HistorySerializer.readFrom(ByteArrayInputStream(bytes))
            fail("expected CorruptionException")
        } catch (expected: CorruptionException) {
            // expected
        }
    }

    private fun header(version: Int, count: Int): ByteArray = ByteArrayOutputStream().also { out ->
        DataOutputStream(out).apply {
            writeInt(version)
            writeInt(count)
        }
    }.toByteArray()

    @Test
    fun roundTripKeepsEntriesAndOrder() = runTest {
        val data = HistoryData(
            listOf(
                HistoryEntry("125+25×2", "175", 1_794_000_000_000L),
                HistoryEntry("√(2)×π−sin⁻¹(0.5)", "−25.5728336862316", 2L),
                HistoryEntry("1÷3", "0.333333333333333", 0L),
            ),
        )
        assertEquals(data, roundTrip(data))
    }

    @Test
    fun carriedValuesRoundTrip() = runTest {
        val data = HistoryData(
            listOf(
                HistoryEntry("0.333333333333333×3", "1", 7L, carryValue = "0.3333333333333333333333333333333333"),
                HistoryEntry("2+3", "5", 6L),
                HistoryEntry("1.4142135623731^2", "2", 5L, carryValue = "1.414213562373095048801688724209698"),
            ),
        )
        assertEquals(data, roundTrip(data))
    }

    @Test
    fun version1FilesWithoutCarryAreStillRead() = runTest {
        val bytes = ByteArrayOutputStream().also { out ->
            DataOutputStream(out).apply {
                writeInt(1)
                writeInt(2)
                writeUTF("125+25×2")
                writeUTF("175")
                writeLong(1_794_000_000_000L)
                writeUTF("1÷3")
                writeUTF("0.333333333333333")
                writeLong(4L)
            }
        }.toByteArray()
        val expected = HistoryData(
            listOf(
                HistoryEntry("125+25×2", "175", 1_794_000_000_000L),
                HistoryEntry("1÷3", "0.333333333333333", 4L),
            ),
        )
        assertEquals(expected, HistorySerializer.readFrom(ByteArrayInputStream(bytes)))
    }

    @Test
    fun emptyHistoryRoundTrips() = runTest {
        assertEquals(HistoryData(), roundTrip(HistoryData()))
        assertEquals(HistoryData(), HistorySerializer.defaultValue)
    }

    @Test
    fun truncatedDataIsCorrupt() = runTest {
        assertCorrupt(byteArrayOf(0, 0, 0))
        assertCorrupt(header(version = 1, count = 2))
        assertCorrupt(header(version = 2, count = 1))
        val bytes = ByteArrayOutputStream().also {
            HistorySerializer.writeTo(HistoryData(listOf(HistoryEntry("1+1", "2", 5L))), it)
        }.toByteArray()
        assertCorrupt(bytes.copyOf(bytes.size - 3))
    }

    @Test
    fun unknownVersionIsCorrupt() = runTest { assertCorrupt(header(version = 99, count = 0)) }

    @Test
    fun invalidSizeIsCorrupt() = runTest {
        assertCorrupt(header(version = 1, count = -1))
        assertCorrupt(header(version = 1, count = 1_000_000))
    }
}
