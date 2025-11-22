package com.numaansystems.testapp;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

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
        
        return "index";
    }

    @GetMapping("/api/user")
    @ResponseBody
    public Map<String, String> getUserInfo(
            @RequestHeader(value = "X-Auth-User-Name", required = false) String userName,
            @RequestHeader(value = "X-Auth-User-Email", required = false) String userEmail,
            @RequestHeader(value = "X-Auth-User-Sub", required = false) String userSub) {
        
        Map<String, String> userInfo = new HashMap<>();
        userInfo.put("name", userName != null ? userName : "Not authenticated");
        userInfo.put("email", userEmail != null ? userEmail : "N/A");
        userInfo.put("sub", userSub != null ? userSub : "N/A");
        userInfo.put("app", "Modern Spring Boot Application");
        userInfo.put("port", "8081");
        
        return userInfo;
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
