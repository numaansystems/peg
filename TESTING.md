# Testing Guide - PEG Gateway

This guide walks you through testing the PEG Gateway with the included test applications.

## Quick Start

### 1. Build Everything

```bash
# Build the gateway
mvn clean package

# Build test applications
cd test-apps
cd spring-boot-app && mvn clean package && cd ..
cd legacy-app && mvn clean package && cd ..
cd ..
```

### 2. Start the Test Applications

```bash
cd test-apps
./start-test-apps.sh
```

This starts:
- Spring Boot Test App on port 8081
- Legacy Test App on port 8082

### 3. Configure Azure AD (If Not Already Done)

Create a `.env` file from the template:

```bash
cp .env.example .env
```

Edit `.env` with your Azure AD credentials:
```
AZURE_TENANT_ID=your-tenant-id
AZURE_CLIENT_ID=your-client-id
AZURE_CLIENT_SECRET=your-client-secret
```

### 4. Start the Gateway

```bash
java -jar target/peg-1.0.0-SNAPSHOT.jar
```

The gateway will start on port 8080.

## Testing Scenarios

### Scenario 1: Access via Gateway (Authenticated)

**Test Modern Spring Boot App:**
1. Open browser: http://localhost:8080/gateway/api/
2. You'll be redirected to Azure AD login
3. Login with your Azure AD credentials
4. You'll be redirected back and see:
   - ✓ Authenticated status
   - Your name from Azure AD
   - Your email
   - Your unique user ID

**Test Legacy Spring App:**
1. Open browser: http://localhost:8080/gateway/legacy/
2. Since you're already authenticated, you'll see:
   - ✓ Authenticated status
   - Your name, email, and ID
   - Different styling (legacy theme)

### Scenario 2: Direct Access (Not Authenticated)

**Test Direct Access to Spring Boot App:**
1. Open browser: http://localhost:8081/
2. You'll see:
   - ✗ Not Authenticated
   - No user information
   - This shows the app needs the gateway for authentication

**Test Direct Access to Legacy App:**
1. Open browser: http://localhost:8082/
2. You'll see:
   - ✗ Not Authenticated
   - No user information

### Scenario 3: API Endpoints

**Via Gateway (Authenticated):**
```bash
# Get user info from Spring Boot app (JSON)
curl http://localhost:8080/gateway/api/api/user \
  -H "Cookie: SESSION=your-session-cookie"

# Get user info from Legacy app (JSON)
curl http://localhost:8080/gateway/legacy/api/user \
  -H "Cookie: SESSION=your-session-cookie"
```

**Direct Access (No Authentication):**
```bash
# Direct to Spring Boot app
curl http://localhost:8081/api/user

# Direct to Legacy app
curl http://localhost:8082/api/user
```

Both will return "Not authenticated" in the response.

## What To Look For

### Visual Indicators

**Authenticated via Gateway:**
- Green ✓ checkmark next to "Authenticated via Gateway"
- User name displayed (from Azure AD)
- User email displayed
- Unique user ID displayed

**Not Authenticated (Direct Access):**
- Red ✗ next to "Not Authenticated"
- "Not authenticated" or "N/A" for all user fields

### Authentication Headers

The gateway adds these headers to all proxied requests:

```
X-Auth-User-Name: John Doe
X-Auth-User-Email: john.doe@example.com
X-Auth-User-Sub: 00000000-0000-0000-0000-000000000000
```

The test applications read these headers and display them.

## Verification Checklist

- [ ] Gateway starts successfully on port 8080
- [ ] Spring Boot app starts on port 8081
- [ ] Legacy app starts on port 8082
- [ ] Accessing gateway redirects to Azure AD login
- [ ] After login, user info is displayed correctly
- [ ] Both test apps show authenticated status via gateway
- [ ] Direct access to apps shows "Not authenticated"
- [ ] API endpoints return user info when accessed via gateway
- [ ] API endpoints return "Not authenticated" when accessed directly
- [ ] Can navigate between /api/ and /legacy/ routes without re-authentication

## Architecture Flow

