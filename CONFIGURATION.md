# PEG Gateway Configuration Guide

This guide provides detailed information on configuring the PEG Gateway for your environment.

## Azure AD Configuration

### Step 1: Register Application in Azure AD

1. Sign in to the [Azure Portal](https://portal.azure.com)
2. Navigate to **Azure Active Directory** > **App registrations**
3. Click **New registration**
4. Fill in the application details:
   - Name: `PEG Gateway`
   - Supported account types: Choose appropriate option
   - Redirect URI: `https://your-gateway-domain.com/login/oauth2/code/azure`
5. Click **Register**

### Step 2: Configure Application

1. Note the **Application (client) ID** - this is your `AZURE_CLIENT_ID`
2. Note the **Directory (tenant) ID** - this is your `AZURE_TENANT_ID`
3. Navigate to **Certificates & secrets**
4. Click **New client secret**
5. Add a description and expiration period
6. Click **Add** and copy the secret value - this is your `AZURE_CLIENT_SECRET`

### Step 3: Configure API Permissions

1. Navigate to **API permissions**
2. Add the following permissions:
   - Microsoft Graph > Delegated permissions:
     - `openid`
     - `profile`
     - `email`
     - `User.Read`
3. Click **Grant admin consent** (if you have admin rights)

### Step 4: Configure Authentication

1. Navigate to **Authentication**
2. Under **Platform configurations**, add redirect URIs:
   - `https://your-gateway-domain.com/gateway/login/oauth2/code/azure`
   - `http://localhost:8080/gateway/login/oauth2/code/azure` (for local development)
3. Under **Implicit grant and hybrid flows**, enable:
   - ID tokens (used for implicit and hybrid flows)
4. Click **Save**

## Gateway Configuration

### Environment Variables

Set the following environment variables:

```bash
# Azure AD
export AZURE_TENANT_ID=your-tenant-id
export AZURE_CLIENT_ID=your-client-id
export AZURE_CLIENT_SECRET=your-client-secret

# Server
export SERVER_PORT=8080

# Backend Services
export BACKEND_SPRING_BOOT_URL=http://your-spring-boot-app:8081
export BACKEND_LEGACY_SPRING_URL=http://your-legacy-app:8082
export BACKEND_GWT_URL=http://your-gwt-app:8083
```

### Application Configuration

The main configuration is in `application.yml`. Key sections:

#### Gateway Routes

Routes define how requests are proxied to backend services:

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: my-service
          uri: http://backend-service:8080
          predicates:
            - Path=/my-service/**
          filters:
            - StripPrefix=1
            - AuthenticationHeaderFilter
```

**Route Components:**
- `id`: Unique identifier for the route
- `uri`: Backend service URL
- `predicates`: Conditions for matching requests
- `filters`: Transformations applied to requests/responses

#### Security Configuration

Configure OAuth2 client settings:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          azure:
            client-id: ${AZURE_CLIENT_ID}
            client-secret: ${AZURE_CLIENT_SECRET}
            scope: openid, profile, email
```

### Backend Service Configuration

Backend services receive authentication information via headers:

| Header | Description |
|--------|-------------|
| `X-Auth-User-Name` | User's display name |
| `X-Auth-User-Email` | User's email address |
| `X-Auth-User-Sub` | Unique user identifier |
| `X-Auth-ID-Token` | (Optional) OIDC ID token |

**Example: Reading headers in Spring Boot backend**

```java
@RestController
public class UserController {
    
    @GetMapping("/api/user")
    public UserInfo getCurrentUser(
            @RequestHeader("X-Auth-User-Name") String userName,
            @RequestHeader("X-Auth-User-Email") String userEmail,
            @RequestHeader("X-Auth-User-Sub") String userSub) {
        
        return new UserInfo(userName, userEmail, userSub);
    }
}
```

**Example: Reading headers in Legacy Spring**

```java
@Controller
public class UserController {
    
    @RequestMapping("/user")
    public ModelAndView getCurrentUser(HttpServletRequest request) {
        String userName = request.getHeader("X-Auth-User-Name");
        String userEmail = request.getHeader("X-Auth-User-Email");
        String userSub = request.getHeader("X-Auth-User-Sub");
        
        ModelAndView mav = new ModelAndView("user");
        mav.addObject("userName", userName);
        mav.addObject("userEmail", userEmail);
        return mav;
    }
}
```

## Session Management

### Understanding Session Storage

The PEG Gateway uses **stateful OAuth2 authentication** with Azure AD. This means it must maintain session data containing:
- OAuth2 tokens (access, refresh, ID tokens)
- User authentication state
- User claims (email, name, subject)

### In-Memory Sessions (Default)

By default, sessions are stored in the gateway's memory. This is suitable for:
- **Single-instance deployments** (only one gateway pod running)
- **Development environments** (testing on localhost)
- **Testing scenarios**

**When in-memory sessions work:**
```
Client → Single Gateway Instance → Backend Services
         [Sessions in RAM]
```
All client requests hit the same gateway instance, so sessions are always available.

### Redis Sessions (Required for Production)

For production deployments with **multiple gateway instances**, Redis is **required** for distributed session management.

**Why Redis is necessary:**

In multi-instance deployments (Kubernetes with replicas > 1, load-balanced setups), client requests can hit any gateway instance:

```
                    ┌─> Gateway Pod 1 ─┐
Client → Load Bal ─→├─> Gateway Pod 2 ─┤─> Backend Services
                    └─> Gateway Pod 3 ─┘
                           ↓
                     Redis (Shared Sessions)
```

**Without Redis:**
- Request 1 hits Pod 1: User authenticates, session stored in Pod 1's memory
- Request 2 hits Pod 2: Session not found, user forced to re-authenticate
- **Result**: Constant re-authentication, poor user experience

**With Redis:**
- Request 1 hits Pod 1: User authenticates, session stored in Redis
- Request 2 hits Pod 2: Session retrieved from Redis, user stays authenticated
- **Result**: Seamless experience across all gateway instances

**For a detailed explanation, see [ARCHITECTURE.md](ARCHITECTURE.md#session-management-architecture)**

### Configuring Redis Sessions

1. Uncomment Redis dependencies in `pom.xml`
2. Configure Redis connection:

```yaml
spring:
  redis:
    host: redis-host
    port: 6379
    password: ${REDIS_PASSWORD}
  session:
    store-type: redis
    timeout: 30m
```

3. Deploy Redis or use a managed service (Azure Cache for Redis, AWS ElastiCache)

## CORS Configuration

Configure CORS for your frontend applications:

```yaml
spring:
  cloud:
    gateway:
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOrigins: 
              - "https://your-frontend.com"
            allowedMethods:
              - GET
              - POST
              - PUT
              - DELETE
            allowedHeaders: "*"
            allowCredentials: true
```

## SSL/TLS Configuration

For production, configure SSL:

```yaml
server:
  port: 8443
  ssl:
    enabled: true
    key-store: classpath:keystore.p12
    key-store-password: ${KEYSTORE_PASSWORD}
    key-store-type: PKCS12
    key-alias: gateway
```

## Logging Configuration

Adjust logging levels:

```yaml
logging:
  level:
    root: INFO
    com.numaansystems.peg: DEBUG
    org.springframework.cloud.gateway: DEBUG
    org.springframework.security: DEBUG
```

## Health Checks

Health endpoints are available at:
- `/actuator/health` - Overall health status
- `/actuator/info` - Application information
- `/actuator/metrics` - Application metrics

Configure health checks:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: when-authorized
```

## Troubleshooting

### Common Issues

**Issue: "Unable to resolve Configuration with the provided Issuer"**
- Verify `AZURE_TENANT_ID` is correct
- Check network connectivity to Azure AD
- Ensure the issuer URL is accessible

**Issue: "Invalid redirect URI"**
- Verify redirect URI in Azure AD matches your configuration
- Check that the URI includes the protocol (http/https)
- Ensure trailing slashes match

**Issue: "Backend service not reachable"**
- Verify backend service URLs are correct
- Check network connectivity
- Ensure backend services are running
- Review gateway logs for routing errors

**Issue: "CORS errors in browser"**
- Configure CORS in `application.yml`
- Ensure `allowedOrigins` includes your frontend URL
- Set `allowCredentials: true` if using cookies

## Performance Tuning

### JVM Options

```bash
java -Xmx1g -Xms512m \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -jar peg-1.0.0-SNAPSHOT.jar
```

### Connection Pool Tuning

Configure connection pools for better performance:

```yaml
spring:
  cloud:
    gateway:
      httpclient:
        pool:
          max-connections: 500
          max-pending-acquires: 1000
```

## Security Best Practices

1. **Always use HTTPS in production**
2. **Store secrets securely** (Azure Key Vault, AWS Secrets Manager)
3. **Enable rate limiting** for API routes
4. **Implement request validation**
5. **Regular security audits**
6. **Keep dependencies updated**
7. **Enable security headers** (HSTS, CSP, X-Frame-Options)
8. **Monitor and log security events**

## Deployment

### Docker Deployment

```bash
# Build the application
mvn clean package

# Build Docker image
docker build -t peg-gateway:latest .

# Run with Docker Compose
docker-compose up -d
```

### Kubernetes Deployment

Example Kubernetes deployment:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: peg-gateway
spec:
  replicas: 3
  selector:
    matchLabels:
      app: peg-gateway
  template:
    metadata:
      labels:
        app: peg-gateway
    spec:
      containers:
      - name: gateway
        image: peg-gateway:latest
        ports:
        - containerPort: 8080
        env:
        - name: AZURE_TENANT_ID
          valueFrom:
            secretKeyRef:
              name: azure-credentials
              key: tenant-id
        - name: AZURE_CLIENT_ID
          valueFrom:
            secretKeyRef:
              name: azure-credentials
              key: client-id
        - name: AZURE_CLIENT_SECRET
          valueFrom:
            secretKeyRef:
              name: azure-credentials
              key: client-secret
```

## Monitoring

Integrate with monitoring solutions:

- **Prometheus**: Expose metrics endpoint
- **Grafana**: Create dashboards for visualization
- **ELK Stack**: Centralized logging
- **Azure Application Insights**: Azure-native monitoring

Example Prometheus configuration:

```yaml
management:
  metrics:
    export:
      prometheus:
        enabled: true
  endpoints:
    web:
      exposure:
        include: prometheus
```
