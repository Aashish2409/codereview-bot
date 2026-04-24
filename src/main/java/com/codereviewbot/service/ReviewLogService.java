package com.codereviewbot.service;

import com.codereviewbot.model.ReviewRecord;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory log of all PR review attempts.
 * Exposed via REST API so the frontend dashboard can display recent activity.
 *
 * NOTE: This is intentionally in-memory (not a database).
 * Data resets on each app restart. For a production system you would
 * persist this in PostgreSQL — but for a demo/interview project, this is perfect.
 *
 * Thread safety: CopyOnWriteArrayList handles concurrent webhook requests
 * without needing explicit synchronization.
 */
@Service
public class ReviewLogService {

    /** Maximum records to keep in memory (prevents unbounded growth) */
    private static final int MAX_RECORDS = 100;

    private final CopyOnWriteArrayList<ReviewRecord> records = new CopyOnWriteArrayList<>();

    /**
     * Adds a new review record to the top of the log.
     * Automatically trims to MAX_RECORDS if needed.
     */
    public void add(ReviewRecord record) {
        records.add(0, record); // newest first
        if (records.size() > MAX_RECORDS) {
            records.remove(records.size() - 1);
        }
    }

    /**
     * Returns all stored review records (newest first).
     * Returns an unmodifiable view to prevent external mutation.
     */
    public List<ReviewRecord> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(records));
    }

    /** Returns the total count of reviews processed so far. */
    public int getTotalCount() {
        return records.size();
    }

    /** Returns count of reviews with SUCCESS status. */
    public long getSuccessCount() {
        return records.stream()
            .filter(r -> r.getStatus() == ReviewRecord.Status.SUCCESS)
            .count();
    }
}
