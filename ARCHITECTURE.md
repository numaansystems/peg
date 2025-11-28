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

## JDBC Session Store (Alternative to Redis)

### Using Oracle Database for Session Storage

While Redis is recommended for most scenarios, **JDBC session storage with Oracle database** is a viable alternative, especially if:
- Your organization already has Oracle database infrastructure
- Database operations are more familiar to your team
- You need persistent session storage with existing backup/recovery procedures
- Compliance requirements mandate database-backed storage

### JDBC Configuration

```yaml
spring:
  datasource:
    url: jdbc:oracle:thin:@<oracle-host>:1521/<service-name>
    username: ${ORACLE_USERNAME}
    password: ${ORACLE_PASSWORD}
    driver-class-name: oracle.jdbc.OracleDriver
    hikari:
      maximum-pool-size: 20
      minimum-idle: 10
      connection-timeout: 30000
  session:
    store-type: jdbc
    timeout: 30m
    jdbc:
      initialize-schema: always  # Auto-creates session tables
```

### How JDBC Sessions Work

```
┌─────────┐         ┌──────────────────┐         ┌─────────────┐
│         │────1───>│  Gateway Pod 1   │────────>│             │
│         │         │                  │         │             │
│ Client  │         └──────────┬───────┘         │  Backend    │
│         │         ┌──────────┴───────┐         │  Services   │
│         │         │  Oracle Database │         │             │
│         │         │  (SPRING_SESSION)│         │             │
│         │────2───>│  Gateway Pod 2   │────────>│             │
│         │         │                  │         │             │
└─────────┘         └──────────────────┘         └─────────────┘
                    ✅ Sessions shared via database!
```

### Database Schema

Spring Session automatically creates these tables in Oracle:

```sql
-- Main session table
CREATE TABLE SPRING_SESSION (
  PRIMARY_ID CHAR(36) NOT NULL,
  SESSION_ID CHAR(36) NOT NULL,
  CREATION_TIME NUMBER(19) NOT NULL,
  LAST_ACCESS_TIME NUMBER(19) NOT NULL,
  MAX_INACTIVE_INTERVAL NUMBER(10) NOT NULL,
  EXPIRY_TIME NUMBER(19) NOT NULL,
  PRINCIPAL_NAME VARCHAR2(100),
  CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);

-- Session attributes table (stores OAuth2 tokens, user info)
CREATE TABLE SPRING_SESSION_ATTRIBUTES (
  SESSION_PRIMARY_ID CHAR(36) NOT NULL,
  ATTRIBUTE_NAME VARCHAR2(200) NOT NULL,
  ATTRIBUTE_BYTES BLOB NOT NULL,
  CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
  CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID) 
    REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE
);

-- Performance indexes
CREATE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);
```

### Redis vs JDBC Comparison

| Feature | Redis | JDBC (Oracle) |
|---------|-------|---------------|
| **Performance** | ⭐⭐⭐⭐⭐ Sub-millisecond | ⭐⭐⭐ 5-20ms per operation |
| **Latency** | <1ms typical | 5-20ms typical |
| **Throughput** | Very high (100K+ ops/sec) | Moderate (depends on DB sizing) |
| **Setup Complexity** | Low (standalone service) | Medium (requires DB setup) |
| **Operational Knowledge** | Redis-specific | Standard database ops |
| **Infrastructure Cost** | Dedicated Redis cluster | Use existing DB |
| **Persistence** | Optional (can be in-memory only) | Always persistent |
| **Backup/Recovery** | Redis-specific tools | Standard DB backup tools |
| **Session Storage** | In-memory with optional disk | Always on disk |
| **Scalability** | Excellent (Redis Cluster) | Good (limited by DB) |
| **Connection Pooling** | Lettuce (built-in) | HikariCP (requires tuning) |
| **Failure Recovery** | Fast (in-memory) | Slower (disk I/O) |
| **Best For** | High-traffic, new deployments | Existing Oracle infrastructure |

