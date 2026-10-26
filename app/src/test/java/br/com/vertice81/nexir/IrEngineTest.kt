package br.com.vertice81.nexir

import org.junit.Assert.*
import org.junit.Test

class IrEngineTest {
    @Test fun necHeaderAndBits() {
        val pulses = IrEngine.validate(IrCommand(code="20DF10EF", protocol="NEC", frequency=38000))
        assertEquals(67, pulses.size)
        assertArrayEquals(intArrayOf(9000, 4500, 560, 560), pulses.take(4).toIntArray())
        assertEquals(560, pulses.last())
    }
    @Test fun samsungHeader() {
        val pulses = IrEngine.validate(IrCommand(code="E0E040BF", protocol="SAMSUNG"))
        assertEquals(67, pulses.size)
        assertEquals(4500, pulses[0]); assertEquals(4500, pulses[1])
    }
    @Test fun sonyHeader() {
        val pulses = IrEngine.validate(IrCommand(code="A90", protocol="SONY12", order="LSB", frequency=40000))
        assertEquals(26, pulses.size)
        assertEquals(2400, pulses[0]); assertEquals(600, pulses[1])
    }
    @Test(expected=IllegalArgumentException::class)
    fun rejectExcessiveFrame() { IrEngine.validate(IrCommand(pattern=List(20) { 120000 }, protocol="RAW")) }
    @Test(expected=IllegalArgumentException::class)
    fun rejectInvalidHex() { IrEngine.validate(IrCommand(code="FFAX!0")) }
}
