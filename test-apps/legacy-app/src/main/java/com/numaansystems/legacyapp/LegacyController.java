package com.numaansystems.legacyapp;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import java.util.HashMap;
import java.util.Map;

@Controller
public class LegacyController {

    @GetMapping("/")
    public ModelAndView home(HttpServletRequest request) {
        ModelAndView mav = new ModelAndView("index");
        
        String userName = request.getHeader("X-Auth-User-Name");
        String userEmail = request.getHeader("X-Auth-User-Email");
        String userSub = request.getHeader("X-Auth-User-Sub");
        
        mav.addObject("userName", userName != null ? userName : "Not authenticated");
        mav.addObject("userEmail", userEmail != null ? userEmail : "N/A");
        mav.addObject("userSub", userSub != null ? userSub : "N/A");
        mav.addObject("appName", "Legacy Spring Framework Application");
        mav.addObject("appPort", "8082");
        
        return mav;
    }

    @GetMapping("/api/user")
    @ResponseBody
    public Map<String, String> getUserInfo(HttpServletRequest request) {
        Map<String, String> userInfo = new HashMap<>();
        
        String userName = request.getHeader("X-Auth-User-Name");
        String userEmail = request.getHeader("X-Auth-User-Email");
        String userSub = request.getHeader("X-Auth-User-Sub");
        
        userInfo.put("name", userName != null ? userName : "Not authenticated");
        userInfo.put("email", userEmail != null ? userEmail : "N/A");
        userInfo.put("sub", userSub != null ? userSub : "N/A");
        userInfo.put("app", "Legacy Spring Framework Application");
        userInfo.put("port", "8082");
        
        return userInfo;
    }

    @GetMapping("/health")
    @ResponseBody
    public Map<String, String> health() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "UP");
        status.put("app", "legacy-test-app");
        return status;
    }
}
