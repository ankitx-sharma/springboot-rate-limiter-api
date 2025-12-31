package com.project.rate_limiter.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.project.rate_limiter.controller.dto.DemoEvent;
import com.project.rate_limiter.controller.dto.DemoRunRequest;
import com.project.rate_limiter.controller.dto.DemoRunResponse;

import io.swagger.v3.oas.annotations.parameters.RequestBody;

@RestController
@RequestMapping("/limiter/demo")
public class DemoRunController {
	
	private final RestTemplate restTemplate;
	
	@Value("${rate.request.limit.count}")
	private int limit;
	
	@Value("${rate.request.limit.timeperiod}")
	private long windowMs;
	
	@Value("${rate.request.limit.refill.rate:1}")
	private int refillRate;

	public DemoRunController(RestTemplate restTemplate) {
		this.restTemplate = restTemplate;
	}

	@PostMapping("/run")
	public void run(@RequestBody DemoRunRequest request) throws InterruptedException{
		String alg = (request.algorithm() == null || request.algorithm().isBlank() ) ? "FIXED_WINDOW" : request.algorithm();
		String scenario = (request.scenario() == null || request.scenario().isBlank() ) ? "FIXED_WINDOW_BOUNDARY_BURST" : request.scenario();
		String userId = (request.userId() == null || request.userId().isBlank() ) ? "demo_user" : request.userId();
		
		long start = System.currentTimeMillis();
		List<DemoEvent> timeline = new ArrayList<>();
		
		Map<String, Object> notes = new HashMap<>();
		notes.put("limit", limit);
		notes.put("windowMs", windowMs);
		notes.put("tokenBucketRefillPerSec", refillRate);
		notes.put("howToRead", "status=200 allowed, status=429 blocked; headers show Remaining / RetryAfter / ResetIn");
		
		switch(scenario) {
			case "FIXED_WINDOW_BOUNDARY_BURST" -> runFixedWindowBoundaryBurst(alg, userId, start, timeline, notes);
			case "SLIDING_WINDOW_SMOOTH" -> runSlidingWindowSmooth(alg, userId, start, timeline, notes);
			case "TOKEN_BUCKET_BURST_REFILL" -> runTokenBucketBurstRefill(alg, userId, start, timeline, notes);
			default -> {
				notes.put("error", "Unknown scenario. Use FIXED_WINDOW_BOUNDARY_BURST | SLIDING_WINDOW_SMOOTH | TOKEN_BUCKET_BURST_REFILL");
			}
		}
		
		int allowed = (int) timeline.stream().filter(e -> e.status == 200).count();
		int blocked = (int) timeline.stream().filter(e -> e.status == 429).count();
		
		notes.put("explanation", switch (scenario) {
	        case "FIXED_WINDOW_BOUNDARY_BURST" ->
	        "Fixed Window: kurz vor Window-Ende Burst + direkt nach Reset wieder Burst -> 'double dip' / uneven load.";
			case "SLIDING_WINDOW_SMOOTH" ->
			        "Sliding Window: kein harter Reset; Block/Allow wird 'smooth', aber mehr bookkeeping (timestamps).";
			case "TOKEN_BUCKET_BURST_REFILL" ->
			        "Token Bucket: erlaubt Burst bis Capacity, dann block; danach kommen Allows steady mit Refill.";
			default -> "";
		});
		
		new DemoRunResponse(scenario, alg, userId, notes, allowed, blocked, timeline);
	}
	
	private void runFixedWindowBoundaryBurst(String alg, String userId, long start, 
			List<DemoEvent> timeline, Map<String, Object> notes) throws InterruptedException {
		
		callCheck(alg, userId, start, timeline, 1);
		
		long waitMs = Math.max(0, windowMs - 200);
		Thread.sleep(waitMs);
		
		//Limit - 1: burst calls 
		for(int i=0; i<Math.max(0, limit-1); i++) {
			callCheck(alg, userId, start, timeline, timeline.size() + 1);
		}
		
		//short delay at boundary
		Thread.sleep(250);
		
		//Burst call again
		for(int i=0; i<limit; i++) {
			callCheck(alg, userId, start, timeline, timeline.size() + 1);
		}
		
		notes.put("trigger", "Prime -> wait(windowMs-200ms) -> burst -> sleep(250ms) -> burst");
	}
	
	private void runSlidingWindowSmooth(String alg, String userId, long start,
			List<DemoEvent> timeline, Map<String, Object> notes) throws InterruptedException {
		
		for(int i=0; i<limit; i++) {
			callCheck(alg, userId, start, timeline, i+1);
		}
		
		for(int i=0; i<5; i++) {
			callCheck(alg, userId, start, timeline, timeline.size()+1);
		}
		
		Thread.sleep(Math.max(200, windowMs / 2));
		
		for(int i=0; i<5; i++) {
			callCheck(alg, userId, start, timeline, timeline.size()+1);
			Thread.sleep(150);
		}
		
		notes.put("trigger", "burst(limit) -> extra(5) -> sleep(window/2) -> spaced requests");
	}
	
	private void runTokenBucketBurstRefill(String alg, String userId, long start,
			List<DemoEvent> timeline, Map<String, Object> notes) throws InterruptedException {
		
		int burst = limit + 3;
		
		for(int i=0; i<burst; i++) {
			callCheck(alg, userId, start, timeline, i+1);
		}
		
		for(int i=0; i<5; i++) {
			Thread.sleep(1000);
			callCheck(alg, userId, start, timeline, timeline.size()+1);
		}
		
		notes.put("trigger", "burst(limit+3) -> then 1 req/sec x5");
		
	}
	
	private void callCheck(String alg, String userId, long start, 
			List<DemoEvent> timeline, int index) {
		
		HttpHeaders headers = new HttpHeaders();
		headers.set("X-RateLimit-Alg", alg);
		headers.set("X-User-Id", userId);
		
		HttpEntity<Void> entity = new HttpEntity<>(headers);
		
		ResponseEntity<String> res = restTemplate.exchange(
				"http://localhost:8080/limiter/api/check",
				HttpMethod.GET,
				entity,
				String.class);
		
		long tMs = System.currentTimeMillis() - start;
		
		HttpHeaders resultHeaders = res.getHeaders();
		long remaining = parseLong(resultHeaders.getFirst("X-RateLimit-Remaining"));
		long retryAfterMs = parseLong(resultHeaders.getFirst("X-RateLimit-RetryAfter-Ms"));
		long resetInMs = parseLong(resultHeaders.getFirst("X-RateLimit-ResetIn-Ms"));
		
		timeline.add(new DemoEvent(index, tMs, res.getStatusCode().value(), remaining, retryAfterMs, resetInMs));
	}
	
	private long parseLong(String v) {
		try { return (v==null) ? 0 : Long.parseLong(v); }
		catch (Exception ex) { return 0; }
	}
}
