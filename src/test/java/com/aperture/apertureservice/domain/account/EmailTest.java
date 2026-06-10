package com.aperture.apertureservice.domain.account;

import com.aperture.apertureservice.ddd.BadRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailTest {

    @Test
    void normalizesToLowercase() {
        assertThat(new Email("User@Example.COM").value()).isEqualTo("user@example.com");
    }

    @Test
    void rejectsInvalidShapes() {
        for (String bad : new String[]{"", "plain", "a@b", "a @b.com", "a@b .com", "@b.com", "a@.com"}) {
            assertThatThrownBy(() -> new Email(bad))
                    .isInstanceOf(BadRequest.class)
                    .hasFieldOrPropertyWithValue("code", "EMAIL_INVALID");
        }
    }
}
