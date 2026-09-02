/**
 * 認証コンテキストのセキュリティ実装。パスワードの照合と JWT の発行を担う。
 *
 * <p><strong>JWT の検証はここで行わない。</strong> 署名検証は API Gateway に一元化しており
 * （ADR-004）、鍵を持つのは発行側の authms と検証側の gatewayms だけである。
 * 検証をここに置くと、鍵の配布とローテーションが全サービスに拡散する。
 * この分担は ArchUnit の {@code noTokenVerificationRule} が検証する。
 */
package com.example.authms.infrastructure.security;
