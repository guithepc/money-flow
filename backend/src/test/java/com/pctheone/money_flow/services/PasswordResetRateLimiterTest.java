package com.pctheone.money_flow.services;

import com.pctheone.money_flow.utils.MutableClock;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

public class PasswordResetRateLimiterTest {

    MutableClock clock = new MutableClock(Instant.parse("2026-10-23T12:00:00Z"));
    PasswordResetRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new PasswordResetRateLimiter();
        limiter.clock = clock;
    }

    @Test
    void fourthRequestWithinAnHourIsRejected() {
        Assertions.assertTrue(limiter.tryAcquire("g@example.com"));
        Assertions.assertTrue(limiter.tryAcquire("g@example.com"));
        Assertions.assertTrue(limiter.tryAcquire("g@example.com"));
        clock.advance(Duration.ofMinutes(59));

        Assertions.assertFalse(limiter.tryAcquire("g@example.com"));
        Assertions.assertTrue(limiter.tryAcquire("other@example.com"));
    }

    @Test
    void limitResetsAfterTheHour() {
        for (int i = 0; i < 3; i++)
            limiter.tryAcquire("g@example.com");
        Assertions.assertFalse(limiter.tryAcquire("g@example.com"));

        clock.advance(Duration.ofHours(1));

        Assertions.assertTrue(limiter.tryAcquire("g@example.com"));
    }

    @Test
    void windowIsRolling() {
        limiter.tryAcquire("g@example.com");
        clock.advance(Duration.ofMinutes(30));
        limiter.tryAcquire("g@example.com");
        limiter.tryAcquire("g@example.com");
        clock.advance(Duration.ofMinutes(31));

        //---- Only the first request left the window.
        Assertions.assertTrue(limiter.tryAcquire("g@example.com"));
        Assertions.assertFalse(limiter.tryAcquire("g@example.com"));
    }

    @Test
    void emailIsNormalized() {
        limiter.tryAcquire("g@example.com");
        limiter.tryAcquire(" G@Example.COM ");
        limiter.tryAcquire("G@EXAMPLE.COM");

        Assertions.assertFalse(limiter.tryAcquire("g@example.com  "));
    }

    @Test
    void purgeDropsEmailsOutsideTheWindow() {
        limiter.tryAcquire("old@example.com");
        clock.advance(Duration.ofMinutes(50));
        limiter.tryAcquire("recent@example.com");
        clock.advance(Duration.ofMinutes(20));

        limiter.purgeExpired();

        Assertions.assertEquals(1, limiter.trackedEmails());
    }
}
