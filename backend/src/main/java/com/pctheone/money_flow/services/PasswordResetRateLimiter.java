package com.pctheone.money_flow.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

//---- Max MAX_REQUESTS reset requests per email in a rolling WINDOW, counted whether or not the email exists.
//---- In memory: the app runs as a single instance, and a restart only resets the counters.
@Component
public class PasswordResetRateLimiter {

    static final int MAX_REQUESTS = 3;
    static final Duration WINDOW = Duration.ofHours(1);

    private final Map<String, Deque<Instant>> requests = new ConcurrentHashMap<>();

    @Autowired
    Clock clock;

    public static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    //---- Records the request and returns true when allowed; a rejected request is not recorded.
    public boolean tryAcquire(String email) {
        Instant now = clock.instant();
        boolean[] allowed = {false};
        requests.compute(normalize(email), (key, timestamps) -> {
            Deque<Instant> recent = timestamps != null ? timestamps : new ArrayDeque<>();
            dropExpired(recent, now);
            if (recent.size() < MAX_REQUESTS) {
                recent.addLast(now);
                allowed[0] = true;
            }
            return recent;
        });
        return allowed[0];
    }

    //---- Drops emails whose requests all left the window, so the map does not grow forever.
    @Scheduled(fixedRate = 60 * 60 * 1000)
    public void purgeExpired() {
        Instant now = clock.instant();
        requests.keySet().forEach(key -> requests.computeIfPresent(key, (k, timestamps) -> {
            dropExpired(timestamps, now);
            return timestamps.isEmpty() ? null : timestamps;
        }));
    }

    int trackedEmails() {
        return requests.size();
    }

    private static void dropExpired(Deque<Instant> timestamps, Instant now) {
        Instant windowStart = now.minus(WINDOW);
        while (!timestamps.isEmpty() && !timestamps.peekFirst().isAfter(windowStart))
            timestamps.pollFirst();
    }
}
