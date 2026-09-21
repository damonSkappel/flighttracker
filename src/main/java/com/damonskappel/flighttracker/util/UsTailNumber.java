package com.damonskappel.flighttracker.util;

/**
 * Derives a US civil registration ("tail number") from an ICAO 24-bit address.
 *
 * <p>OpenSky's state vectors carry no registration, but for US-registered
 * aircraft none is needed: the FAA assigns ICAO addresses in the block
 * A00001-ADF7C7 as a strict, gapless encoding of N-numbers, so the mapping is a
 * pure function requiring no lookup, no network call and no cache.
 *
 * <p>The bucket sizes below come from the N-number grammar rather than a table.
 * An N-number is 'N' followed by a leading digit 1-9, then up to four more
 * characters: further digits, optionally ending in one or two letters, where the
 * letter alphabet omits I and O to avoid confusion with 1 and 0. Counting the
 * completions available after each digit position gives:
 *
 * <pre>
 *   after 4 digits: 25 endings (none + 24 letters) + 10 fifth digits   =     35
 *   after 3 digits: 601 endings (none + 24 + 24*24) + 10 * 35          =    951
 *   after 2 digits: 601 + 10 * 951                                     =  10111
 *   after 1 digit : 601 + 10 * 10111                                   = 101711
 * </pre>
 *
 * Nine leading digits times 101711 is 915399, which is exactly the size of the
 * A00001-ADF7C7 block — the grammar and the allocation agree, which is what
 * makes the decode exact rather than approximate.
 */
public final class UsTailNumber {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int FIRST = 0xA00001;
    private static final int LAST = 0xADF7C7;

    private static final int BUCKET_1 = 101711;
    private static final int BUCKET_2 = 10111;
    private static final int BUCKET_3 = 951;
    private static final int BUCKET_4 = 35;

    /** Number of endings that are "nothing, one letter, or two letters". */
    private static final int LETTER_ENDINGS = 601;

    private UsTailNumber() {}

    /**
     * @param icao24 lowercase 6-digit hex address as OpenSky reports it
     * @return the N-number, or null when the address is malformed or is not in
     *         the US block (other countries' blocks are allocated from registries
     *         rather than encoded, so they cannot be derived this way)
     */
    public static String fromIcao24(String icao24) {
        if (icao24 == null || icao24.isBlank()) return null;

        int address;
        try {
            address = Integer.parseInt(icao24.trim(), 16);
        } catch (NumberFormatException e) {
            return null;
        }
        if (address < FIRST || address > LAST) return null;

        int remainder = address - FIRST;
        StringBuilder out = new StringBuilder("N");

        out.append(remainder / BUCKET_1 + 1);
        remainder %= BUCKET_1;
        if (remainder < LETTER_ENDINGS) return out.append(ending(remainder)).toString();

        remainder -= LETTER_ENDINGS;
        out.append(remainder / BUCKET_2);
        remainder %= BUCKET_2;
        if (remainder < LETTER_ENDINGS) return out.append(ending(remainder)).toString();

        remainder -= LETTER_ENDINGS;
        out.append(remainder / BUCKET_3);
        remainder %= BUCKET_3;
        if (remainder < LETTER_ENDINGS) return out.append(ending(remainder)).toString();

        remainder -= LETTER_ENDINGS;
        out.append(remainder / BUCKET_4);
        remainder %= BUCKET_4;
        if (remainder < 25) return out.append(ending(remainder)).toString();

        // Past the letter endings, what is left is the fifth and final digit.
        return out.append(remainder - 25).toString();
    }

    /** 0 is no ending, 1-24 a single letter, 25-600 a letter pair. */
    private static String ending(int n) {
        if (n == 0) return "";
        if (n <= 24) return String.valueOf(ALPHABET.charAt(n - 1));
        int pair = n - 25;
        return "" + ALPHABET.charAt(pair / 24) + ALPHABET.charAt(pair % 24);
    }
}
