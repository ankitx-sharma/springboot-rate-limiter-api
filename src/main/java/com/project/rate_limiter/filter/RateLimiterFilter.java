package com.project.rate_limiter.filter;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.project.rate_limiter.constants.RateLimiterAlgorithm;
import com.project.rate_limiter.entity.RateLimiterDecision;
import com.project.rate_limiter.service.FixedSizeRateLimiterService;
import com.project.rate_limiter.service.SlidingWindowRateLimiterService;
import com.project.rate_limiter.service.TokenBucketRateLimiterService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class RateLimiterFilter extends OncePerRequestFilter{
	
	private FixedSizeRateLimiterService fixedSizeService;
	private SlidingWindowRateLimiterService slidingWService;
	private TokenBucketRateLimiterService tokenBService;
	
	public RateLimiterFilter(FixedSizeRateLimiterService fixedSizeService,
				SlidingWindowRateLimiterService slidingWService,
				TokenBucketRateLimiterService tokenBService) {
		this.fixedSizeService = fixedSizeService;
		this.slidingWService = slidingWService;
		this.tokenBService = tokenBService;
	}
	
	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI();
		
		return path.startsWith("/swagger-ui") || 
			   path.startsWith("/v3/api-docs") ||
			   path.startsWith("rate-api/");
	}
	
	@Override
	protected void doFilterInternal(HttpServletRequest request, 
			HttpServletResponse response, 
			FilterChain filterChain) throws ServletException, IOException {
		String algRaw = request.getHeader("X-RateLimit-ALg");
		
		if(algRaw == null || algRaw.isBlank()) {
			algRaw = request.getParameter("alg");
		}
		
		RateLimiterAlgorithm alg;
		try {
			alg = RateLimiterAlgorithm.from(algRaw);
		} catch(IllegalArgumentException ex) {
			response.setStatus(HttpStatus.BAD_REQUEST.value());
			response.getWriter().write("Invalid algorithm. Use TOKEN_BUCKET, FIXED_WINDOW, SLIDING_WINDOW");
			return;
		}
		
		String key = request.getHeader("X-User-Id");
		if(key == null || key.isBlank()) {
			key = request.getRemoteAddr();
		}
		
		RateLimiterDecision decision = switch(alg) {
			case TOKEN_BUCKET -> tokenBService.decision(key);
			case SLIDING_WINDOW -> slidingWService.decision(key);
			case FIXED_WINDOW -> fixedSizeService.decision(key);
		};
		
		response.setHeader("X-RateLimit-Algorithm", alg.name());
		response.setHeader("X-RateLimit-Key", key);
		response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
		response.setHeader("X-RateLimit-RetryAfter-Ms", String.valueOf(decision.retryAfterMs()));
		response.setHeader("X-RateLimit-ResetIn-Ms", String.valueOf(decision.resetInMs()));
		
		if(!decision.isAllowed()) {
			response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
			long retryAfterSeconds = Math.max(1, (long)Math.ceil(decision.retryAfterMs() / 1000L));
			response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
			return;
		}
		
		filterChain.doFilter(request, response);
	}	

}