### When to Use JDBC Instead of Redis

**Choose JDBC if:**
1. ✅ You already have Oracle database infrastructure
2. ✅ Your team is more familiar with database operations than Redis
3. ✅ Compliance requires all data in corporate database
4. ✅ You need sessions to survive complete system outages with database backups
5. ✅ Budget constraints prevent additional Redis infrastructure

**Choose Redis if:**
1. ✅ You need maximum performance and lowest latency
2. ✅ You're deploying a new system (no existing DB)
3. ✅ High traffic is expected (>1000 requests/sec)
4. ✅ You want session storage decoupled from application database
5. ✅ Your team has Redis operational expertise

### JDBC Performance Tuning

To get optimal performance with JDBC sessions:

```yaml
spring:
  datasource:
    hikari:
      # Connection pool sizing (adjust based on load)
      maximum-pool-size: 20            # Max connections
      minimum-idle: 10                 # Min idle connections
      
      # Connection timeouts
      connection-timeout: 30000        # 30 seconds
      idle-timeout: 600000             # 10 minutes
      max-lifetime: 1800000            # 30 minutes
      
      # Performance tuning
      leak-detection-threshold: 60000  # Detect connection leaks
      
  session:
    jdbc:
      # Cleanup expired sessions every 15 minutes
      cleanup-cron: "0 */15 * * * *"
      
      # Session table names (optional customization)
      table-name: SPRING_SESSION
```

**Database-Level Tuning:**
- Ensure proper indexing on `SESSION_ID`, `EXPIRY_TIME`, and `PRINCIPAL_NAME`
- Configure adequate database connection limits
- Monitor connection pool usage and adjust sizing
- Regular cleanup of expired sessions (handled by Spring Session)
- Consider database connection pooling at the database level (e.g., Oracle RAC)

### JDBC Session Security

Similar to Redis, secure your JDBC session storage:

1. **Encrypted Database Connections:**
   ```yaml
   spring:
     datasource:
       url: jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCPS)(HOST=<host>)(PORT=2484))(CONNECT_DATA=(SERVICE_NAME=<service>)))
   ```

2. **Use Secrets Management:**
   - Store database credentials in Azure Key Vault, AWS Secrets Manager, or similar
   - Never hardcode credentials

3. **Network Isolation:**
   - Keep Oracle database in private VPC/subnet
   - Use security groups/firewall rules
   - Restrict access to gateway pods only

4. **Session Timeout:**
   ```yaml
   spring:
     session:
       timeout: 30m  # Shorter timeout = better security
   ```

5. **Audit Logging:**
   - Enable Oracle audit logging for session table access
   - Monitor for unusual session patterns

## Security Considerations

### Session Data Storage

The gateway stores these items in session storage (Redis or JDBC):
- OAuth2 authentication tokens (access, refresh, ID tokens)
- User principal (email, name, sub)
- Azure AD OAuth2 client state

### Security Best Practices

#### For Redis:

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

#### For JDBC (Oracle):

1. **Encrypted Database Connections:**
   Use Oracle wallet or TCPS protocol

2. **Credential Management:**
   Store database credentials securely (vault services)

3. **Database Access Control:**
   - Dedicated database user with minimal privileges
   - Grant only necessary permissions on session tables

4. **All Session Stores:**

   **Configure Session Timeout:**
   ```yaml
   spring:
     session:
       timeout: 30m  # Balance security vs UX
   ```

   **Regular Token Rotation:**
   - Spring Security handles refresh token rotation
   - Session store ensures tokens are accessible for rotation

## Alternative Approaches

### Why Not Use Other Session Stores?

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

## Monitoring

### Monitoring Redis Sessions

**Key Metrics to Monitor:**

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

**Example Prometheus Queries:**

```promql
# Active sessions
redis_sessions_total{job="peg-gateway"}

# Session creation rate
rate(redis_sessions_created_total[5m])

# Redis memory usage
redis_memory_used_bytes
```

### Monitoring JDBC Sessions

