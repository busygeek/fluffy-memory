package com.example.restproject.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record User(
        Long id,
        @NotBlank String name,
        @NotBlank @Email String email,
        @NotBlank String role
) {
}
