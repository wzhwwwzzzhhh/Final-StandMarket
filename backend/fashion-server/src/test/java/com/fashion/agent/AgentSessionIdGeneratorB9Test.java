package com.fashion.agent;

import com.fashion.util.AgentSessionIdGenerator;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentSessionIdGeneratorB9Test {

    @Test
    void generatesUniqueBase64UrlIdentifiersFromSixteenRandomBytes() {
        AgentSessionIdGenerator generator = new AgentSessionIdGenerator();
        Set<String> values = new HashSet<>();

        for (int i = 0; i < 128; i++) {
            String value = generator.generate();
            assertEquals(22, value.length());
            assertTrue(value.matches("[A-Za-z0-9_-]{22}"));
            values.add(value);
        }

        assertEquals(128, values.size());
    }

    @Test
    void rejectsLegacyAndMalformedIdentifiers() {
        AgentSessionIdGenerator generator = new AgentSessionIdGenerator();

        assertFalse(generator.isValid("0123456789abcdef"));
        assertFalse(generator.isValid("contains+nonurl/chars"));
        assertTrue(generator.isValid("abcdefghijklmnopqrstuv"));
    }
}
