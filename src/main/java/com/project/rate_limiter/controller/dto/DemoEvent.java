package com.project.rate_limiter.controller.dto;

public record DemoEvent (
	String type,
	int index,
	long tMs,
	int status,
	long remaining,
	long retryAfterMs,
	long resetInMs
) {}