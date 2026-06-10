package com.aperture.apertureservice.domain.account;

import com.aperture.apertureservice.ddd.BadRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    @Test
    void acceptsSrs004CompliantPassword() {
        PasswordPolicy.check("abcdef1!");   // 8 chars, 1 digit, 1 special
    }

    @Test
    void rejectsShortOrMissingClasses() {
        for (String bad : new String[]{null, "abcde1!", "abcdefg1", "abcdefg!", "abcdefgh"}) {
            assertThatThrownBy(() -> PasswordPolicy.check(bad)).as("input: %s", bad)
                    .isInstanceOf(BadRequest.class)
                    .hasFieldOrPropertyWithValue("code", "PASSWORD_POLICY");
        }
    }
}
