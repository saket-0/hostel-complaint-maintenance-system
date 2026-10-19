package edu.vit.hcms.core.util;

import java.time.Year;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe Complaint ID generator producing identifiers in the format:
 * {@code HCM-YYYY-NNNNNN} (e.g., {@code HCM-2026-000042}).
 *
 * <p>Thread-safety:
 * Uses {@link AtomicLong#incrementAndGet()} to ensure lock-free, atomic, and
 * monotonically increasing sequence numbers under high concurrent workloads.
 */
public class ComplaintIdGenerator {

    private final AtomicLong sequence;
    private final int year;

    public ComplaintIdGenerator() {
        this(Year.now().getValue(), 0);
    }

    public ComplaintIdGenerator(int year, long initialSequence) {
        this.year = year;
        this.sequence = new AtomicLong(initialSequence);
    }

    /**
     * Generates the next unique, thread-safe complaint ID.
     *
     * @return formatted complaint ID string
     */
    public String nextId() {
        long seq = sequence.incrementAndGet();
        return String.format("HCM-%d-%06d", year, seq);
    }

    /**
     * Returns the current underlying sequence value.
     *
     * @return current sequence number
     */
    public long getCurrentSequence() {
        return sequence.get();
    }
}
