package com.example.authms.application.port;

import com.example.authms.domain.model.User;

/**
 * JWT の発行。鍵を持つのは authms（発行）と gatewayms（検証）だけである（ADR-004）。
 */
public interface TokenIssuer {

    String issue(User user);
}
