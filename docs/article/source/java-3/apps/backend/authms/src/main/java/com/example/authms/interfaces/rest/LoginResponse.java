package com.example.authms.interfaces.rest;

import java.util.List;

/**
 * ログイン応答（architecture_backend.md の契約）。
 *
 * <p>画面が誰として入っているか・どのメニューを出すかを判断できるよう、表示名とロールを
 * 明示的に返す。トークンを画面側で復号させると、画面が JWT の内部構造に依存する。
 */
public record LoginResponse(String token, String userId, String displayName, List<String> roles)
        implements AuthenticationResponse {
}
