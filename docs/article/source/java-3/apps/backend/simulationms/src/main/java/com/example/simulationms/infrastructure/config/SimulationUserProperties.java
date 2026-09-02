package com.example.simulationms.infrastructure.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 工程を踏む利用者の設定（[ADR-030] 決定 2）。
 *
 * <p>ロールから利用者への対応を設定で持つ。環境ごとに利用者は変わるため、
 * <strong>コードに書かない</strong>。
 */
@ConfigurationProperties(prefix = "app.simulation-users")
public record SimulationUserProperties(String password, Map<String, String> usernames) {
}
