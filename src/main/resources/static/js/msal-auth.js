/**
 * MSAL.js Authentication Wrapper for Azure AD
 * 
 * This module provides a minimal wrapper around MSAL.js (msal-browser) to handle
 * Azure AD authentication using Authorization Code flow with PKCE.
 * 
 * Security Features:
 * - Uses Authorization Code + PKCE (no implicit flow)
 * - Tokens stored only in sessionStorage (not localStorage)
 * - ID token sent to backend to create HttpOnly session cookie
 * - Tokens not persisted client-side long-term
 * 
 * Usage:
 * 1. Include msal-browser library (CDN or npm bundle)
 * 2. Include this script
 * 3. Call MsalAuth.configure({clientId, authority}) with your Azure AD config
 * 4. Call MsalAuth.login() to initiate login
 * 5. On success, a session cookie will be created automatically
 * 
 * For GWT Integration:
 * - Include this script in your GWT host page
 * - Call MsalAuth methods via JSNI or JavaScript interop
 * 
 * @requires msal-browser (https://github.com/AzureAD/microsoft-authentication-library-for-js)
 */
(function(global) {
    'use strict';

    // Check if msal is available
    if (typeof msal === 'undefined') {
        console.warn('MSAL library not loaded. Please include msal-browser before msal-auth.js');
    }

    /**
     * MSAL Authentication Module
     */
    const MsalAuth = {
        // MSAL PublicClientApplication instance
        msalInstance: null,
        
        // Configuration
        config: {
            clientId: null,
            authority: null,
            redirectUri: null,
            postLogoutRedirectUri: null,
            sessionEndpoint: '/oauth/session/create',
            sessionDestroyEndpoint: '/oauth/session/destroy',
            sessionInfoEndpoint: '/oauth/session/info'
        },
        
        // State
        isInitialized: false,
        currentAccount: null,

        /**
         * Configure MSAL with Azure AD settings.
         * 
         * @param {Object} options Configuration options
         * @param {string} options.clientId - Azure AD Application (client) ID
         * @param {string} options.authority - Azure AD Authority URL (e.g., https://login.microsoftonline.com/{tenant-id})
         * @param {string} [options.redirectUri] - Redirect URI after authentication (default: current origin)
         * @param {string} [options.postLogoutRedirectUri] - Redirect URI after logout (default: current origin)
         * @param {string} [options.sessionEndpoint] - Backend session creation endpoint (default: /oauth/session/create)
         * @returns {Promise} Resolves when initialization is complete
         */
        configure: async function(options) {
            if (!options.clientId || !options.authority) {
                throw new Error('clientId and authority are required');
            }

            this.config.clientId = options.clientId;
            this.config.authority = options.authority;
            this.config.redirectUri = options.redirectUri || window.location.origin;
            this.config.postLogoutRedirectUri = options.postLogoutRedirectUri || window.location.origin;
            
            if (options.sessionEndpoint) {
                this.config.sessionEndpoint = options.sessionEndpoint;
            }

            const msalConfig = {
                auth: {
                    clientId: this.config.clientId,
                    authority: this.config.authority,
                    redirectUri: this.config.redirectUri,
                    postLogoutRedirectUri: this.config.postLogoutRedirectUri,
                    navigateToLoginRequestUrl: true
                },
                cache: {
                    // Use sessionStorage to avoid tokens persisting across browser sessions
                    cacheLocation: 'sessionStorage',
                    // Disable storing auth state in cookies for PKCE flow
                    storeAuthStateInCookie: false
                },
                system: {
                    loggerOptions: {
                        logLevel: msal.LogLevel.Warning,
                        loggerCallback: function(level, message, containsPii) {
                            if (!containsPii) {
                                console.log('[MSAL]', message);
                            }
                        }
                    }
                }
            };

            this.msalInstance = new msal.PublicClientApplication(msalConfig);
            
            // Handle redirect callback
            await this.handleRedirectPromise();
            
            this.isInitialized = true;
            console.log('[MsalAuth] Initialized successfully');
            
            return this;
        },

        /**
         * Handle the redirect response from Azure AD.
         * Should be called on page load for redirect flow.
         */
        handleRedirectPromise: async function() {
            try {
                const response = await this.msalInstance.handleRedirectPromise();
                if (response) {
                    console.log('[MsalAuth] Redirect response received');
                    await this._handleAuthenticationResult(response);
                } else {
                    // Check if user is already signed in
                    const accounts = this.msalInstance.getAllAccounts();
                    if (accounts.length > 0) {
                        this.currentAccount = accounts[0];
                        console.log('[MsalAuth] Existing account found:', this.currentAccount.username);
                    }
                }
            } catch (error) {
                console.error('[MsalAuth] Redirect handling error:', error);
                throw error;
            }
        },

        /**
         * Initiate login using popup window.
         * 
         * @param {Object} [options] Additional login options
         * @returns {Promise<Object>} User info on success
         */
        loginPopup: async function(options) {
            this._ensureInitialized();
            
            const loginRequest = {
                scopes: ['openid', 'profile', 'email'],
                prompt: 'select_account',
                ...options
            };

            try {
                const response = await this.msalInstance.loginPopup(loginRequest);
                return await this._handleAuthenticationResult(response);
            } catch (error) {
                console.error('[MsalAuth] Login popup error:', error);
                throw error;
            }
        },

        /**
         * Initiate login using redirect flow.
         * Page will redirect to Azure AD and back.
         * 
         * @param {Object} [options] Additional login options
         */
        loginRedirect: async function(options) {
            this._ensureInitialized();
            
            const loginRequest = {
                scopes: ['openid', 'profile', 'email'],
                prompt: 'select_account',
                ...options
            };

            try {
                await this.msalInstance.loginRedirect(loginRequest);
            } catch (error) {
                console.error('[MsalAuth] Login redirect error:', error);
                throw error;
            }
        },

        /**
         * Convenience method - defaults to popup login.
         * 
         * @param {Object} [options] Login options
         * @param {boolean} [options.useRedirect=false] - Use redirect flow instead of popup
         * @returns {Promise<Object>} User info on success (popup only)
         */
        login: async function(options) {
            options = options || {};
            
            if (options.useRedirect) {
                return this.loginRedirect(options);
            }
            return this.loginPopup(options);
        },

        /**
         * Log out the current user.
         * 
         * @param {Object} [options] Logout options
         * @param {boolean} [options.useRedirect=true] - Use redirect flow (default: true)
         */
        logout: async function(options) {
            this._ensureInitialized();
            options = options || {};
            
            // First, destroy the server-side session
            try {
                await this._destroyServerSession();
            } catch (error) {
                console.warn('[MsalAuth] Failed to destroy server session:', error);
            }

            // Clear MSAL cache
            const logoutRequest = {
                account: this.currentAccount,
                postLogoutRedirectUri: this.config.postLogoutRedirectUri
            };

            if (options.useRedirect === false) {
                await this.msalInstance.logoutPopup(logoutRequest);
            } else {
                await this.msalInstance.logoutRedirect(logoutRequest);
            }
            
            this.currentAccount = null;
        },

        /**
         * Get the current authenticated user.
         * 
         * @returns {Object|null} Current user account or null
         */
        getAccount: function() {
            this._ensureInitialized();
            
            if (this.currentAccount) {
                return this.currentAccount;
            }
            
            const accounts = this.msalInstance.getAllAccounts();
            if (accounts.length > 0) {
                this.currentAccount = accounts[0];
                return this.currentAccount;
            }
            
            return null;
        },

        /**
         * Check if user is currently authenticated.
         * 
         * @returns {boolean} True if authenticated
         */
        isAuthenticated: function() {
            return this.getAccount() !== null;
        },

        /**
         * Get session info from the server.
         * 
         * @returns {Promise<Object>} Session info
         */
        getSessionInfo: async function() {
            try {
                const response = await fetch(this.config.sessionInfoEndpoint, {
                    method: 'GET',
                    credentials: 'include'
                });
                return await response.json();
            } catch (error) {
                console.error('[MsalAuth] Failed to get session info:', error);
                throw error;
            }
        },

        /**
         * Silently acquire a token (for API calls).
         * 
         * @param {string[]} scopes - Required scopes
         * @returns {Promise<string>} Access token
         */
        getAccessToken: async function(scopes) {
            this._ensureInitialized();
            
            const account = this.getAccount();
            if (!account) {
                throw new Error('No authenticated user');
            }

            const tokenRequest = {
                scopes: scopes || ['openid', 'profile', 'email'],
                account: account
            };

            try {
                const response = await this.msalInstance.acquireTokenSilent(tokenRequest);
                return response.accessToken;
            } catch (error) {
                if (error instanceof msal.InteractionRequiredAuthError) {
                    // Silent token acquisition failed, need user interaction
                    const response = await this.msalInstance.acquireTokenPopup(tokenRequest);
                    return response.accessToken;
                }
                throw error;
            }
        },

        /**
         * Internal: Handle authentication result from MSAL.
         * Creates server-side session with the ID token.
         */
        _handleAuthenticationResult: async function(response) {
            if (!response || !response.idToken) {
                throw new Error('No ID token received');
            }

            this.currentAccount = response.account;
            console.log('[MsalAuth] Authenticated:', response.account.username);

            // Create server-side session
            const sessionResult = await this._createServerSession(response.idToken);
            
            return {
                account: response.account,
                sessionCreated: sessionResult.success,
                user: sessionResult.user
            };
        },

        /**
         * Internal: Create server-side session by sending ID token to backend.
         */
        _createServerSession: async function(idToken) {
            try {
                const response = await fetch(this.config.sessionEndpoint, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({ id_token: idToken }),
                    credentials: 'include' // Important: include cookies
                });

                const result = await response.json();
                
                if (!result.success) {
                    console.error('[MsalAuth] Server session creation failed:', result.error);
                    throw new Error(result.message || 'Session creation failed');
                }

                console.log('[MsalAuth] Server session created successfully');
                return result;
            } catch (error) {
                console.error('[MsalAuth] Failed to create server session:', error);
                throw error;
            }
        },

        /**
         * Internal: Destroy server-side session.
         */
        _destroyServerSession: async function() {
            const response = await fetch(this.config.sessionDestroyEndpoint, {
                method: 'POST',
                credentials: 'include'
            });
            return await response.json();
        },

        /**
         * Internal: Ensure MSAL is initialized.
         */
        _ensureInitialized: function() {
            if (!this.isInitialized || !this.msalInstance) {
                throw new Error('MsalAuth not initialized. Call configure() first.');
            }
        }
    };

    // Export to global scope
    global.MsalAuth = MsalAuth;

    // Also support module exports if available
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = MsalAuth;
    }

})(typeof window !== 'undefined' ? window : this);
