package com.example.cargotracker.simulation.infrastructure.api;

import com.example.cargotracker.simulation.domain.model.valueobjects.StepRole;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.client.RestClient;

/**
 * ロールごとのトークンを取る（[ADR-0020] 決定 2）。
 *
 * <p><b>人と同じ経路でログインする。</b> 内部でトークンを作ると、認証の経路を
 * 踏まない——そこが壊れていても気づけない。</p>
 *
 * <p><b>1 度取ったら使い回す。</b> 工程ごとにログインすると、1 本のシナリオで
 * 10 回以上認証することになり、確かめたいもの（業務の連鎖）より認証の負荷が
 * 目立つ。<b>実行をまたいでは持たない</b>——期限切れを自分で判断しないため、
 * 実行ごとに作り直す。</p>
 */
public class GatewayTokens {

    /** 動作確認用の利用者に共通のパスワード（ADR-0004）。 */
    private static final String PASSWORD = "secret1234"; // NOSONAR: 開発環境の動作確認用（ADR-0004）

    private final RestClient client;
    private final Map<StepRole, String> tokens = new ConcurrentHashMap<>();

    public GatewayTokens(RestClient client) {
        this.client = client;
    }

    /** そのロールのトークン。<b>初回だけログインする</b>。 */
    public String of(StepRole role) {
        return tokens.computeIfAbsent(role, this::login);
    }

    private String login(StepRole role) {
        LoginResponse response = client.post()
                .uri("/api/v1/auth/login")
                .body(new LoginRequest(role.username(), PASSWORD))
                .retrieve()
                .body(LoginResponse.class);
        if (response == null || response.token() == null) {
            throw new IllegalStateException(
                    "ログインできませんでした: " + role.username()
                            + "（動作確認用の利用者が入っているか確かめてください・ADR-0004）");
        }
        return response.token();
    }

    private record LoginRequest(String username, String password) {
    }

    private record LoginResponse(String token) {
    }
}
