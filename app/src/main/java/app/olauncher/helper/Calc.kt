package app.olauncher.helper

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

object Calc {

    private sealed interface Token {
        data class Number(val value: Double) : Token
        data object Plus : Token
        data object Minus : Token
        data object Multiply : Token
        data object Divide : Token
        data object Power : Token
        data object Percent : Token
        data object LParen : Token
        data object RParen : Token
        data object Eof : Token
    }

    fun evaluate(query: String): String? {
        if (query.isBlank()) return null
        val tokens = tokenize(query) ?: return null
        return Parser(tokens).parse()
    }

    private fun tokenize(query: String): List<Token>? {
        val tokens = mutableListOf<Token>()
        var i = 0
        val n = query.length

        while (i < n) {
            val c = query[i]
            when {
                c.isWhitespace() -> {
                    i++
                }
                c == '+' -> {
                    tokens.add(Token.Plus)
                    i++
                }
                c == '-' -> {
                    tokens.add(Token.Minus)
                    i++
                }
                c == '*' || c == '×' -> {
                    tokens.add(Token.Multiply)
                    i++
                }
                c == '/' || c == '÷' -> {
                    tokens.add(Token.Divide)
                    i++
                }
                c == '^' -> {
                    tokens.add(Token.Power)
                    i++
                }
                c == '%' -> {
                    tokens.add(Token.Percent)
                    i++
                }
                c == '(' -> {
                    tokens.add(Token.LParen)
                    i++
                }
                c == ')' -> {
                    tokens.add(Token.RParen)
                    i++
                }
                c.isDigit() || c == '.' || c == ',' -> {
                    var hasDot = false
                    var digitCount = 0
                    val sb = StringBuilder()

                    while (i < n) {
                        val ch = query[i]
                        if (ch.isDigit()) {
                            digitCount++
                            sb.append(ch)
                            i++
                        } else if (ch == '.' || ch == ',') {
                            if (hasDot) break
                            hasDot = true
                            sb.append('.')
                            i++
                        } else {
                            break
                        }
                    }

                    if (digitCount == 0) return null
                    val num = sb.toString().toDoubleOrNull() ?: return null
                    tokens.add(Token.Number(num))
                }
                else -> {
                    return null
                }
            }
        }
        tokens.add(Token.Eof)
        return tokens
    }

    private class Parser(private val tokens: List<Token>) {
        private var pos = 0
        private var hasBinaryOperator = false

        private fun peek(): Token = tokens[pos]

        private fun advance(): Token {
            val token = tokens[pos]
            if (pos < tokens.size - 1) pos++
            return token
        }

        private fun match(token: Token): Boolean {
            if (peek() == token) {
                advance()
                return true
            }
            return false
        }

        fun parse(): String? {
            return try {
                val result = parseExpression()
                if (peek() != Token.Eof) return null
                if (!hasBinaryOperator) return null
                formatResult(result)
            } catch (_: Exception) {
                null
            } catch (_: StackOverflowError) {
                null
            }
        }

        private fun parseExpression(): Double {
            var left = parseTerm()
            while (true) {
                if (match(Token.Plus)) {
                    hasBinaryOperator = true
                    left += parseTerm()
                } else if (match(Token.Minus)) {
                    hasBinaryOperator = true
                    left -= parseTerm()
                } else {
                    break
                }
            }
            return left
        }

        private fun parseTerm(): Double {
            var left = parseUnary()
            while (true) {
                if (match(Token.Multiply)) {
                    hasBinaryOperator = true
                    left *= parseUnary()
                } else if (match(Token.Divide)) {
                    hasBinaryOperator = true
                    val right = parseUnary()
                    if (right == 0.0 || right == -0.0) throw ArithmeticException("Division by zero")
                    left /= right
                } else {
                    break
                }
            }
            return left
        }

        private fun parseUnary(): Double {
            if (match(Token.Plus)) {
                return parseUnary()
            }
            if (match(Token.Minus)) {
                return -parseUnary()
            }
            return parsePower()
        }

        private fun parsePower(): Double {
            var left = parsePostfix()
            if (match(Token.Power)) {
                hasBinaryOperator = true
                val right = parseUnary()
                if ((left == 0.0 || left == -0.0) && right < 0.0) throw ArithmeticException("Division by zero")
                left = Math.pow(left, right)
                if (left.isNaN() || left.isInfinite()) throw ArithmeticException("Invalid power result")
            }
            return left
        }

        private fun parsePostfix(): Double {
            var value = parsePrimary()
            while (match(Token.Percent)) {
                value /= 100.0
            }
            return value
        }

        private fun parsePrimary(): Double {
            return when (val token = peek()) {
                is Token.Number -> {
                    advance()
                    token.value
                }
                Token.LParen -> {
                    advance()
                    val expr = parseExpression()
                    if (!match(Token.RParen)) throw IllegalArgumentException("Missing closing parenthesis")
                    expr
                }
                else -> throw IllegalArgumentException("Unexpected token $token")
            }
        }

        private fun formatResult(value: Double): String? {
            if (value.isNaN() || value.isInfinite()) return null
            if (value == 0.0 || value == -0.0) return "0"
            val bd = BigDecimal(value, MathContext(10, RoundingMode.HALF_UP))
            if (bd.compareTo(BigDecimal.ZERO) == 0) return "0"
            return bd.stripTrailingZeros().toPlainString()
        }
    }
}
