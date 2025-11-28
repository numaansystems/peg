package com.numaansystems.peg.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MsalSessionRouterConfig.
 */
@ExtendWith(MockitoExtension.class)
class MsalSessionRouterConfigTest {

    @Mock
    private SessionCreateHandler sessionHandler;

    @Test
    void shouldCreateRouterFunction() {
        MsalSessionRouterConfig config = new MsalSessionRouterConfig(sessionHandler);
        
        RouterFunction<ServerResponse> routes = config.msalSessionRoutes();
        
        assertNotNull(routes);
    }
}
