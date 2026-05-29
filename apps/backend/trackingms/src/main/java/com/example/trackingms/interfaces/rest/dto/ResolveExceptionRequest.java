package com.example.trackingms.interfaces.rest.dto;

/**
 * 追跡例外解決リクエスト（US19 / US20）。
 *
 * @param resolution 対応内容（補償方針・代替ルート等）。必須
 */
public record ResolveExceptionRequest(String resolution) {
}