```
┌─────────────┐
│   Browser   │
└──────┬──────┘
       │ 1. Request http://localhost:8080/gateway/api/
       ↓
┌─────────────────────────┐
│   PEG Gateway :8080     │
│                         │
│  - Not authenticated?   │
│  - Redirect to Azure AD │
└──────┬──────────────────┘
       │ 2. Redirect to Azure AD
       ↓
┌─────────────────────────┐
│      Azure AD           │
│   (login.microsoft...)  │
└──────┬──────────────────┘
       │ 3. User logs in
       ↓
┌─────────────────────────┐
│   PEG Gateway :8080     │
│                         │
│  - Create session       │
│  - Add auth headers     │
│  - Proxy to backend     │
└──────┬──────────────────┘
       │ 4. Proxy with headers:
       │    X-Auth-User-Name: John Doe
       │    X-Auth-User-Email: john@example.com
       │    X-Auth-User-Sub: xxx-xxx-xxx
       ↓
┌─────────────────────────┐
│  Spring Boot App :8081  │
│                         │
│  - Read headers         │
│  - Display user info    │
└─────────────────────────┘
```

## Troubleshooting

### Gateway won't start
- Check if port 8080 is available
- Verify Azure AD configuration in .env
- Check logs for errors

### Test apps won't start
- Check if ports 8081 and 8082 are available
- Verify Maven build was successful
- Check logs in test-apps directory

### Not redirected to Azure AD
- Verify Azure AD configuration
- Check redirect URI in Azure AD matches gateway URL
- Clear browser cookies and try again

### Authentication succeeds but no user info shown
- Check browser developer console for errors
- Verify AuthenticationHeaderFilter is enabled
- Check gateway logs for routing errors
- Ensure backend apps are running

### "Not authenticated" shown when accessing via gateway
- Clear browser cookies
- Check Azure AD token hasn't expired
- Verify session management is working
- Check gateway logs

## Logs

**Gateway logs:**
- Console output shows routing and authentication
- Look for "Mapped [GET] /api/**" and similar route mappings

**Test app logs:**
- `test-apps/spring-boot-app/spring-boot-app.log`
- `test-apps/legacy-app/legacy-app.log`

## Cleanup

Stop all applications:

```bash
# Stop test apps
cd test-apps
./stop-test-apps.sh

# Stop gateway (Ctrl+C in the terminal where it's running)
```

## Next Steps

After validating the gateway works:

1. Replace test applications with your actual backend services
2. Update routes in `application.yml`
3. Configure production Azure AD settings
4. Set up SSL/TLS certificates
5. Deploy to production environment
6. Configure session management with Redis for scalability
7. Set up monitoring and logging

## Advanced Testing

### Test with Multiple Users
1. Open an incognito window
2. Login with a different Azure AD account
3. Verify different user information is displayed

### Test Session Persistence
1. Login via gateway
2. Close browser (but not the gateway)
3. Open browser again
4. Access gateway URL
5. Should still be authenticated (session persists)

### Test Logout
1. Navigate to http://localhost:8080/gateway/logout
2. Should logout from Azure AD
3. Next access should require re-authentication

### Performance Testing
```bash
# Install Apache Bench if not already installed
# Test gateway performance
ab -n 1000 -c 10 http://localhost:8080/gateway/api/health
```

## Security Testing

### Verify Headers Are Added
Use browser developer tools:
1. Open DevTools (F12)
2. Go to Network tab
3. Access http://localhost:8080/gateway/api/
4. Click on the request to backend
5. Verify X-Auth-* headers are present

### Verify Direct Access Is Not Authenticated
1. Access http://localhost:8081/ directly
2. Should show "Not authenticated"
3. This proves backend relies on gateway for auth

## Success Criteria

✅ Gateway successfully integrates with Azure AD
✅ User can login with Azure credentials
✅ User information is correctly propagated to backend apps
✅ Both Spring Boot and Legacy apps receive authentication
✅ Direct access to backends shows no authentication
✅ Session persists across requests
✅ Can access multiple backend apps with single login
✅ Logout works correctly

---

**Congratulations!** If all tests pass, your PEG Gateway is working correctly and ready for integration with your actual backend applications.
