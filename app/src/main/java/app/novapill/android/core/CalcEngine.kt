package app.novapill.android.core

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

sealed interface CalcResult {
    data class Value(val text: String) : CalcResult
    data object Error : CalcResult
}

/**
 * Exact arithmetic for the floating calculator: BigDecimal, no floating-point drift, no eval.
 * Grammar: expr = term (('+'|'-') term)*; term = unary (('*'|'/') unary)*; unary = '-' unary | postfix;
 * postfix = primary '%'*; primary = number | '(' expr ')'. A '%' divides by 100.
 */
object CalcEngine {
    const val MAX_LENGTH = 64
    private val MC = MathContext(24, RoundingMode.HALF_EVEN)
    private val HUNDRED = BigDecimal(100)

    fun evaluate(expression: String): CalcResult {
        val src = normalise(expression)
        if (src.isEmpty() || src.length > MAX_LENGTH) return CalcResult.Error
        val value = Parser(src).parseAll() ?: return CalcResult.Error
        return CalcResult.Value(format(value))
    }

    /** Plain decimal text without trailing zeros; never "-0". */
    fun format(v: BigDecimal): String {
        val plain = v.round(MC).stripTrailingZeros().toPlainString()
        return if (plain == "-0") "0" else plain
    }

    private fun normalise(expression: String): String {
        val out = StringBuilder(expression.length)
        for (ch in expression) {
            when (ch) {
                '×', '*' -> out.append('*')
                '÷', '/' -> out.append('/')
                '−', '-' -> out.append('-')
                '+', '%', '(', ')', '.' -> out.append(ch)
                in '0'..'9' -> out.append(ch)
                ' ' -> Unit
                else -> return ""
            }
        }
        return out.toString()
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun parseAll(): BigDecimal? {
            val v = expr() ?: return null
            return if (i == s.length) v else null
        }

        private fun peek(): Char? = if (i < s.length) s[i] else null

        private fun expr(): BigDecimal? {
            var left = term() ?: return null
            while (true) {
                val op = peek()
                if (op != '+' && op != '-') return left
                i++
                val right = term() ?: return null
                left = if (op == '+') left.add(right) else left.subtract(right)
            }
        }

        private fun term(): BigDecimal? {
            var left = unary() ?: return null
            while (true) {
                val op = peek()
                if (op != '*' && op != '/') return left
                i++
                val right = unary() ?: return null
                left = if (op == '*') {
                    left.multiply(right, MC)
                } else {
                    if (right.signum() == 0) return null
                    left.divide(right, MC)
                }
            }
        }

        private fun unary(): BigDecimal? = when (peek()) {
            '-' -> {
                i++
                unary()?.negate()
            }
            '+' -> {
                i++
                unary()
            }
            else -> postfix()
        }

        private fun postfix(): BigDecimal? {
            var v = primary() ?: return null
            while (peek() == '%') {
                i++
                v = v.divide(HUNDRED, MC)
            }
            return v
        }

        private fun primary(): BigDecimal? {
            if (peek() == '(') {
                i++
                val v = expr() ?: return null
                if (peek() != ')') return null
                i++
                return v
            }
            return number()
        }

        private fun number(): BigDecimal? {
            val start = i
            var dot = false
            while (i < s.length) {
                val c = s[i]
                if (c in '0'..'9') {
                    i++
                } else if (c == '.' && !dot) {
                    dot = true
                    i++
                } else {
                    break
                }
            }
            if (i == start) return null
            val text = s.substring(start, i)
            if (text == ".") return null
            return try {
                BigDecimal(text)
            } catch (_: NumberFormatException) {
                null
            }
        }
    }
}

