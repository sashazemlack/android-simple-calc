package com.scalc.calculator.calc.engine

import java.math.BigDecimal

/** Evaluates a [Node] tree with BigDecimal arithmetic; every intermediate value is range-checked. */
internal class Evaluator(private val angleMode: AngleMode) {

    fun evaluate(node: Node): BigDecimal = BigMath.checked(compute(node))

    private fun compute(node: Node): BigDecimal = when (node) {
        is Node.Num -> node.exact ?: BigMath.parseLiteral(node.text)
        is Node.Constant -> node.value
        is Node.Negate -> evaluate(node.operand).negate()
        is Node.Group -> evaluate(node.inner)
        is Node.Sum -> sum(node)
        is Node.Product -> product(node)
        is Node.Power -> BigMath.pow(evaluate(node.base), evaluate(node.exponent))
        is Node.Factorial -> BigMath.factorial(evaluate(node.operand))
        is Node.Percent -> evaluate(node.operand).movePointLeft(2)
        is Node.SquareRoot -> BigMath.sqrt(evaluate(node.operand))
        is Node.Call -> call(node.function, evaluate(node.argument))
    }

    /**
     * Left-to-right addition. When an addend is solely a top-level percent ("10%", "(5+5)%",
     * optionally signed) it is a percentage of the running total: 200+10% = 220, 200−10% = 180.
     *
     * Every partial sum is rounded to 34 digits, so subtracting an exact value from a rounded
     * one can leave pure rounding residue ("1÷3×3−1" would be −1E-34, "1+40!−40!" 5.7E+12).
     * A result smaller than 1E-32 of the larger operand is therefore cancellation noise and
     * becomes exactly 0 (see [snapCancellation]); "1E+20+1−1E+20" is still 1, while
     * "10^32+1−10^32" is 0 as in any finite-precision calculator.
     */
    private fun sum(node: Node.Sum): BigDecimal {
        var acc = evaluate(node.first)
        for (addend in node.rest) {
            val value = evaluate(addend.node)
            val operand = if (isPercentTerm(addend.node)) {
                BigMath.checked(acc.multiply(value, BigMath.MC))
            } else {
                value
            }
            val next = if (addend.subtract) acc.subtract(operand, BigMath.MC) else acc.add(operand, BigMath.MC)
            acc = BigMath.checked(snapCancellation(next, acc, operand))
        }
        return acc
    }

    /**
     * [result] of a ± b, or exactly 0 when it lies below the 34-digit working precision of the
     * operands (|result| · 1E+32 <= max(|a|, |b|)), i.e. it is only the residue of rounding.
     */
    private fun snapCancellation(result: BigDecimal, a: BigDecimal, b: BigDecimal): BigDecimal {
        if (result.signum() == 0) return result
        val larger = a.abs().max(b.abs())
        return if (result.abs().scaleByPowerOfTen(CANCELLATION_DIGITS) <= larger) BigDecimal.ZERO else result
    }

    private fun isPercentTerm(node: Node): Boolean = when (node) {
        is Node.Percent -> true
        is Node.Negate -> isPercentTerm(node.operand)
        else -> false
    }

    private fun product(node: Node.Product): BigDecimal {
        var acc = evaluate(node.first)
        for (factor in node.rest) {
            val value = evaluate(factor.node)
            val next = when (factor.op) {
                MulOp.Times -> acc.multiply(value, BigMath.MC)
                MulOp.Divide -> {
                    if (value.signum() == 0) throw CalcException(CalcError.DivisionByZero)
                    acc.divide(value, BigMath.MC)
                }
                MulOp.Modulo -> BigMath.remainder(acc, value)
            }
            acc = BigMath.checked(next)
        }
        return acc
    }

    private fun call(function: MathFunction, argument: BigDecimal): BigDecimal = when (function) {
        MathFunction.Log -> BigMath.log10(argument)
        MathFunction.Ln -> BigMath.ln(argument)
        MathFunction.Sin, MathFunction.Cos, MathFunction.Tan -> Trig.forward(function, argument, angleMode)
        MathFunction.Asin, MathFunction.Acos, MathFunction.Atan -> Trig.inverse(function, argument, angleMode)
    }

    private companion object {
        /** Two guard digits below the 34-digit precision of [BigMath.MC]. */
        const val CANCELLATION_DIGITS = 32
    }
}
