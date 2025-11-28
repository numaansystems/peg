# MSAL.js Integration for Azure AD Sign-In

This document describes how to integrate Azure AD authentication into the legacy Spring 3 + GWT application using MSAL.js (Microsoft Authentication Library for JavaScript).

## Overview

The integration provides:
- **Client-side**: MSAL.js wrapper (`msal-auth.js`) using Authorization Code flow with PKCE
- **Server-side**: JWT validation endpoint that creates HttpOnly session cookies
- **Security**: No tokens stored long-term in browser; server-side session management

## Architecture

```
┌─────────────────┐     ┌──────────────────────────────────────┐     ┌─────────────┐
│  Browser/GWT    │────▸│  PEG Gateway                         │────▸│  Azure AD   │
│                 │     │                                      │     │             │
│  msal-auth.js   │     │  - /oauth/session/create             │     │  - JWKS     │
│  (PKCE flow)    │     │  - JWT validation (Nimbus)           │     │  - Token    │
│                 │     │  - HttpOnly session cookie           │     │   issuer    │
└─────────────────┘     └──────────────────────────────────────┘     └─────────────┘
```

### Authentication Flow

1. User clicks "Sign In" in GWT application
2. `MsalAuth.login()` initiates Authorization Code + PKCE flow via popup/redirect
3. User authenticates with Azure AD
4. MSAL.js receives ID token (in browser memory)
5. `msal-auth.js` POSTs ID token to `/oauth/session/create`
6. Gateway validates token signature against Azure AD JWKS
7. Gateway validates issuer, audience, and expiration claims
8. Gateway creates HttpOnly Secure session cookie
9. User can now make authenticated requests to backend services

## Azure AD App Registration

### Step 1: Create App Registration