**Key Metrics to Monitor:**

1. **Database Connection Pool:**
   - Active connections
   - Idle connections
   - Connection wait time
   - Connection acquisition time

2. **Session Table Statistics:**
   - Row count in SPRING_SESSION table
   - Query performance on session lookups
   - Session cleanup job execution time

3. **Database Health:**
   - CPU usage during session operations
   - I/O wait time
   - Table space usage

**Example SQL Queries:**

```sql
-- Count active sessions
SELECT COUNT(*) FROM SPRING_SESSION 
WHERE EXPIRY_TIME > (EXTRACT(EPOCH FROM CURRENT_TIMESTAMP) * 1000);

-- Sessions by user
SELECT PRINCIPAL_NAME, COUNT(*) 
FROM SPRING_SESSION 
GROUP BY PRINCIPAL_NAME;

-- Old sessions that need cleanup
SELECT COUNT(*) FROM SPRING_SESSION 
WHERE EXPIRY_TIME < (EXTRACT(EPOCH FROM CURRENT_TIMESTAMP) * 1000);
```

## Troubleshooting

### Redis-Specific Issues

**"Session expired" errors after deployment**
- **Cause**: Redis not configured or unreachable
- **Solution**: Verify Redis connection and configuration

**Users forced to re-login frequently**
- **Cause**: In-memory sessions with multiple pods
- **Solution**: Enable Redis session store

**Slow authentication**
- **Cause**: Redis network latency
- **Solution**: Use Redis in same region/VPC as gateway

**Redis connection failures**
- **Cause**: Network isolation, wrong credentials
- **Solution**: Check Redis host, port, password, and network rules

### JDBC-Specific Issues

**"Could not open JDBC Connection for transaction"**
- **Cause**: Database unreachable or connection pool exhausted
- **Solution**: Check database connectivity, increase pool size

**Slow session lookups**
- **Cause**: Missing indexes on session tables
- **Solution**: Verify indexes exist on SESSION_ID, EXPIRY_TIME columns

**Connection pool exhaustion**
- **Cause**: Too many concurrent sessions, small pool size
- **Solution**: Increase HikariCP maximum-pool-size

**Sessions not cleaning up**
- **Cause**: Cleanup cron job not running
- **Solution**: Check `spring.session.jdbc.cleanup-cron` configuration

**Oracle errors during session creation**
- **Cause**: Insufficient tablespace or permissions
- **Solution**: Grant proper permissions, increase tablespace

## Summary

| Deployment Type | Session Store Options | Recommendation |
|-----------------|----------------------|----------------|
| Local Development | In-Memory | ✅ In-Memory (no config needed) |
| Single Instance | In-Memory | ✅ In-Memory (no config needed) |
| Kubernetes (replicas > 1) | Redis or JDBC | ✅ Redis (performance) or JDBC (existing DB) |
| Production Load-Balanced | Redis or JDBC | ✅ Redis (recommended) or JDBC (if Oracle exists) |
| High Availability | Redis or JDBC | ✅ Redis (best performance) |
| Existing Oracle Infrastructure | JDBC | ✅ JDBC (leverage existing DB) |

### Decision Matrix

**Choose Redis if:**
- ✅ You need maximum performance (<1ms latency)
- ✅ High traffic expected (>1000 req/sec)
- ✅ New deployment without existing database constraints
- ✅ Team has Redis operational experience
- ✅ Session data can be ephemeral (doesn't need to survive all failures)

**Choose JDBC (Oracle) if:**
- ✅ Organization already has Oracle infrastructure
- ✅ DBAs prefer database-backed storage
- ✅ Compliance requires all data in corporate database
- ✅ Need sessions in backups for disaster recovery
- ✅ Lower traffic scenarios (<500 req/sec)

**Bottom Line**: Both Redis and JDBC enable distributed session management for multi-instance deployments. Redis offers better performance and is recommended for most scenarios. JDBC with Oracle is a valid choice when leveraging existing database infrastructure or when database persistence is required by policy.
