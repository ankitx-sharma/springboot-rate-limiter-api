package com.project.rate_limiter.helper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NotesHelper {
		
	public static Map<String, Object> baseNotes(String scenario, String algorithm, String userId
			, int limit, long windowMs, int refillRate) {
	    Map<String, Object> notes = new HashMap<>();

	    Map<String, Object> cfg = new HashMap<>();
	    cfg.put("limit", limit);
	    cfg.put("windowMs", windowMs);
	    cfg.put("tokenBucketRefillPerSec", refillRate);
	    cfg.put("algorithm", algorithm);
	    cfg.put("userId", userId);

	    notes.put("config", cfg);

	    notes.put("howToReadTimeline", List.of(
	        "Each timeline item is one request sent by the demo runner.",
	        "status=200 means allowed; status=429 means rate-limited.",
	        "remaining is the quota/tokens left after that request (0 means you're at the limit).",
	        "retryAfterMs tells roughly how long to wait until the next request is likely to succeed.",
	        "tMs is the time offset since the demo started (relative time)."
	    ));

	    // A tiny glossary, helps instantly
	    notes.put("glossary", Map.of(
	        "tMs", "time since demo start (ms)",
	        "status", "HTTP status from /limiter/api/check (200 allowed, 429 limited)",
	        "remaining", "how much quota/tokens are left",
	        "retryAfterMs", "suggested wait time until you may get 200 again",
	        "resetInMs", "time until a meaningful reset/refill point (varies by algorithm)"
	    ));

	    return notes;
	}

	public static void enrichNotesForScenario(Map<String, Object> notes, String scenario) {
	    switch (scenario) {
	        case "FIXED_WINDOW_BOUNDARY_BURST" -> notes.putAll(Map.of(
	            "title", "Fixed Window: boundary burst (double-dip) demonstration",
	            "whatThisDemoShows",
	                "Fixed Window resets counters at hard boundaries. If you time bursts around the boundary, you can get two bursts allowed back-to-back.",
	            "expectedPattern", List.of(
	                "You should see many 200s near the end of the window.",
	                "Then shortly after the boundary, you see another batch of 200s again (a sudden 'reset').",
	                "This produces an uneven load spike: more traffic is allowed in a short real-world interval."
	            ),
	            "howToSpotItFast", List.of(
	                "Look for two clusters of status=200 separated by a very small time gap.",
	                "In the second cluster, remaining jumps back up (reset effect)."
	            ),
	            "whyItHappens", List.of(
	                "Counters are bucketed into fixed time slices (e.g., per 10 seconds).",
	                "At the window boundary, the counter resets even if requests were just sent moments ago."
	            ),
	            "tradeoffs", Map.of(
	                "pros", List.of("Very simple to implement", "Low memory usage"),
	                "cons", List.of("Boundary burst (double-dip) problem", "Uneven load near resets")
	            ),
	            "tryNext", List.of(
	                "Run the same scenario with SLIDING_WINDOW and compare: the second 'reset cluster' should not appear.",
	                "Increase limit and rerun to see how the spike size scales."
	            )
	        ));

	        case "SLIDING_WINDOW_SMOOTH" -> notes.putAll(Map.of(
	            "title", "Sliding Window: smoother limiting (no hard reset)",
	            "whatThisDemoShows",
	                "Sliding Window enforces the limit over the last N milliseconds continuously, so there is no sudden reset at a boundary.",
	            "expectedPattern", List.of(
	                "After you hit the limit, you see 429s.",
	                "As time passes, older requests fall out of the window, and 200s return gradually (not all at once)."
	            ),
	            "howToSpotItFast", List.of(
	                "You should NOT see a sudden jump back to a big remaining value at a fixed boundary time.",
	                "You’ll see a smoother transition from 429 back to 200."
	            ),
	            "whyItHappens", List.of(
	                "The algorithm tracks request timestamps in a rolling window.",
	                "When the oldest request expires (falls out), capacity returns gradually."
	            ),
	            "tradeoffs", Map.of(
	                "pros", List.of("Fairer and smoother behavior", "Avoids boundary burst spikes"),
	                "cons", List.of("More bookkeeping (timestamps/queues)", "Higher CPU/memory than Fixed Window")
	            ),
	            "tryNext", List.of(
	                "Compare retryAfterMs values vs Fixed Window — Sliding Window tends to guide you more smoothly.",
	                "Increase request burst size and observe how long it takes for 200s to return."
	            )
	        ));

	        case "TOKEN_BUCKET_BURST_REFILL" -> notes.putAll(Map.of(
	            "title", "Token Bucket: burst allowance + steady refill",
	            "whatThisDemoShows",
	                "Token Bucket allows short bursts up to the bucket capacity and then refills tokens at a steady rate.",
	            "expectedPattern", List.of(
	                "You should see an initial burst of 200s until remaining reaches 0.",
	                "Then 429s appear immediately after tokens are depleted.",
	                "After waiting, isolated 200s return as tokens refill (steady recovery)."
	            ),
	            "howToSpotItFast", List.of(
	                "Look for remaining decreasing to 0 quickly during the burst.",
	                "Then see 429s, followed by occasional 200s spaced out by time (refill effect)."
	            ),
	            "whyItHappens", List.of(
	                "Tokens accumulate over time up to a maximum (capacity).",
	                "Each request consumes a token; if none remain, the request is blocked until refill adds tokens back."
	            ),
	            "tradeoffs", Map.of(
	                "pros", List.of("Great for APIs that allow bursts", "Stable long-term average rate"),
	                "cons", List.of("Tuning matters (capacity/refill)", "Too-high capacity can still allow big spikes")
	            ),
	            "tryNext", List.of(
	                "Change refill rate and rerun: lower refill makes recovery slower.",
	                "Change capacity and rerun: higher capacity allows a bigger initial burst."
	            )
	        ));

	        default -> notes.put("title", "Unknown scenario");
	    }
	}

	
}
