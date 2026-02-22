package com.restaurant.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "Username or Email cannot be blank")
    private String email;

    @NotBlank(message = "Password cannot be blank")
    private String password;
}