/** Keypad state for the floating calculator. [expr] is what the user typed, [shown] is what the display shows. */
data class CalcPad(
    val expr: String = "",
    val shown: String = "0",
    val error: Boolean = false,
    /** The last key was "=": the display holds a result and a digit starts a new calculation. */
    val fresh: Boolean = false,
) {
    companion object {
        const val MAX_INPUT = 48
        private val OPERATORS = setOf('+', '−', '×', '÷', '%')
        private val BINARY = setOf('+', '−', '×', '÷')

        /** Applies one key. Keys: "0".."9", ".", "+", "−", "×", "÷", "%", "(", ")", "=", "C", "⌫". */
        fun press(cur: CalcPad, key: String): CalcPad {
            if (key == "C") return CalcPad()
            if (cur.error && key != "⌫") return press(CalcPad(), key)
            return when {
                key == "⌫" -> backspace(cur)
                key == "=" -> equals(cur)
                key.length == 1 && key[0] in '0'..'9' || key == "." -> digit(cur, key)
                key == "(" -> open(cur)
                key == ")" -> close(cur)
                key.length == 1 && key[0] in OPERATORS -> operator(cur, key[0])
                else -> cur
            }
        }

        private fun backspace(cur: CalcPad): CalcPad {
            if (cur.fresh) return CalcPad()
            val trimmed = cur.expr.dropLast(1)
            return CalcPad(trimmed, trimmed.ifEmpty { "0" })
        }

        private fun digit(cur: CalcPad, key: String): CalcPad {
            val base = if (cur.fresh) CalcPad() else cur
            if (base.expr.length >= MAX_INPUT) return base
            if (key == ".") {
                val currentNumber = base.expr.takeLastWhile { it.isDigit() || it == '.' }
                if (currentNumber.contains('.')) return base
                val text = if (currentNumber.isEmpty()) base.expr + "0." else base.expr + "."
                return CalcPad(text, text)
            }
            // "(2+3)4" would be ambiguous, so a digit after a closing bracket multiplies by it.
            val glue = if (base.expr.endsWith(")")) "×" else ""
            val text = base.expr + glue + key
            return CalcPad(text, text)
        }

        /** A key that completes a value: a digit, a closing bracket or a percent sign. */
        private fun endsValue(c: Char): Boolean = c.isDigit() || c == ')' || c == '%'

        /** Operators an opening bracket or another operator may directly precede a sign. */
        private val OPEN_OR_OP = setOf('(', '+', '−', '×', '÷')

        /** After "=" the expression is the result itself, so every operator carries on from it. */
        private fun operator(cur: CalcPad, op: Char): CalcPad {
            val base = cur.expr
            if (op == '%') {
                if (base.isEmpty() || !endsValue(base.last())) return cur
                return CalcPad(base + "%", base + "%")
            }
            if (base.isEmpty()) return if (op == '−') CalcPad("−", "−") else cur
            val last = base.last()
            if (last == '(') return if (op == '−') CalcPad(base + "−", base + "−") else cur
            if (last in BINARY) {
                // A minus that already is a sign (after "(" or another operator) cannot take a second operator.
                val isSign = last == '−' && (base.length == 1 || base[base.length - 2] in OPEN_OR_OP)
                if (isSign) return cur
                // "5×−2": a minus right after an operator starts a negative number instead of replacing it.
                if (op == '−' && last != '−') return CalcPad(base + op, base + op)
                val text = base.dropLast(1) + op
                return CalcPad(text, text)
            }
            return CalcPad(base + op, base + op)
        }

        private fun open(cur: CalcPad): CalcPad {
            val base = if (cur.fresh) "" else cur.expr
            if (base.isNotEmpty() && endsValue(base.last())) return cur
            if (base.length >= MAX_INPUT) return cur
            val text = base + "("
            return CalcPad(text, text)
        }

        private fun close(cur: CalcPad): CalcPad {
            if (cur.fresh || cur.expr.isEmpty()) return cur
            val opens = cur.expr.count { it == '(' }
            val closes = cur.expr.count { it == ')' }
            val last = cur.expr.last()
            val endsValue = last.isDigit() || last == ')' || last == '%'
            if (opens <= closes || !endsValue) return cur
            val text = cur.expr + ")"
            return CalcPad(text, text)
        }

        private fun equals(cur: CalcPad): CalcPad {
            if (cur.fresh || cur.expr.isEmpty()) return cur
            var source = cur.expr
            while (source.count { it == '(' } > source.count { it == ')' }) source += ")"
            val last = source.last()
            if (last in BINARY || last == '(') return cur.copy(error = true, shown = "Error")
            return when (val r = CalcEngine.evaluate(source)) {
                is CalcResult.Value -> CalcPad(r.text, r.text, fresh = true)
                CalcResult.Error -> CalcPad(expr = cur.expr, shown = "Error", error = true)
            }
        }
    }
}
