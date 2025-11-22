# Test Applications

This directory contains two simple test applications to validate that the PEG Gateway is working correctly and properly authenticating users.

## Applications

### 1. Spring Boot Test App (Port 8081)
A modern Spring Boot application that demonstrates:
- Reading authentication headers from the gateway
- Displaying user information in a web interface
- Providing JSON API endpoints

**Access via Gateway:** `http://localhost:8080/gateway/api/`

### 2. Legacy Test App (Port 8082)
A legacy-style Spring application that demonstrates:
- Traditional HttpServletRequest header reading
- No OAuth2 dependencies
- Simple authentication delegation pattern

**Access via Gateway:** `http://localhost:8080/gateway/legacy/`

## Building and Running

### Build All Test Apps

```bash
# From the test-apps directory
cd test-apps

# Build Spring Boot app
cd spring-boot-app
mvn clean package
cd ..

# Build Legacy app
cd legacy-app
mvn clean package
cd ..
```

### Run Test Apps

**Option 1: Run in separate terminals**

Terminal 1:
```bash
cd test-apps/spring-boot-app
java -jar target/spring-boot-test-app-1.0.0-SNAPSHOT.jar
```

Terminal 2:
```bash
cd test-apps/legacy-app
java -jar target/legacy-test-app-1.0.0-SNAPSHOT.jar
```

**Option 2: Use the startup script**

```bash
cd test-apps
./start-test-apps.sh
```

To stop:
```bash
./stop-test-apps.sh
```

## Testing the Gateway

1. **Start the gateway** (make sure it's configured with Azure AD)
   ```bash
   cd ../
   java -jar target/peg-1.0.0-SNAPSHOT.jar
   ```

2. **Start both test applications**

3. **Access via Gateway:**
   - Modern app: http://localhost:8080/gateway/api/
   - Legacy app: http://localhost:8080/gateway/legacy/

4. **You should be:**
   - Redirected to Azure AD login
   - Authenticated by Azure AD
   - Redirected back to the gateway
   - See your user information displayed on each app

## Direct Access (Without Gateway)

You can also access the apps directly to see the difference:
- Direct to Spring Boot app: http://localhost:8081/
- Direct to Legacy app: http://localhost:8082/

When accessed directly (not via gateway), the apps will show "Not authenticated" because they won't receive the authentication headers.

## Endpoints

Each app provides:

| Endpoint | Description | Response Type |
|----------|-------------|---------------|
| `/` | Home page with user info | HTML |
| `/api/user` | User information | JSON |
| `/health` | Health check | JSON |

## What Each App Demonstrates

### Spring Boot App
- Modern Spring Boot 3.x application
- Uses `@RequestHeader` annotation to extract headers
- Thymeleaf templates for UI
- RESTful API endpoints

### Legacy App
- Simulates older Spring applications
- Uses `HttpServletRequest` directly
- No modern Spring Security integration
- Shows backward compatibility

## Authentication Flow

```
User Browser → Gateway (Azure AD Auth) → Backend Apps
                    ↓
              Adds Headers:
              - X-Auth-User-Name
              - X-Auth-User-Email
              - X-Auth-User-Sub
```

## Troubleshooting

**Apps won't start:**
- Check if ports 8081 and 8082 are available
- Ensure Java 17+ is installed
- Verify Maven build was successful

**Not seeing authentication:**
- Ensure you're accessing via gateway URLs (port 8080)
- Check that gateway is running and configured
- Verify Azure AD configuration in gateway

**Connection refused:**
- Make sure all applications are running
- Check firewall settings
- Verify network connectivity
