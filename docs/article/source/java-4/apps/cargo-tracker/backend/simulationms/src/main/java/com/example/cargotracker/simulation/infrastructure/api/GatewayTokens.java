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
 * 目立つ。</p>
 *
 * <p><b>期限切れは自分で判断しない。</b> 有効期間を写して数えると、authms が
 * 期間を変えたときにこちらだけが古くなる。代わりに<b>断られたら捨てて取り直す</b>
 * ——{@link #renew} を呼ぶのは 401 を受けた呼び出し側である。US36 の継続実行は
 * 何時間も走るので、実行ごとに作り直すだけでは足りない（IT16 のレビュー N10）。</p>
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

    /**
     * そのロールのトークンを取り直す（401 を受けたとき）。
     *
     * @return 新しいトークン
     */
    public String renew(StepRole role) {
        String renewed = login(role);
        tokens.put(role, renewed);
        return renewed;
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
