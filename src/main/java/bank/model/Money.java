package bank.model;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/** Formatting and parsing of money amounts ($1,234.56). */
public final class Money {

    private Money() {}

    public static String format(BigDecimal amount) {
        return NumberFormat.getCurrencyInstance(Locale.US).format(amount);
    }

    /** Accepts "1500", "1500.50", "1,500.50", "$1,500.50" and also "1500,50" and "1.500,50". */
    public static BigDecimal parse(String text) {
        String clean = text.trim().replace("$", "").replace(" ", "");
        if (clean.contains(".") && clean.lastIndexOf(',') > clean.lastIndexOf('.')) {
            // "1.500,50": the Brazilian way, dots for thousands and the comma for cents.
            // Before this it became 1.50050, a deposit of a dollar and a half
            clean = clean.replace(".", "").replace(",", ".");
        } else if (clean.contains(".")) {
            clean = clean.replace(",", "");  // "1,500.50": the comma only separates thousands
        } else if (clean.matches("\\d{1,3}(,\\d{3})+")) {
            clean = clean.replace(",", "");  // "1,500" = one thousand five hundred
        } else {
            clean = clean.replace(",", ".");  // "1500,50" from people used to a decimal comma
        }
        return new BigDecimal(clean);
    }
}
