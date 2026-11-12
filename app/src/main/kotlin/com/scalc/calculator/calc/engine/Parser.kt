package com.scalc.calculator.calc.engine

import java.math.BigDecimal

/** Syntax tree produced by [Parser] and evaluated by [Evaluator]. */
internal sealed interface Node {
    class Num(val text: String, val exact: BigDecimal?) : Node
    class Constant(val value: BigDecimal) : Node
    class Negate(val operand: Node) : Node

    /** Parenthesised sub-expression; kept so "(10%)" is not mistaken for a top-level percent. */
    class Group(val inner: Node) : Node
    class Sum(val first: Node, val rest: List<Addend>) : Node
    class Product(val first: Node, val rest: List<Factor>) : Node
    class Power(val base: Node, val exponent: Node) : Node
    class Factorial(val operand: Node) : Node
    class Percent(val operand: Node) : Node
    class SquareRoot(val operand: Node) : Node
    class Call(val function: MathFunction, val argument: Node) : Node
}

internal class Addend(val subtract: Boolean, val node: Node)

internal enum class MulOp { Times, Divide, Modulo }

internal class Factor(val op: MulOp, val node: Node)

/**
 * Recursive-descent parser for the display syntax:
 *
 * ```
 * expression := term (('+'|'−') term)*
 * term       := unary (('×'|'÷'|mod) unary | implicit power)*
 * unary      := ('+'|'−')* power
 * power      := postfix ('^' unary)?          right-associative; 2^−1, −2^2 = −4
 * postfix    := primary ('!' | percent)*
 * primary    := number | 'π' | 'e' | func '(' expression ')' | '√' '(' expression ')' | '√' unary
 *             | '(' expression ')'
 * ```
 *
 * A parenthesised radicand delimits the root like a function call ("√(4)! = 2", "√(−4)^2" is a
 * domain error); without parentheses '√' takes a unary-level operand ("√4^2" = 4, "√4!" = √24).
 *
 * Implicit multiplication binds like '×' and applies when an operand end (number, constant,
 * ')', '!', percent) is directly followed by an operand start (number, constant, function, '√',
 * '('), except number followed by number. Unclosed parentheses are closed at the end of input.
 * Running out of tokens where an operand is required reports [CalcError.Incomplete]; anything
 * else unexpected reports [CalcError.Syntax].
 */
internal class Parser(private val tokens: List<Token>) {
    private var pos = 0
    private var depth = 0

    private val current: Token get() = tokens[pos]
    private val previousType: TokenType? get() = if (pos > 0) tokens[pos - 1].type else null

    fun parse(): Node {
        if (current.type == TokenType.End) fail(CalcError.Incomplete)
        val root = expression()
        if (current.type != TokenType.End) fail(CalcError.Syntax)
        return root
    }

    private fun expression(): Node {
        val first = term()
        val rest = ArrayList<Addend>(0)
        while (current.type == TokenType.Plus || current.type == TokenType.Minus) {
            val subtract = current.type == TokenType.Minus
            pos++
            rest += Addend(subtract, term())
        }
        return if (rest.isEmpty()) first else Node.Sum(first, rest)
    }

    private fun term(): Node {
        val first = unary()
        val rest = ArrayList<Factor>(0)
        while (true) {
            val op = when (current.type) {
                TokenType.Times -> MulOp.Times
                TokenType.Divide -> MulOp.Divide
                TokenType.Modulo -> MulOp.Modulo
                else -> null
            }
            if (op != null) {
                pos++
                rest += Factor(op, unary())
            } else if (isImplicitMultiplication()) {
                rest += Factor(MulOp.Times, power())
            } else {
                break
            }
        }
        return if (rest.isEmpty()) first else Node.Product(first, rest)
    }

    private fun isImplicitMultiplication(): Boolean {
        val next = current.type
        val prev = previousType ?: return false
        if (next !in Tokenizer.OPERAND_STARTS || prev !in OPERAND_ENDS) return false
        return !(prev == TokenType.Number && next == TokenType.Number)
    }

    private fun unary(): Node = nested {
        var negative = false
        while (current.type == TokenType.Plus || current.type == TokenType.Minus) {
            if (current.type == TokenType.Minus) negative = !negative
            pos++
        }
        val operand = power()
        if (negative) Node.Negate(operand) else operand
    }

    private fun power(): Node {
        val base = postfix()
        if (current.type != TokenType.Caret) return base
        pos++
        return Node.Power(base, unary())
    }

    private fun postfix(): Node {
        var node = primary()
        while (true) {
            node = when (current.type) {
                TokenType.Bang -> Node.Factorial(node)
                TokenType.Percent -> Node.Percent(node)
                else -> return node
            }
            pos++
        }
    }

    private fun primary(): Node {
        val token = current
        return when (token.type) {
            TokenType.Number -> {
                pos++
                Node.Num(token.text, token.exactValue)
            }
            TokenType.Pi -> {
                pos++
                Node.Constant(BigMath.PI)
            }
            TokenType.Euler -> {
                pos++
                Node.Constant(BigMath.E)
            }
            TokenType.Func -> {
                pos++
                when (current.type) {
                    TokenType.LParen -> pos++
                    TokenType.End -> fail(CalcError.Incomplete)
                    else -> fail(CalcError.Syntax)
                }
                Node.Call(token.function!!, groupBody())
            }
            TokenType.Sqrt -> {
                pos++
                // "√(…)" (what the √ key inserts) reads like a call: the group is the radicand,
                // so "√(4)!" = 2 and "√(16)%" = 0.04. Without '(' the operand is a unary.
                if (current.type == TokenType.LParen) {
                    pos++
                    Node.SquareRoot(Node.Group(groupBody()))
                } else {
                    Node.SquareRoot(unary())
                }
            }
            TokenType.LParen -> {
                pos++
                Node.Group(groupBody())
            }
            TokenType.End -> fail(CalcError.Incomplete)
            // "()" or "sin()" is still being typed; ")" anywhere else is malformed.
            TokenType.RParen -> fail(if (previousType == TokenType.LParen) CalcError.Incomplete else CalcError.Syntax)
            else -> fail(CalcError.Syntax)
        }
    }

    /** Parses the inside of a parenthesis whose '(' was consumed; a missing ')' at the end is implied. */
    private fun groupBody(): Node {
        val inner = expression()
        when (current.type) {
            TokenType.RParen -> pos++
            TokenType.End -> Unit
            else -> fail(CalcError.Syntax)
        }
        return inner
    }

    /** Guards the recursion depth so pathological nesting fails cleanly instead of overflowing the stack. */
    private inline fun <T> nested(block: () -> T): T {
        if (++depth > MAX_DEPTH) fail(CalcError.Syntax)
        try {
            return block()
        } finally {
            depth--
        }
    }

    private fun fail(error: CalcError): Nothing = throw CalcException(error)

    private companion object {
        /** Enough for 200+ nested parentheses while keeping stack usage small on any thread. */
        const val MAX_DEPTH = 300

        val OPERAND_ENDS: Set<TokenType> = setOf(
            TokenType.Number, TokenType.Pi, TokenType.Euler, TokenType.RParen, TokenType.Bang, TokenType.Percent,
        )
    }
}
