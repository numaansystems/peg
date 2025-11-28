package com.numaansystems.testapp;

import com.numaansystems.testapp.security.GatewayUserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class UserController {

    @GetMapping("/")
    public String home(
            @RequestHeader(value = "X-Auth-User-Name", required = false) String userName,
            @RequestHeader(value = "X-Auth-User-Email", required = false) String userEmail,
            @RequestHeader(value = "X-Auth-User-Sub", required = false) String userSub,
            Model model) {
        
        model.addAttribute("userName", userName != null ? userName : "Not authenticated");
        model.addAttribute("userEmail", userEmail != null ? userEmail : "N/A");
        model.addAttribute("userSub", userSub != null ? userSub : "N/A");
        model.addAttribute("appName", "Modern Spring Boot Application");
        model.addAttribute("appPort", "8081");
        
        // Add user authorities from security context
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            List<String> authorities = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList());
            model.addAttribute("authorities", authorities);
        }
        
        return "index";
    }

    @GetMapping("/api/user")
    @ResponseBody
    public Map<String, Object> getUserInfo(
            @RequestHeader(value = "X-Auth-User-Name", required = false) String userName,
            @RequestHeader(value = "X-Auth-User-Email", required = false) String userEmail,
            @RequestHeader(value = "X-Auth-User-Sub", required = false) String userSub) {
        
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("name", userName != null ? userName : "Not authenticated");
        userInfo.put("email", userEmail != null ? userEmail : "N/A");
        userInfo.put("sub", userSub != null ? userSub : "N/A");
        userInfo.put("app", "Modern Spring Boot Application");
        userInfo.put("port", "8081");
        
        // Include authorities from database
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            List<String> authorities = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList());
            userInfo.put("authorities", authorities);
            
            if (auth.getPrincipal() instanceof GatewayUserPrincipal) {
                GatewayUserPrincipal principal = (GatewayUserPrincipal) auth.getPrincipal();
                userInfo.put("principal", principal.toString());
            }
        }
        
        return userInfo;
    }

    /**
     * Example endpoint requiring ADMIN role.
     * This demonstrates how to use @PreAuthorize with database-backed authorities.
     */
    @GetMapping("/api/admin")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public Map<String, String> adminEndpoint() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "Welcome Admin! You have ROLE_ADMIN authority.");
        response.put("status", "success");
        return response;
    }

    /**
     * Example endpoint requiring specific permission.
     * This demonstrates fine-grained permission checking.
     */
    @GetMapping("/api/edit")
    @PreAuthorize("hasAuthority('PERMISSION_EDIT')")
    @ResponseBody
    public Map<String, String> editEndpoint() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "You have edit permission.");
        response.put("status", "success");
        return response;
    }

    @GetMapping("/health")
    @ResponseBody
    public Map<String, String> health() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "UP");
        status.put("app", "spring-boot-test-app");
        return status;
    }
}
