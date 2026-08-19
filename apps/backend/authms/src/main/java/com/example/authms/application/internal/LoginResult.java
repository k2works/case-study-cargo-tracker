package com.example.authms.application.internal;

import com.example.shared.auth.Role;
import java.util.Set;

/**
 * ログイン結果。
 *
 * <p>画面が誰として入っているか・どのメニューを出すかを判断できるよう、ロールと表示名を
 * 明示的に返す。トークンを画面側で復号させると、JWT の内部構造に画面が依存する。
 */
public record LoginResult(String token, String userId, String displayName, Set<Role> roles) {
}
