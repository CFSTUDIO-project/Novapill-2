package app.novapill.android.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalcEngineTest {
    private fun value(expr: String): String {
        val r = CalcEngine.evaluate(expr)
        assertTrue("expected a value for $expr but got $r", r is CalcResult.Value)
        return (r as CalcResult.Value).text
    }

    private fun isError(expr: String) = CalcEngine.evaluate(expr) == CalcResult.Error

    @Test
    fun precedenceAndBrackets() {
        assertEquals("14", value("2+3*4"))
        assertEquals("20", value("(2+3)*4"))
        assertEquals("-3", value("-5+2"))
        assertEquals("5", value("2--3"))
        assertEquals("6", value("2*(1+2)"))
    }

    @Test
    fun divisionIsExactNotFloating() {
        assertEquals("2.5", value("10/4"))
        assertEquals("0.1", value("0.3-0.2"))
        assertEquals("0.3", value("0.1+0.2"))
    }

    @Test
    fun percentDividesByHundred() {
        assertEquals("0.5", value("50%"))
        assertEquals("20", value("200*10%"))
    }

    @Test
    fun symbolsFromTheKeypadWork() {
        assertEquals("12", value("3×4"))
        assertEquals("3.5", value("7÷2"))
        assertEquals("-1", value("2−3"))
    }

    @Test
    fun badInputIsAnErrorNotACrash() {
        assertTrue(isError(""))
        assertTrue(isError("1/0"))
        assertTrue(isError("2*(3"))
        assertTrue(isError("2+"))
        assertTrue(isError("1..2"))
        assertTrue(isError("."))
        assertTrue(isError("2 ? 3"))
        assertTrue(isError("9".repeat(CalcEngine.MAX_LENGTH + 1)))
    }

    @Test
    fun negativeZeroIsShownAsZero() {
        assertEquals("0", value("-0"))
        assertEquals("0", value("1-1"))
    }

    private fun typed(keys: List<String>): CalcPad = keys.fold(CalcPad()) { s, k -> CalcPad.press(s, k) }

    @Test
    fun keypadTypesAndEvaluates() {
        val s = typed(listOf("1", "2", "+", "3", "×", "2", "="))
        assertEquals("18", s.shown)
        assertTrue(s.fresh)
    }

    @Test
    fun keypadStartsOverAfterResultWhenTypingADigit() {
        val s = typed(listOf("8", "÷", "4", "=", "5"))
        assertEquals("5", s.shown)
        assertEquals(false, s.fresh)
    }

    @Test
    fun keypadContinuesFromResultWhenOperatorIsPressed() {
        val s = typed(listOf("8", "÷", "4", "=", "+", "1", "="))
        assertEquals("3", s.shown)
    }

    @Test
    fun keypadShowsErrorAndRecoversOnNextKey() {
        val bad = typed(listOf("5", "÷", "0", "="))
        assertTrue(bad.error)
        assertEquals("Error", bad.shown)
        val next = CalcPad.press(bad, "7")
        assertEquals("7", next.shown)
        assertEquals(false, next.error)
    }

    @Test
    fun keypadIgnoresASecondDecimalPoint() {
        val s = typed(listOf("1", ".", "2", "."))
        assertEquals("1.2", s.expr)
    }

    @Test
    fun keypadReplacesAnOperatorPressedTwice() {
        val s = typed(listOf("9", "+", "×"))
        assertEquals("9×", s.expr)
    }

    @Test
    fun keypadAllowsAUnaryMinusAtTheStartAndAfterAnOperator() {
        assertEquals("-8", typed(listOf("−", "4", "×", "2", "=")).shown)
        assertEquals("8", typed(listOf("−", "4", "×", "−", "2", "=")).shown)
    }

    @Test
    fun keypadAutoClosesBracketsAndInsertsMultiplication() {
        val s = typed(listOf("(", "2", "+", "3", ")", "4", "="))
        assertEquals("20", s.shown)
    }

    @Test
    fun keypadBackspaceAndClear() {
        val s = typed(listOf("1", "2", "3", "⌫"))
        assertEquals("12", s.expr)
        assertEquals(CalcPad(), CalcPad.press(s, "C"))
        assertEquals(CalcPad(), CalcPad.press(typed(listOf("4", "⌫")), "⌫"))
    }

    @Test
    fun keypadNeverExceedsItsLimit() {
        var s = CalcPad()
        repeat(CalcPad.MAX_INPUT + 10) { s = CalcPad.press(s, "9") }
        assertEquals(CalcPad.MAX_INPUT, s.expr.length)
    }
}
