package com.example.cargotracker.authms.interfaces.rest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "ユーザー名は必須です")
        @Size(max = 50, message = "ユーザー名は50文字以内にしてください")
        String username,
        @NotBlank(message = "メールアドレスは必須です")
        @Email(message = "メールアドレスの形式が正しくありません")
        String email,
        @NotBlank(message = "パスワードは必須です")
        @Size(min = 8, message = "パスワードは8文字以上にしてください")
        String password,
        String role
) {}
