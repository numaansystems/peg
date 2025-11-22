# PEG Gateway Architecture

## Overview

PEG (Proxy Gateway) is a Spring Cloud Gateway-based reverse proxy that provides centralized authentication using Azure AD OAuth2/OIDC. This document explains the architectural decisions and components of the gateway.

## Session Management Architecture

### Why Redis is Needed for Production Deployments

#### The Problem: Stateful Authentication in Distributed Systems

The PEG Gateway implements OAuth2/OIDC authentication with Azure AD, which requires maintaining **stateful sessions** for authenticated users. Here's why:

1. **OAuth2 Token Storage**: After successful Azure AD authentication, the gateway stores:
   - Access tokens
   - Refresh tokens  
   - ID tokens (containing user claims)
   - Token expiration times
   - User principal information

2. **Session-Based Authentication**: The gateway uses Spring Security's session-based authentication to:
   - Maintain user login state across requests
   - Avoid repeated OAuth2 flows for each request
   - Store authentication context securely

#### Single-Instance Deployments (In-Memory Sessions)

For development or single-instance deployments, **in-memory sessions work fine**:

```
┌─────────┐         ┌──────────────────┐         ┌─────────────┐
│ Client  │────────>│  PEG Gateway     │────────>│  Backend    │
│         │         │  (Single Pod)    │         │  Services   │
│         │         │  [Sessions: RAM] │         │             │
└─────────┘         └──────────────────┘         └─────────────┘
```

**This works because:**
- All requests from a client go to the same gateway instance
- Session data is stored in that instance's memory
- Session cookies reference data in that specific instance

#### Multi-Instance Deployments (Problem Without Redis)

In production, you need **multiple gateway instances** for:
- High availability (if one fails, others handle traffic)
- Load balancing (distribute traffic across instances)
- Zero-downtime deployments
- Horizontal scaling

**Without Redis, multi-instance deployments fail:**

```
┌─────────┐         ┌──────────────────┐         ┌─────────────┐
│         │────1───>│  Gateway Pod 1   │────────>│             │
│ Client  │         │  [Session: A]    │         │  Backend    │
│         │────2───>│  Gateway Pod 2   │────────>│  Services   │
│         │         │  [No Session]    │         │             │
└─────────┘         └──────────────────┘         └─────────────┘
                    ❌ User re-authenticates on every pod!
```

**Problems:**
1. **Request 1** goes to Pod 1: User authenticates, session stored in Pod 1's memory
2. **Request 2** goes to Pod 2: Session doesn't exist, user forced to re-authenticate
3. **Result**: Users constantly asked to log in, terrible user experience

#### Solution: Redis for Distributed Session Management

**With Redis, all gateway instances share session data:**

```
┌─────────┐         ┌──────────────────┐         ┌─────────────┐
│         │────1───>│  Gateway Pod 1   │────────>│             │
│         │         │                  │         │             │
│ Client  │         └──────────┬───────┘         │  Backend    │
│         │         ┌──────────┴───────┐         │  Services   │
│         │         │  Redis Cluster   │         │             │
│         │         │  [Shared Store]  │         │             │
│         │────2───>│  Gateway Pod 2   │────────>│             │
│         │         │                  │         │             │
└─────────┘         └──────────────────┘         └─────────────┘
                    ✅ Session available everywhere!
```

**How it works:**
1. **Request 1** (Pod 1): User authenticates → Session stored in Redis → Cookie sent to client
2. **Request 2** (Pod 2): Cookie received → Pod 2 retrieves session from Redis → User stays authenticated
3. **Result**: Seamless authentication across all gateway instances

### Redis Benefits

#### 1. Session Persistence
- Sessions survive gateway pod restarts
- No user disruption during deployments
- Session data persists beyond process lifetime

#### 2. Consistent User Experience
- Users authenticate once, work everywhere
- No repeated login prompts
- Smooth failover between gateway instances

#### 3. Horizontal Scalability
- Add/remove gateway instances without breaking sessions
- Auto-scaling works seamlessly
- Load balancers can distribute requests freely

#### 4. Operational Flexibility
- Rolling deployments without session loss
- Blue-green deployments without re-authentication
- Easy instance maintenance

### When You Don't Need Redis

**Skip Redis if you have:**

1. **Single Gateway Instance**: Development, testing, or small internal deployments
   ```yaml
   # application.yml (default - no Redis config needed)
   # Sessions stored in memory
   ```

2. **Stateless Authentication**: If you use JWT tokens or other stateless auth mechanisms
   - Not applicable to this gateway (uses OAuth2 session-based auth)

3. **Development Environment**: Local testing on your laptop
   ```bash
   mvn spring-boot:run  # Works without Redis
   ```

