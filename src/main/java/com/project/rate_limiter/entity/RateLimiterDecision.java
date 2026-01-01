package com.project.rate_limiter.entity;

public record RateLimiterDecision(
		boolean isAllowed,
		int remaining,
		long retryAfterMs,
		long timeToFullMs
) {}