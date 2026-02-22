package com.restaurant.admin.controller;

import java.security.Principal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Collections;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.restaurant.admin.service.RestaurantService;
import com.restaurant.admin.model.Restaurant;

import com.restaurant.admin.service.SimpleUserService;
import com.restaurant.admin.model.Restaurant;
import com.restaurant.admin.model.SimpleUser;
import com.restaurant.admin.service.EmailVerificationService;

import jakarta.servlet.http.HttpSession;

@Controller
public class SignupController {

    @Autowired
    private SimpleUserService userService;

    @Autowired
    private EmailVerificationService emailVerificationService;
    @Autowired
    private RestaurantService restaurantService;

    /**
     * Step 1: User submits email and password Send verification code to email
     */
    @PostMapping("/signup")
    public String signup(
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam String confirmPassword,
            HttpSession session) {

        // Passwords must match
        if (!password.equals(confirmPassword)) {
            return "redirect:/signup?error=password";
        }

        // Check if email already exists (using your existing service)
        if (userService.emailExists(email)) {
            return "redirect:/signup?error=email";
        }

        // Store email and password in session (cleaner approach)
        session.setAttribute("signupEmail", email.trim());
        session.setAttribute("signupPassword", password);

        // Send verification code
        try {
            emailVerificationService.sendSignupVerificationCode(email.trim());
        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/signup?error=system";
        }

        // Redirect WITHOUT parameters
        return "redirect:/signup/verify";
    }

    /**
     * Step 2: Show verification code entry page
     */
    @GetMapping("/signup/verify")
    public String showVerificationPage(HttpSession session, Model model) {
        String email = (String) session.getAttribute("signupEmail");

        if (email == null || email.trim().isEmpty()) {
            return "redirect:/signup";
        }

        model.addAttribute("userEmail", email);
        return "signup-verify";
    }

    /**
     * Step 3: Verify the code and complete signup Then redirect to
     * choose-option page
     */
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

        // Verify the code
        if (!emailVerificationService.verifyCode(email, code)) {
            model.addAttribute("userEmail", email);
            model.addAttribute("error", "invalid");
            return "signup-verify";
        }

        // Code is valid, register the user using your existing service
        boolean success = userService.registerUser(email, password);

        if (!success) {
            return "redirect:/signup?error=system";
        }

// 🔥 AUTO LOGIN THE USER
        UsernamePasswordAuthenticationToken authToken
                = new UsernamePasswordAuthenticationToken(
                        email,
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                );

        SecurityContextHolder.getContext().setAuthentication(authToken);

// Clear session data
        session.removeAttribute("signupEmail");
        session.removeAttribute("signupPassword");

// Now they are authenticated
        return "redirect:/choose-option";
    }

    /**
     * Resend verification code
     */
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
        return "choose-option"; // Serves choose-option.html
    }

    @GetMapping("/details")
    public String restaurantDetails(@RequestParam String option) {
        return "restaurant-details"; // Serves restaurant-details.html from templates folder
    }

    public String completeRestaurantSetup(
            @RequestParam String restaurantName,
            @RequestParam String restaurantType,
            @RequestParam(value = "logoUpload", required = false) MultipartFile logoFile,
            HttpSession session,
            Principal principal) {

        if (principal == null) {
            return "redirect:/login";
        }

        String email = principal.getName();

        SimpleUser user = userService.findByEmail(email);

        if (user == null) {
            return "redirect:/login";
        }
        try {
            // ✅ Save restaurant to database with logo upload
            Restaurant restaurant = restaurantService.setupRestaurant(
                    user.getId(),
                    restaurantName,
                    restaurantType,
                    logoFile
            );
        } catch (Exception e) {
            e.printStackTrace();

        }
        // ✅ Set boolean to true
        user.setRestaurantSetupComplete(true);

        userService.save(user);

        return "redirect:/dashboard";
    }

}
