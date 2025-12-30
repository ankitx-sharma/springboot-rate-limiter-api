package com.project.rate_limiter.service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.project.rate_limiter.entity.RateLimiterDecision;
import com.project.rate_limiter.entity.UserRequestInfo;

@Service
public class SlidingWindowRateLimiterService {
	
	@Value("${rate.request.limit.count}")
	private int REQUEST_LIMIT;
	
	@Value("${rate.request.limit.timeperiod}")
	private long TIME_WINDOW_MS;
	
	Map<String, UserRequestInfo> userRequestMap = new HashMap<>();
	
	public RateLimiterDecision decision(String user) {
		long now = Instant.now().toEpochMilli();
		return decision(user, now);
	}
	
	public RateLimiterDecision decision(String user, long currentTime) {
		UserRequestInfo userInfo = userRequestMap.getOrDefault(user, 
				new UserRequestInfo(currentTime, 0, new ArrayDeque<>()));
		
		int originalQueueSize = userInfo.getRequestList().size();
		userInfo.getRequestList().removeIf(val -> currentTime - val > TIME_WINDOW_MS);
		
		int requestsDataPurged = originalQueueSize - userInfo.getRequestList().size();
		userInfo.setNumberOfRequestsMade(userInfo.getNumberOfRequestsMade() - requestsDataPurged);
		
		if(userInfo.getRequestList().size() >= REQUEST_LIMIT) {
			long oldest = userInfo.getRequestList().peekFirst();
			long retryAfterMs = Math.max(0, (currentTime - oldest));
			return new RateLimiterDecision(false, 0, retryAfterMs, retryAfterMs);
		}
		
		//allow
		userInfo.getRequestList().addLast(currentTime);
		userInfo.setNumberOfRequestsMade(userInfo.getNumberOfRequestsMade()+1);
		userRequestMap.put(user, userInfo);
		
		int remaining = Math.max(0, REQUEST_LIMIT - userInfo.getRequestList().size());
		long resetInMs = 0;
		if(!userInfo.getRequestList().isEmpty()) {
			long oldest = userInfo.getRequestList().peekLast();
			resetInMs = Math.max(0, TIME_WINDOW_MS - (currentTime - oldest));
		}
		

		return new RateLimiterDecision(false, remaining, resetInMs, resetInMs);
	}
	
	public boolean isAllowed(String user) {
		return decision(user).isAllowed();
	}
}