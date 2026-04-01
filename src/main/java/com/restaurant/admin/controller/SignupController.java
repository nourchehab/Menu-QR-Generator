package com.restaurant.admin.controller;

import java.security.Principal;
import java.util.Collections;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.restaurant.admin.model.Restaurant;
import com.restaurant.admin.model.SimpleUser;
import com.restaurant.admin.service.EmailVerificationService;
import com.restaurant.admin.service.RestaurantService;
import com.restaurant.admin.service.SimpleUserService;

import jakarta.servlet.http.HttpSession;

@Controller
public class SignupController {

    @Autowired
    private SimpleUserService userService;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private RestaurantService restaurantService;

    @PostMapping("/signup")
    public String signup(
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam String confirmPassword,
            HttpSession session) {

        if (!password.equals(confirmPassword)) {
            return "redirect:/signup?error=password";
        }

        if (userService.emailExists(email)) {
            return "redirect:/signup?error=email";
        }

        session.setAttribute("signupEmail", email.trim());
        session.setAttribute("signupPassword", password);

        try {
            emailVerificationService.sendSignupVerificationCode(email.trim());
        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/signup?error=system";
        }

        return "redirect:/signup/verify";
    }

    @GetMapping("/signup/verify")
    public String showVerificationPage(HttpSession session, Model model) {
        String email = (String) session.getAttribute("signupEmail");

        if (email == null || email.trim().isEmpty()) {
            return "redirect:/signup";
        }

        model.addAttribute("userEmail", email);
        return "signup-verify";
    }

    @PostMapping("/signup/verify")
    public String verifyAndCompleteSignup(
            @RequestParam String code,
            HttpSession session,
            Model model) {

        String email = (String) session.getAttribute("signupEmail");
        String password = (String) session.getAttribute("signupPassword");

        if (email == null || password == null) {
            return "redirect:/signup";
        }

        if (!emailVerificationService.verifyCode(email, code)) {
            model.addAttribute("userEmail", email);
            model.addAttribute("error", "invalid");
            return "signup-verify";
        }

        boolean success = userService.registerUser(email, password);

        if (!success) {
            return "redirect:/signup?error=system";
        }

        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                email,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );

        SecurityContextHolder.getContext().setAuthentication(authToken);
        session.setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());

        session.removeAttribute("signupEmail");
        session.removeAttribute("signupPassword");

        return "redirect:/details";
    }

    @PostMapping("/signup/verify/resend")
    public String resendVerificationCode(HttpSession session, Model model) {
        String email = (String) session.getAttribute("signupEmail");

        if (email == null) {
            return "redirect:/signup";
        }

        try {
            emailVerificationService.resendVerificationCode(email);
            model.addAttribute("userEmail", email);
            model.addAttribute("resent", true);
            return "signup-verify";
        } catch (Exception e) {
            model.addAttribute("userEmail", email);
            model.addAttribute("error", "resend");
            return "signup-verify";
        }
    }

    @GetMapping("/choose-option")
    public String chooseOption() {
        return "choose-option";
    }

    @GetMapping("/details")
    public String restaurantDetails(@RequestParam(required = false) String option, Model model) {
        model.addAttribute("formAction", "/signup/restaurant/setup");
        return "restaurant-details";
    }

    @PostMapping("/signup/restaurant/setup")
    @ResponseBody
    public ResponseEntity<?> completeRestaurantSetup(
            @RequestParam String restaurantName,
            @RequestParam String restaurantType,
            @RequestParam(value = "logoUpload", required = false) MultipartFile logoFile,
            Principal principal) {

        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }

        String email = principal.getName();
        SimpleUser user = userService.findByEmail(email);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found"));
        }

        try {
            Restaurant restaurant = restaurantService.setupRestaurant(
                    user.getId(),
                    restaurantName,
                    restaurantType,
                    logoFile
            );
            if (restaurant != null) {
                user.setRestaurantSetupComplete(true);
                userService.save(user);
                
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Restaurant created successfully",
                        "restaurantId", restaurant.getId()
                ));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create restaurant"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

}
