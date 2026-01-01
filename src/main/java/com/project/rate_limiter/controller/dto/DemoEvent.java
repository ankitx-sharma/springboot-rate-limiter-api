package com.project.rate_limiter.controller.dto;

public record DemoEvent(
	int status,
	long remaining,
	long retryAfterMs,
	String comment
){}