### When You Must Have Redis

**Redis is required for:**

1. **Kubernetes/Production Deployments**
   - Multiple gateway pods (replicas > 1)
   - Auto-scaling enabled
   - High availability requirements

2. **Load-Balanced Environments**
   - AWS/Azure load balancers
   - Nginx/HAProxy in front of multiple gateways
   - Round-robin or least-connections routing

3. **Zero-Downtime Deployments**
   - Rolling updates
   - Blue-green deployments
   - Canary releases

## Redis Configuration Options

### Basic Redis Setup

```yaml
spring:
  redis:
    host: localhost
    port: 6379
  session:
    store-type: redis
    timeout: 30m  # Session expiration
```

### Redis Cluster (High Availability)

```yaml
spring:
  redis:
    cluster:
      nodes:
        - redis-node1:6379
        - redis-node2:6379
        - redis-node3:6379
  session:
    store-type: redis
```

### Managed Redis Services

**Azure Cache for Redis:**
```yaml
spring:
  redis:
    host: <your-cache-name>.redis.cache.windows.net
    port: 6380
    ssl: true
    password: ${REDIS_PASSWORD}
```

**AWS ElastiCache:**
```yaml
spring:
  redis:
    host: <your-cluster>.cache.amazonaws.com
    port: 6379
```

## Security Considerations

### Session Data in Redis

The gateway stores these items in Redis sessions:
- OAuth2 authentication tokens (access, refresh, ID tokens)
- User principal (email, name, sub)
- Azure AD OAuth2 client state

### Security Best Practices

1. **Enable Redis Authentication:**
   ```yaml
   spring:
     redis:
       password: ${REDIS_PASSWORD}
   ```

2. **Use TLS/SSL for Redis Connections:**
   ```yaml
   spring:
     redis:
       ssl: true
   ```

3. **Network Isolation:**
   - Keep Redis in private network/VPC
   - Use security groups to restrict access
   - Only gateway pods should reach Redis

4. **Configure Session Timeout:**
   ```yaml
   spring:
     session:
       timeout: 30m  # Balance security vs UX
   ```

5. **Regular Token Rotation:**
   - Spring Security handles refresh token rotation
   - Redis ensures tokens are accessible for rotation

## Alternative Approaches (Not Used)

### Why Not Use Other Session Stores?

#### JDBC Session Store
```yaml
# Not recommended for gateways
spring:
  session:
    store-type: jdbc
```
**Why not:**
- Higher latency than Redis
- Requires database schema management
- Less scalable for high-traffic scenarios
- Overkill for session storage only

#### Hazelcast
```yaml
# Alternative option
spring:
  session:
    store-type: hazelcast
```
**Why not:**
- More complex setup
- Larger operational footprint
- Redis is industry standard for session caching

#### Sticky Sessions (Load Balancer)
**Why not:**
- Defeats purpose of multiple instances
- Poor failover behavior (sessions lost on pod failure)
- Complicates load balancer configuration
- Against cloud-native best practices

## Monitoring Redis

### Key Metrics to Monitor

1. **Connection Pool:**
   - Active connections
   - Idle connections
   - Wait time for connections

2. **Session Statistics:**
   - Total sessions stored
   - Session creation rate
   - Session expiration rate

3. **Redis Health:**
   - Memory usage
   - Eviction rate
   - Command latency

### Example Prometheus Queries

```promql
# Active sessions
redis_sessions_total{job="peg-gateway"}

# Session creation rate
rate(redis_sessions_created_total[5m])

# Redis memory usage
redis_memory_used_bytes
```

## Troubleshooting

### "Session expired" errors after deployment
- **Cause**: Redis not configured or unreachable
- **Solution**: Verify Redis connection and configuration

### Users forced to re-login frequently
- **Cause**: In-memory sessions with multiple pods
- **Solution**: Enable Redis session store

### Slow authentication
- **Cause**: Redis network latency
- **Solution**: Use Redis in same region/VPC as gateway

### Redis connection failures
- **Cause**: Network isolation, wrong credentials
- **Solution**: Check Redis host, port, password, and network rules

## Summary

| Deployment Type | Session Store | Redis Needed? |
|-----------------|--------------|---------------|
| Local Development | In-Memory | ❌ No |
| Single Instance | In-Memory | ❌ No |
| Kubernetes (replicas > 1) | Redis | ✅ Yes |
| Production Load-Balanced | Redis | ✅ Yes |
| High Availability | Redis | ✅ Yes |

**Bottom Line**: Redis is essential for any production deployment where you need multiple gateway instances for reliability, scalability, or availability. It enables seamless session sharing across all gateway pods, providing a consistent authentication experience for users.
