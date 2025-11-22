# PEG - Proxy Gateway

A reverse proxy gateway application using Spring Cloud Gateway that provides centralized authentication for various applications through Azure AD integration.

## Features

- **Centralized Authentication**: Single sign-on (SSO) using Azure AD OAuth2/OIDC
- **Reverse Proxy**: Routes requests to multiple backend applications
- **Multi-Application Support**: Compatible with:
  - Modern Spring Boot applications
  - Legacy Spring Framework web applications
  - GWT applications
- **Authentication Propagation**: Forwards user identity to backend services via custom headers
- **Session Management**: Maintains user sessions across requests
- **Health Monitoring**: Actuator endpoints for monitoring and health checks

## Architecture

The gateway acts as a single entry point for all client applications:

```
Client → Gateway (Azure AD Auth) → Backend Services
```

The gateway:
1. Authenticates users via Azure AD
2. Maintains user sessions
3. Routes requests to appropriate backend services
4. Adds authentication headers for backend consumption

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- Azure AD tenant with application registration

## Configuration

### Azure AD Setup

1. Register an application in Azure AD
2. Configure redirect URI: `http://localhost:8080/login/oauth2/code/azure`
3. Note the following values:
   - Tenant ID
   - Client ID
   - Client Secret

### Environment Variables

Set the following environment variables:

```bash
export AZURE_TENANT_ID=your-tenant-id
export AZURE_CLIENT_ID=your-client-id
export AZURE_CLIENT_SECRET=your-client-secret
export SERVER_PORT=8080
```

### Backend Service URLs

Configure backend service URLs via environment variables:

```bash
export BACKEND_SPRING_BOOT_URL=http://localhost:8081
export BACKEND_LEGACY_SPRING_URL=http://localhost:8082
export BACKEND_GWT_URL=http://localhost:8083
```

## Building the Application

```bash
mvn clean package
```

## Running the Application

### Production Mode

```bash
java -jar target/peg-1.0.0-SNAPSHOT.jar
```

### Development Mode

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## API Routes

The gateway provides the following routes:

| Path | Backend | Description |
|------|---------|-------------|
| `/api/**` | Spring Boot App | Modern Spring Boot application |
| `/legacy/**` | Legacy Spring App | Legacy Spring Framework application |
| `/gwt/**` | GWT App | GWT application |

All routes require authentication via Azure AD.

## Authentication Headers

The gateway adds the following headers to proxied requests:

- `X-Auth-User-Name`: User's display name
- `X-Auth-User-Email`: User's email address
- `X-Auth-User-Sub`: User's subject identifier (unique ID)
- `X-Auth-ID-Token`: (Optional) OIDC ID token for backend validation

Backend applications can use these headers to identify authenticated users without implementing OAuth2 themselves.

## Health Checks

Access health endpoints:

- Health: `http://localhost:8080/actuator/health`
- Info: `http://localhost:8080/actuator/info`

## Testing

Run tests:

```bash
mvn test
```

### Test Applications

The repository includes two test applications to validate the gateway functionality:

1. **Spring Boot Test App** (Port 8081)
   - Modern Spring Boot application with web UI
   - Displays authentication information received from gateway
   - Access via gateway: `http://localhost:8080/api/`

2. **Legacy Test App** (Port 8082)
   - Legacy-style Spring application
   - Demonstrates HttpServletRequest header reading
   - Access via gateway: `http://localhost:8080/legacy/`

To run the test applications:

```bash
cd test-apps

# Build both apps
cd spring-boot-app && mvn clean package && cd ..
cd legacy-app && mvn clean package && cd ..

# Start both apps
./start-test-apps.sh

# Stop both apps
./stop-test-apps.sh
```

See [test-apps/README.md](test-apps/README.md) for detailed instructions.

## Security Considerations

- CSRF is disabled for proxy scenarios (consider enabling for production)
- All routes require authentication except health check endpoints
- Session cookies are used to maintain authentication state
- Consider using Redis for distributed session management in production

## Customization

### Adding New Routes

Edit `application.yml` to add new backend services:

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: new-service
          uri: http://localhost:8084
          predicates:
            - Path=/new-service/**
          filters:
            - StripPrefix=1
            - AuthenticationHeaderFilter
```

### Session Management

For production deployments with multiple gateway instances, enable Redis session management:

1. Uncomment Redis dependencies in `pom.xml`
2. Configure Redis connection in `application.yml`:

```yaml
spring:
  redis:
    host: localhost
    port: 6379
  session:
    store-type: redis
```

## Troubleshooting

### Authentication Issues

- Verify Azure AD configuration (tenant ID, client ID, client secret)
- Check redirect URI matches Azure AD application registration
- Review logs for OAuth2 errors

### Backend Connection Issues

- Verify backend service URLs are correct and accessible
- Check network connectivity to backend services
- Review gateway logs for routing errors

## License

Copyright © 2024 Numaan Systems
