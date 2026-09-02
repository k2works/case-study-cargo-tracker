package com.example.authms.application.port;

/** パスワードの照合。実装は BCrypt（ハッシュ方式をドメインから隠す）。 */
public interface PasswordVerifier {

    boolean matches(String rawPassword, String passwordHash);
}
