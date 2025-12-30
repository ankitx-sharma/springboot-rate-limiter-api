package com.project.rate_limiter.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.project.rate_limiter.entity.RateLimiterDecision;
import com.project.rate_limiter.entity.UserRequestInfo;

@Service
public class FixedSizeRateLimiterService {
	
	@Value("${rate.request.limit.count}")
	private int REQUEST_LIMIT;
	
	@Value("${rate.request.limit.timeperiod}")
	private long TIME_WINDOW_MS;
	
	private Map<String, UserRequestInfo> userRequestMap = new HashMap<>();
	
	public RateLimiterDecision decision(String user) {
		long now = Instant.now().toEpochMilli();
		return decision(user, now);
	}
	
	public RateLimiterDecision decision(String user, long currentTime) {
		UserRequestInfo userInfo =  userRequestMap.getOrDefault(user, new UserRequestInfo(currentTime, 0, null));
		
		// window reset?
		if(currentTime - userInfo.getLimitWindowStart() > TIME_WINDOW_MS) {
			userInfo.setNumberOfRequestsMade(1);
			userInfo.setLimitWindowStart(currentTime);
			userRequestMap.put(user, userInfo);
			
			int remaining = Math.max(0, REQUEST_LIMIT-1);
			long resetInMs = TIME_WINDOW_MS;
			return new RateLimiterDecision(true, remaining, 0L, resetInMs);
		}
		
		// within current window
		if(userInfo.getNumberOfRequestsMade() < REQUEST_LIMIT) {
			userInfo.setNumberOfRequestsMade(userInfo.getNumberOfRequestsMade()+1);
			userRequestMap.put(user, userInfo);
			
			int remaining = Math.max(0, REQUEST_LIMIT - userInfo.getNumberOfRequestsMade());
			long resetInMs = TIME_WINDOW_MS - (currentTime - userInfo.getLimitWindowStart());
			return new RateLimiterDecision(true, remaining, 0L, resetInMs);
		}
		
		// blocked
		long resetInMs = TIME_WINDOW_MS - (currentTime - userInfo.getLimitWindowStart());
		return new RateLimiterDecision(false, 0, resetInMs, resetInMs);
	}
	
	public boolean isAllowed(String user) {
		return decision(user).isAllowed();
	}
}