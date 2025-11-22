package com.numaansystems.peg;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Basic unit test to verify the application class is properly configured.
 */
class GatewayApplicationTests {

    @Test
    void applicationClassExists() {
        GatewayApplication app = new GatewayApplication();
        assertNotNull(app, "GatewayApplication should be instantiable");
    }
    
    @Test
    void mainMethodExists() throws NoSuchMethodException {
        // Verify that the main method exists and has the correct signature
        assertNotNull(GatewayApplication.class.getDeclaredMethod("main", String[].class));
    }
}
