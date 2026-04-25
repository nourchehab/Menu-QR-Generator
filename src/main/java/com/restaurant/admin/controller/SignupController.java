package com.restaurant.admin.controller;

import java.security.Principal;
import java.util.Collections;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
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

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String trimmed = email.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase();
    }

    private String resolveEmail(Authentication auth) {
        if (auth == null) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof OidcUser oidcUser) {
            return normalizeEmail(oidcUser.getEmail());
        }
        if (principal instanceof OAuth2User oauth2User) {
            Object emailAttr = oauth2User.getAttributes().get("email");
            return normalizeEmail(emailAttr == null ? null : emailAttr.toString());
        }
        return normalizeEmail(auth.getName());
    }

    private String resolveEmail(Principal principal) {
        if (principal == null) {
            return null;
        }
        if (principal instanceof Authentication auth) {
            return resolveEmail(auth);
        }
        return normalizeEmail(principal.getName());
    }

    private SimpleUser getCurrentUser(Principal principal) {
        String email = resolveEmail(principal);
        if (email == null) {
            return null;
        }
        SimpleUser user = userService.findByEmail(email);
        if (user == null && principal != null) {
            user = userService.findByEmail(principal.getName());
        }
        return user;
    }

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
    public String chooseOption(Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }
        return "redirect:/details";
    }

    @GetMapping("/details")
    public String restaurantDetails(@RequestParam(required = false) String option,
                                    @RequestParam(required = false) String source,
                                    Principal principal,
                                    Model model) {
        if (principal == null) {
            return "redirect:/login";
        }

        SimpleUser user = getCurrentUser(principal);
        if (user == null) {
            return "redirect:/login";
        }

        // Only redirect away if this is NOT an explicit "add new restaurant" request.
        // Existing users adding a new restaurant should pass ?source=new to bypass this.
        if (!"new".equals(source)) {
            if (user.isRestaurantSetupComplete() && restaurantService.userHasRestaurant(user)) {
                return "redirect:/restaurants";
            }
        }

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

        SimpleUser user = getCurrentUser(principal);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found"));
        }

        try {
            // Validate inputs
            if (restaurantName == null || restaurantName.trim().isEmpty())
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Restaurant name is required"));
            if (restaurantType == null || restaurantType.trim().isEmpty())
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Restaurant type is required"));
            if (logoFile == null || logoFile.isEmpty())
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Logo is required"));

            // Removed "already exists" guard — multiple restaurants per user are supported.

            String correlationId = java.util.UUID.randomUUID().toString();
            Restaurant restaurant = restaurantService.setupRestaurant(
                user.getId(),
                restaurantName,
                restaurantType,
                logoFile,
                correlationId
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Restaurant created successfully",
                    "restaurantId", restaurant.getId(),
                    "redirectUrl", "/restaurants"
            ));
        } catch (Exception e) {
            String errorId = (e.getMessage() != null && e.getMessage().startsWith("ErrorId "))
                ? e.getMessage().split(" ")[1]
                : java.util.UUID.randomUUID().toString();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to create restaurant", "errorId", errorId));
        }
    }
}