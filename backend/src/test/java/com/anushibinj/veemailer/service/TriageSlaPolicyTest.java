package com.anushibinj.veemailer.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TriageSlaPolicyTest {

    @Test
    void toDisplayLabel_GreenRange() {
        assertEquals("\uD83D\uDFE2 2 days old", TriageSlaPolicy.toDisplayLabel(2));
    }

    @Test
    void toDisplayLabel_YellowRange() {
        assertEquals("\uD83D\uDFE1 3 days old", TriageSlaPolicy.toDisplayLabel(3));
    }

    @Test
    void toDisplayLabel_RedRange() {
        assertEquals("\uD83D\uDD34 4 days old", TriageSlaPolicy.toDisplayLabel(4));
    }
}
