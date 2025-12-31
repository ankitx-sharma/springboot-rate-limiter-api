package com.project.rate_limiter.controller.dto;

import java.util.List;
import java.util.Map;

public record DemoRunResponse (
	String scenario,
	String algorithm,
	String userId,
	Map<String, Object> notes,
	int callsThatWereAllowedCount,
	int callsThatWereBlockedCount,
	List<DemoEvent> timeline
) {}