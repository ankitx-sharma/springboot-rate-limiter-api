package com.project.rate_limiter.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/limiter/api")
public class RateLimiterController {
	
	@Operation(hidden = true)
	@GetMapping("/check")
	public ResponseEntity<String> isAllowed(){
		return ResponseEntity.ok("Request Allowed");
	}
}
