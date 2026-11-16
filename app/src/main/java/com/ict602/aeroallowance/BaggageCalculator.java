package com.ict602.aeroallowance;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Pure Java: no Android imports, so the assignment's mathematics is easy to test. */
public final class BaggageCalculator {
    public static final BigDecimal TAX_RATE = new BigDecimal("0.08");
    public enum TicketClass {
        ECONOMY(20), BUSINESS(40);
        public final BigDecimal allowance;
        TicketClass(int kilograms) { allowance = BigDecimal.valueOf(kilograms); }
    }
    private BaggageCalculator() { }

    public static Result calculate(BigDecimal weight, TicketClass ticket, BigDecimal rate) {
        if (weight == null || ticket == null || rate == null) {
            throw new IllegalArgumentException("All three inputs are required.");
        }
        if (weight.signum() < 0 || rate.signum() < 0) {
            throw new IllegalArgumentException("Weight and rate cannot be negative.");
        }
        // max(0) is essential: a bag below its allowance must never earn a negative fee.
        BigDecimal excess = weight.subtract(ticket.allowance).max(BigDecimal.ZERO);
        // Decimal arithmetic avoids binary floating-point errors in money calculations.
        BigDecimal charge = excess.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal tax = charge.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);
        return new Result(excess, charge, tax, charge.add(tax));
    }

    public static final class Result {
        public final BigDecimal excess, charge, tax, total;
        private Result(BigDecimal excess, BigDecimal charge, BigDecimal tax, BigDecimal total) {
            this.excess = excess; this.charge = charge; this.tax = tax; this.total = total;
        }
    }
}
