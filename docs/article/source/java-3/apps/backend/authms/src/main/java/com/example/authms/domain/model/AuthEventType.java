package com.example.authms.domain.model;

/** 認証監査ログの事象種別（data-model.md の auth_audit_log.event_type）。 */
public enum AuthEventType {
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    LOCKED,
    UNLOCKED,
    DISABLED_ATTEMPT,
    LOGOUT
}
