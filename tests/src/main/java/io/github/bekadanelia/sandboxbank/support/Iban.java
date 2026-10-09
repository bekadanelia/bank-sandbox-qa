package io.github.bekadanelia.sandboxbank.support;

import java.math.BigInteger;

/** ISO 13616 check digits, computed independently of the system under test. */
public final class Iban {

    private static final BigInteger NINETY_SEVEN = BigInteger.valueOf(97);

    private Iban() {
    }

    /** True when the rearranged number leaves a remainder of 1 after division by 97. */
    public static boolean hasValidCheckDigits(String iban) {
        return numeric(iban.substring(4) + iban.substring(0, 4)).mod(NINETY_SEVEN).intValue() == 1;
    }

    /** The same account number with its check digits replaced by wrong ones. */
    public static String withWrongCheckDigits(String iban) {
        int wrong = (Integer.parseInt(iban.substring(2, 4)) + 1) % 97;
        return iban.substring(0, 2) + "%02d".formatted(wrong) + iban.substring(4);
    }

    /** A well formed account number that the bank has never issued. */
    public static String unissued() {
        String body = "ZZ9999999999999999";
        int checkDigits = 98 - numeric(body + "GE00").mod(NINETY_SEVEN).intValue();
        return "GE%02d%s".formatted(checkDigits, body);
    }

    private static BigInteger numeric(String text) {
        StringBuilder digits = new StringBuilder();
        for (char character : text.toCharArray()) {
            if (Character.isLetter(character)) {
                digits.append(character - 'A' + 10); // A=10 ... Z=35
            } else {
                digits.append(character);
            }
        }
        return new BigInteger(digits.toString());
    }
}