1. Go to [Azure Portal](https://portal.azure.com) → Azure Active Directory → App registrations
2. Click **New registration**
3. Fill in the details:
   - **Name**: Your application name (e.g., "PEG Gateway SPA")
   - **Supported account types**: Choose based on your requirements
     - Single tenant: "Accounts in this organizational directory only"
     - Multi-tenant: "Accounts in any organizational directory"
   - **Redirect URI**: 
     - Platform: **Single-page application (SPA)**
     - URI: `http://localhost:8080` (for development)

4. Click **Register**

### Step 2: Configure Authentication

1. Go to **Authentication** blade
2. Under **Single-page application**, add redirect URIs:
   ```
   http://localhost:8080
   https://your-production-domain.com
   ```
3. Under **Implicit grant and hybrid flows**:
   - **UNCHECK** both options (we use Authorization Code + PKCE, not implicit flow)
4. Under **Advanced settings**:
   - Enable **Allow public client flows**: No (SPAs with PKCE don't need this)

### Step 3: Configure Token Claims (Optional)

1. Go to **Token configuration** blade
2. Click **Add optional claim**
3. Token type: **ID**
4. Select claims you need:
   - `email` - User's email address
   - `preferred_username` - User's preferred username
   - `upn` - User principal name

### Step 4: Note Your Configuration Values

From the **Overview** blade, note:
- **Application (client) ID**: Your `clientId`
- **Directory (tenant) ID**: Your `tenantId`

The authority URL will be:
```
https://login.microsoftonline.com/{tenantId}
```

### Important: No Client Secret Required

Since this is a Single-Page Application (SPA) using PKCE, **no client secret is needed**. The PKCE flow provides security without requiring a secret that would be exposed in browser code.

## Configuration

### Gateway Configuration (application.yml)

The gateway uses the same Azure AD configuration:

```yaml
spring:
  cloud:
    azure:
      active-directory:
        enabled: true
        profile:
          tenant-id: ${AZURE_TENANT_ID:your-tenant-id}
        credential:
          client-id: ${AZURE_CLIENT_ID:your-client-id}
          # client-secret not required for SPA MSAL flow validation

# Optional: Configure secure cookie for HTTPS environments
msal:
  session:
    secure: ${MSAL_SESSION_SECURE:true}
```

### Client-Side Configuration

```javascript
// Configure MSAL auth
MsalAuth.configure({
    clientId: 'your-client-id',  // From Azure AD app registration
    authority: 'https://login.microsoftonline.com/your-tenant-id',
    redirectUri: window.location.origin,  // Optional, defaults to origin
    postLogoutRedirectUri: window.location.origin  // Optional
});
```

## GWT Integration

### Option 1: Include in Host Page (Recommended)

Add to your GWT host HTML file (e.g., `YourApp.html`):

```html
<!DOCTYPE html>
<html>
<head>
    <!-- MSAL Browser library -->
    <script src="https://alcdn.msauth.net/browser/2.38.0/js/msal-browser.min.js"></script>
    
    <!-- MSAL Auth wrapper -->
    <script src="/js/msal-auth.js"></script>
    
    <!-- Your GWT module -->
    <script src="yourapp/yourapp.nocache.js"></script>
</head>
<body>
    <div id="login-container">
        <button id="login-btn" onclick="handleLogin()">Sign In with Microsoft</button>
    </div>
    
    <script>
        // Initialize MSAL on page load
        document.addEventListener('DOMContentLoaded', async function() {
            try {
                await MsalAuth.configure({
                    clientId: 'your-client-id',
                    authority: 'https://login.microsoftonline.com/your-tenant-id'
                });
                
                // Check if already authenticated
                if (MsalAuth.isAuthenticated()) {
                    document.getElementById('login-btn').style.display = 'none';
                    // Initialize your GWT app
                }
            } catch (error) {
                console.error('MSAL initialization failed:', error);
            }
        });
        
        async function handleLogin() {
            try {
                const result = await MsalAuth.login();
                console.log('Login successful:', result);
                // Reload or initialize GWT app
                location.reload();
            } catch (error) {
                console.error('Login failed:', error);
                alert('Login failed. Please try again.');
            }
        }
    </script>
</body>
</html>
```

### Option 2: JSNI Integration (For existing GWT code)

Create a GWT native method to call MsalAuth:

```java
public class MsalAuthBridge {
    
    /**
     * Configure MSAL authentication.
     */
    public static native void configure(String clientId, String authority) /*-{
        $wnd.MsalAuth.configure({
            clientId: clientId,
            authority: authority
        }).then(function() {
            console.log('MSAL configured');
        }).catch(function(error) {
            console.error('MSAL configuration failed', error);
        });
    }-*/;
    
    /**
     * Initiate login flow.
     */
    public static native void login(AsyncCallback<String> callback) /*-{
        $wnd.MsalAuth.login().then(function(result) {
            callback.@com.google.gwt.user.client.rpc.AsyncCallback::onSuccess(Ljava/lang/Object;)(
                result.user ? result.user.email : ''
            );
        }).catch(function(error) {
            callback.@com.google.gwt.user.client.rpc.AsyncCallback::onFailure(Ljava/lang/Throwable;)(
                @java.lang.Exception::new(Ljava/lang/String;)(error.message)
            );
        });
    }-*/;
    
    /**
     * Check if user is authenticated.
     */
    public static native boolean isAuthenticated() /*-{
        return $wnd.MsalAuth && $wnd.MsalAuth.isAuthenticated();
    }-*/;
    
    /**
     * Log out user.
     */
    public static native void logout() /*-{
        $wnd.MsalAuth.logout();
    }-*/;
}
```

### Option 3: GWT Elemental2 Interop (For GWT 2.8+)

```java
@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "MsalAuth")
public class MsalAuth {
    
    public static native Promise<MsalAuth> configure(MsalConfig config);
    
    public static native Promise<LoginResult> login();
    
    public static native Promise<Void> logout();
    
    public static native boolean isAuthenticated();
    
    public static native Account getAccount();
}

@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Object")
public class MsalConfig {
    public String clientId;
    public String authority;
    public String redirectUri;
}
```

## API Reference

### MsalAuth Methods

| Method | Description | Returns |
|--------|-------------|---------|
| `configure(options)` | Initialize MSAL with Azure AD config | `Promise<MsalAuth>` |
| `login(options?)` | Start login flow (popup by default) | `Promise<LoginResult>` |
| `loginPopup(options?)` | Login using popup window | `Promise<LoginResult>` |
| `loginRedirect(options?)` | Login using redirect flow | `Promise<void>` |
| `logout(options?)` | Log out user | `Promise<void>` |
| `isAuthenticated()` | Check if user is logged in | `boolean` |
| `getAccount()` | Get current user account | `Account \| null` |
| `getSessionInfo()` | Get server session info | `Promise<SessionInfo>` |
| `getAccessToken(scopes)` | Get access token for API calls | `Promise<string>` |

### Server Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/oauth/session/create` | POST | Create session from ID token |
| `/oauth/session/destroy` | POST | Destroy current session |
| `/oauth/session/info` | GET | Get current session info |

## Security Considerations

### Token Handling

- **Client-side**: Tokens stored in `sessionStorage` (cleared when browser closes)
- **Server-side**: ID token validated and discarded; session cookie used for subsequent requests
- **Transport**: All token transmission over HTTPS

### Session Cookie

The server creates an HttpOnly, Secure session cookie:
- `HttpOnly`: Not accessible via JavaScript (XSS protection)
- `Secure`: Only transmitted over HTTPS
- `SameSite=Lax`: CSRF protection
- Default duration: 8 hours

### PKCE Flow

This integration uses Authorization Code flow with PKCE (Proof Key for Code Exchange):
- No client secret exposed in browser
- Code verifier/challenge prevents authorization code interception
- More secure than implicit flow

## Troubleshooting

### Common Issues

**"AADSTS50011: Reply URL does not match"**
- Verify redirect URI in Azure AD matches exactly (including trailing slashes)
- Check for http vs https mismatch

**"Token validation failed: Invalid issuer"**
- Verify tenant ID is correct in both client and server configuration
- Check if using v1 vs v2 endpoints

**"Session creation failed"**
- Check server logs for detailed error
- Verify JWKS endpoint is accessible from server
- Check clock synchronization between client/server

**Login popup blocked**
- Ensure popup is triggered by user action (click event)
- Use redirect flow as fallback: `MsalAuth.login({ useRedirect: true })`

### Debug Mode

Enable debug logging:

```javascript
// In browser console
localStorage.setItem('msal.log.level', 'Verbose');
```

Server-side logging (application.yml):
```yaml
logging:
  level:
    com.numaansystems.peg.auth: DEBUG
```

## Production Checklist

- [ ] Register production redirect URIs in Azure AD
- [ ] Enable `msal.session.secure: true` for HTTPS
- [ ] Configure proper CORS origins in gateway
- [ ] Use environment variables for Azure AD configuration
- [ ] Enable Redis/JDBC session store for multi-instance deployments
- [ ] Review and restrict API permissions in Azure AD
- [ ] Enable Azure AD Conditional Access policies as needed
- [ ] Configure session timeout appropriate for security requirements
