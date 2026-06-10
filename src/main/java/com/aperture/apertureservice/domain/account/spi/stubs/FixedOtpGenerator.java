package com.aperture.apertureservice.domain.account.spi.stubs;

import com.aperture.apertureservice.ddd.Stub;
import com.aperture.apertureservice.domain.account.spi.OtpGenerator;

@Stub
public class FixedOtpGenerator implements OtpGenerator {
    private final String code;

    public FixedOtpGenerator(String code) {
        this.code = code;
    }

    @Override
    public String sixDigits() {
        return code;
    }
}
