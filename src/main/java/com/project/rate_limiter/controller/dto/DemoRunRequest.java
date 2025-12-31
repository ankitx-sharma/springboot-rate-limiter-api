package com.project.rate_limiter.controller.dto;

public record DemoRunRequest (
	String scenario,
	String algorithm,
	String userId
) {}