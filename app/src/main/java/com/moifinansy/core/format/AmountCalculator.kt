package com.moifinansy.core.format

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Калькулятор поля суммы: «120+35», «1 200 − 15,5», «3×250», «900/3». Сумма и разность — в деньгах,
 * множитель и делитель — числа. Результат в минимальных единицах (копейки), округление до копейки.
 * Без Double: считаем в BigDecimal.
 */
object AmountCalculator {
    fun evaluate(expression: String): Long? {
        val tokens = tokenize(expression) ?: return null
        if (tokens.isEmpty()) return null
        return try {
            val parser = Parser(tokens)
            val value = parser.expression()
            if (!parser.atEnd()) return null
            val minor = value.setScale(2, RoundingMode.HALF_UP).movePointRight(2)
            if (minor.abs() > BigDecimal("999999999999999")) null else minor.longValueExact()
        } catch (e: ArithmeticException) {
            null
        } catch (e: IllegalStateException) {
            null
        }
    }

    /** Есть ли в строке действие (тогда под полем показываем результат). */
    fun hasOperator(text: String): Boolean = text.drop(1).any { it in "+-−×*/÷" }

    private sealed interface Token {
        data class Num(val value: BigDecimal) : Token
        data class Op(val op: Char) : Token
    }

    private fun tokenize(text: String): List<Token>? {
        val tokens = mutableListOf<Token>()
        val number = StringBuilder()
        fun flush(): Boolean {
            if (number.isEmpty()) return true
            val s = number.toString().replace(',', '.')
            if (s.count { it == '.' } > 1 || s == ".") return false
            tokens += Token.Num(BigDecimal(if (s.startsWith('.')) "0$s" else s))
            number.clear()
            return true
        }
        for (c in text) {
            when {
                c.isDigit() || c == '.' || c == ',' -> number.append(c)
                c.isWhitespace() || c == ' ' -> Unit
                c in "+-−×*/÷" -> {
                    if (!flush()) return null
                    tokens += Token.Op(
                        when (c) {
                            '−' -> '-'
                            '×' -> '*'
                            '÷' -> '/'
                            else -> c
                        },
                    )
                }
                else -> return null
            }
        }
        if (!flush()) return null
        return tokens
    }

    private class Parser(private val tokens: List<Token>) {
        private var pos = 0
        fun atEnd() = pos == tokens.size

        fun expression(): BigDecimal {
            var value = term()
            while (!atEnd()) {
                val op = (tokens[pos] as? Token.Op)?.op ?: error("op")
                if (op != '+' && op != '-') break
                pos++
                value = if (op == '+') value + term() else value - term()
            }
            return value
        }

        private fun term(): BigDecimal {
            var value = factor()
            while (!atEnd()) {
                val op = (tokens[pos] as? Token.Op)?.op ?: error("op")
                if (op != '*' && op != '/') break
                pos++
                val right = factor()
                value = if (op == '*') value * right else value.divide(right, 10, RoundingMode.HALF_UP)
            }
            return value
        }

        private fun factor(): BigDecimal {
            check(!atEnd())
            return when (val t = tokens[pos++]) {
                is Token.Num -> t.value
                is Token.Op -> if (t.op == '-') factor().negate() else error("unexpected ${t.op}")
            }
        }
    }
}
