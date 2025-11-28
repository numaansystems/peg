/**
 * OAuth2 Authentication Helper for GWT Applications
 * 
 * This module provides a simple interface for initiating OAuth2 login
 * via a popup window and receiving the authentication result.
 * 
 * Usage:
 * 
 *   // Basic usage
 *   PegAuth.login(function(success) {
 *       if (success) {
 *           console.log('User logged in successfully');
 *           // Reload page or update UI
 *           location.reload();
 *       } else {
 *           console.log('Login failed or was cancelled');
 *       }
 *   });
 * 
 *   // With options
 *   PegAuth.login({
 *       onSuccess: function() { location.reload(); },
 *       onCancel: function() { console.log('Cancelled'); },
 *       onError: function(error) { alert('Error: ' + error); }
 *   });
 * 
 *   // Check if user is authenticated
 *   PegAuth.isAuthenticated(function(authenticated) {
 *       console.log('User authenticated:', authenticated);
 *   });
 * 
 *   // Logout
 *   PegAuth.logout();
 *   PegAuth.logout('/custom-redirect');
 *   PegAuth.logout({ azureLogout: true });
 */
(function(global) {
    'use strict';
    
    var PegAuth = {};
    
    // Configuration
    var config = {
        loginPath: '/oauth/login',
        logoutPath: '/oauth/logout',
        statusPath: '/oauth/status',
        popupWidth: 500,
        popupHeight: 600,
        popupName: 'peg_auth_popup'
    };
    
    // Track the current popup window
    var currentPopup = null;
    var messageHandler = null;
    
    /**
     * Opens a login popup window and handles the OAuth flow.
     * 
     * @param {Function|Object} callbackOrOptions - Callback function or options object
     * @returns {Window} The popup window object
     */
    PegAuth.login = function(callbackOrOptions) {
        var options = normalizeOptions(callbackOrOptions);
        
        // Close any existing popup
        if (currentPopup && !currentPopup.closed) {
            currentPopup.close();
        }
        
        // Remove any existing message handler
        if (messageHandler) {
            window.removeEventListener('message', messageHandler);
        }
        
        // Calculate popup position (centered)
        var left = (window.screen.width - config.popupWidth) / 2;
        var top = (window.screen.height - config.popupHeight) / 2;
        
        // Build the login URL
        var loginUrl = config.loginPath + '?popup=true';
        if (options.originalUrl) {
            loginUrl += '&orig=' + encodeURIComponent(options.originalUrl);
        }
        
        // Open the popup
        var popupFeatures = [
            'width=' + config.popupWidth,
            'height=' + config.popupHeight,
            'left=' + left,
            'top=' + top,
            'menubar=no',
            'toolbar=no',
            'location=no',
            'status=no',
            'resizable=yes',
            'scrollbars=yes'
        ].join(',');
        
        currentPopup = window.open(loginUrl, config.popupName, popupFeatures);
        
        if (!currentPopup) {
            // Popup was blocked
            if (options.onError) {
                options.onError('Popup blocked. Please allow popups for this site.');
            }
            return null;
        }
        
        // Set up message listener for popup communication
        messageHandler = function(event) {
            // Validate the message
            if (!event.data || event.data.type !== 'oauth_login_success') {
                return;
            }
            
            // Clean up
            window.removeEventListener('message', messageHandler);
            messageHandler = null;
            
            if (event.data.success) {
                if (options.onSuccess) {
                    options.onSuccess();
                }
            } else {
                if (options.onError) {
                    options.onError(event.data.error || 'Login failed');
                }
            }
        };
        
        window.addEventListener('message', messageHandler);
        
        // Also check for popup close without message
        var checkClosed = setInterval(function() {
            if (currentPopup && currentPopup.closed) {
                clearInterval(checkClosed);
                
                // If we still have a message handler, popup was closed without completing
                if (messageHandler) {
                    window.removeEventListener('message', messageHandler);
                    messageHandler = null;
                    
                    if (options.onCancel) {
                        options.onCancel();
                    }
                }
            }
        }, 500);
        
        // Timeout after 5 minutes
        setTimeout(function() {
            clearInterval(checkClosed);
            if (messageHandler) {
                window.removeEventListener('message', messageHandler);
                messageHandler = null;
                
                if (currentPopup && !currentPopup.closed) {
                    currentPopup.close();
                }
                
                if (options.onError) {
                    options.onError('Login timed out');
                }
            }
        }, 5 * 60 * 1000);
        
        return currentPopup;
    };
    
    /**
     * Logs the user out.
     * 
     * @param {string|Object} redirectOrOptions - Redirect URL or options object
     */
    PegAuth.logout = function(redirectOrOptions) {
        var options = {};
        
        if (typeof redirectOrOptions === 'string') {
            options.redirect = redirectOrOptions;
        } else if (redirectOrOptions) {
            options = redirectOrOptions;
        }
        
        var logoutUrl = config.logoutPath;
        var params = [];
        
        if (options.redirect) {
            params.push('redirect=' + encodeURIComponent(options.redirect));
        }
        
        if (options.azureLogout) {
            params.push('azure_logout=true');
        }
        
        if (params.length > 0) {
            logoutUrl += '?' + params.join('&');
        }
        
        window.location.href = logoutUrl;
    };
    
    /**
     * Checks if the user is currently authenticated.
     * This makes an AJAX request to check session status.
     * 
     * @param {Function} callback - Callback function(isAuthenticated, userInfo)
     */
    PegAuth.isAuthenticated = function(callback) {
        var xhr = new XMLHttpRequest();
        xhr.open('GET', config.statusPath, true);
        xhr.setRequestHeader('Accept', 'application/json');
        
        xhr.onreadystatechange = function() {
            if (xhr.readyState === 4) {
                if (xhr.status === 200) {
                    try {
                        var response = JSON.parse(xhr.responseText);
                        callback(response.authenticated, response.user);
                    } catch (e) {
                        callback(false, null);
                    }
                } else {
                    callback(false, null);
                }
            }
        };
        
        xhr.send();
    };
    
    /**
     * Configure the auth helper.
     * 
     * @param {Object} options - Configuration options
     */
    PegAuth.configure = function(options) {
        if (options.loginPath) config.loginPath = options.loginPath;
        if (options.logoutPath) config.logoutPath = options.logoutPath;
        if (options.statusPath) config.statusPath = options.statusPath;
        if (options.popupWidth) config.popupWidth = options.popupWidth;
        if (options.popupHeight) config.popupHeight = options.popupHeight;
    };
    
    /**
     * Normalizes callback/options argument.
     */
    function normalizeOptions(callbackOrOptions) {
        if (typeof callbackOrOptions === 'function') {
            return {
                onSuccess: function() { callbackOrOptions(true); },
                onCancel: function() { callbackOrOptions(false); },
                onError: function() { callbackOrOptions(false); }
            };
        }
        return callbackOrOptions || {};
    }
    
    // Export to global scope
    global.PegAuth = PegAuth;
    
})(typeof window !== 'undefined' ? window : this);
