# Rate Limiter Service (Spring Boot)

A lightweight **Rate Limiting microservice** built with **Java & Spring Boot**.  
It supports multiple classic rate-limiting algorithms and allows **dynamic algorithm selection per request** using HTTP headers.

This project is designed as a **reusable, pluggable component** that can be embedded into other backend systems or used as a standalone service.

---

## Table of Contents
- Features
- Architecture Overview
- Supported Algorithms
- Configuration
- Quick Start (Local)
- Docker
- API Usage
- Demo Endpoints
- Extensibility
- Troubleshooting
- References

---

## Features
- **Three Rate Limiting Algorithms**
  - Token Bucket (default)
  - Fixed Window
  - Sliding Window
- **Global request filtering** using `OncePerRequestFilter`
- **Per-request algorithm selection** via `X-RateLimit-Algorithm` header
- **Flexible key strategy**
  - Primary: `X-User-Id` header
  - Fallback: client IP address
- **Rich rate-limit response headers**
  - `X-RateLimit-Remaining`
  - `X-RateLimit-RetryAfter-Ms`
  - `X-RateLimit-ResetIn-Ms`
  - Standard `Retry-After` header on HTTP `429`
- **Swagger / OpenAPI documentation**
- Docker & Docker Compose support
- Redis dependency included for future distributed rate-limiting

---

## Architecture Overview
The service uses a **filter-based architecture**:

### Core Components
- **RateLimiterFilter**
  - Intercepts incoming HTTP requests
  - Determines the rate-limiting key (User ID or IP)
  - Selects the algorithm based on request headers
  - Applies rate limiting and blocks requests if necessary
- **Rate Limiter Services**
  - `TokenBucketRateLimiterService`
  - `FixedSizeRateLimiterService`
  - `SlidingWindowRateLimiterService`
- **Decision Model**
  - `RateLimiterDecision` encapsulates:
    - allow/deny decision
    - remaining quota
    - retry/reset timing

> Current implementation is **in-memory per instance**.  
> For distributed setups, Redis can be used as a shared state store.

---

## Supported Algorithms

### 1. Token Bucket (Default)
- Allows bursts up to a fixed capacity
- Tokens refill at a fixed rate per second
- Each request consumes one token

**Best for:** APIs that need smooth traffic handling with bursts.

---

### 2. Fixed Window
- Time is divided into fixed windows (e.g. 6 seconds)
- Requests are counted per window

**Best for:** Simple and fast rate limiting with predictable limits.

---

### 3. Sliding Window
- Stores timestamps of requests
- Removes outdated entries dynamically
- Provides fairer request distribution than fixed windows

**Best for:** Accurate rate limiting under spiky traffic.

---

## Configuration
All configuration is located in:

`src/main/resources/application.properties`

```properties
# Max number of requests / tokens
rate.request.limit.count=5

# Time window in milliseconds (Fixed & Sliding Window)
rate.request.limit.timeperiod=6000

# Token refill rate per second (Token Bucket)
rate.request.limit.refill.rate=1

# Swagger configuration
springdoc.api-docs.path=/v3/api-docs
springdoc-swagger-ui.path=swagger-ui.html
