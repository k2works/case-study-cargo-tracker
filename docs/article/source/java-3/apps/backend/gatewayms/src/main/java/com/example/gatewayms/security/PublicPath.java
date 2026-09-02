package com.example.gatewayms.security;

import org.springframework.http.HttpMethod;

/**
 * 認証不要で通す経路の 1 件。
 *
 * @param method 許可するメソッド。参照だけを公開したい経路で更新まで通さないためメソッドを持つ
 * @param pattern Ant 形式のパスパターン
 */
public record PublicPath(HttpMethod method, String pattern) {
}
