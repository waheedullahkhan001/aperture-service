package com.aperture.apertureservice.infrastructure.controller;

import com.aperture.apertureservice.ddd.BadRequest;
import com.aperture.apertureservice.ddd.Conflict;
import com.aperture.apertureservice.ddd.Forbidden;
import com.aperture.apertureservice.ddd.NotFound;
import com.aperture.apertureservice.ddd.Unauthorized;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemDetailAdviceTest {

    private final ProblemDetailAdvice advice = new ProblemDetailAdvice();

    @Test
    void mapsDomainExceptionFamiliesToStatusesAndCode() {
        assertThat(advice.handleDomain(new BadRequest("X", "x")).getStatus()).isEqualTo(400);
        assertThat(advice.handleDomain(new Unauthorized("X", "x")).getStatus()).isEqualTo(401);
        assertThat(advice.handleDomain(new Forbidden("X", "x")).getStatus()).isEqualTo(403);
        assertThat(advice.handleDomain(new NotFound("X", "x")).getStatus()).isEqualTo(404);
        assertThat(advice.handleDomain(new Conflict("X", "x")).getStatus()).isEqualTo(409);
        ProblemDetail pd = advice.handleDomain(new Conflict("EMAIL_TAKEN", "taken"));
        assertThat(pd.getProperties()).containsEntry("code", "EMAIL_TAKEN");
        assertThat(pd.getDetail()).isEqualTo("taken");
    }

    @Test
    void mapsMalformedBodyToProblem() {
        ProblemDetail pd = advice.handleUnreadable(
                new HttpMessageNotReadableException("bad json",
                        (org.springframework.http.HttpInputMessage) null));
        assertThat(pd.getStatus()).isEqualTo(400);
        assertThat(pd.getProperties()).containsEntry("code", "MALFORMED_REQUEST");
    }
}
