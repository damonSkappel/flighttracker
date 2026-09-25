package com.damonskappel.flighttracker;

import com.damonskappel.flighttracker.util.UsTailNumber;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every expected value here is a real address/registration pair from the FAA
 * registry, as carried in OpenSky's aircraft database. The decoder was once
 * reasoned out without a check like this and got every letter-pair ending wrong.
 */
class UsTailNumberCheck {

    private static void assertDecodes(String icao24, String registration) {
        assertEquals(registration, UsTailNumber.fromIcao24(icao24), icao24);
    }

    @Test
    void eachLetterIsFollowedByItsPairsBeforeTheNextLetter() {
        assertDecodes("a00001", "N1");
        assertDecodes("a00002", "N1A");
        assertDecodes("a00003", "N1AA");   // was decoded as N1B
        assertDecodes("a00005", "N1AC");
        assertDecodes("a0001b", "N1B");    // 1 + 25 slots after N1A
    }

    @Test
    void decodesEveryDigitLevel() {
        assertDecodes("a0025c", "N10AA");
        assertDecodes("a004b4", "N100A");
        assertDecodes("a004b6", "N100AB");
        assertDecodes("a0070d", "N1000A");  // after four digits only one letter fits
        assertDecodes("a00726", "N10001");
    }

    @Test
    void decodesRealAirliners() {
        assertDecodes("a009a4", "N101NN");  // American A321
        assertDecodes("a448d1", "N37502");  // United 737 MAX 9
        assertDecodes("ac0f93", "N8764Q");  // Southwest 737
        assertDecodes("ad3c6d", "N952DN");  // Delta MD-90
        assertDecodes("adf669", "N999ZZ");
    }

    @Test
    void coversExactlyTheUsBlock() {
        assertDecodes("adf7c7", "N99999");
        assertNull(UsTailNumber.fromIcao24("a00000"));
        assertNull(UsTailNumber.fromIcao24("adf7c8"));
        assertNull(UsTailNumber.fromIcao24("3c6444"), "German address");
    }

    @Test
    void rejectsMalformedInput() {
        assertNull(UsTailNumber.fromIcao24(null));
        assertNull(UsTailNumber.fromIcao24(" "));
        assertNull(UsTailNumber.fromIcao24("zzzzzz"));
        assertDecodes("A00003", "N1AA");
    }
}
