package com.example.cargotracker.shared.infrastructure.security;

import java.nio.charset.StandardCharsets;

/**
 * JWT の署名鍵。
 *
 * <p><b>開発用の既定値を本番で黙って使わせない。</b> 既定値を持たせると、環境変数を
 * 渡し忘れてもリポジトリに書かれた既知の鍵で起動が成功する。起動時に接続を検査して
 * 「黙って動く」を潰した（{@code AxonServerStartupCheck}）のと同じ理由で、ここも
 * 黙って動かさない。</p>
 */
public final class JwtSecret {

    /** 開発とテストでだけ使う既定値。本番プロファイルでは使わせない。 */
    public static final String DEVELOPMENT_SECRET = "cargo-tracker-development-secret-key-32bytes!";

    private static final int MINIMUM_BYTES = 32;

    private final String value;

    private JwtSecret(String value) {
        this.value = value;
    }

    /**
     * 設定値から署名鍵を組み立てる。
     *
     * @param configured 設定された鍵（未設定なら {@code null} か空）
     * @param productionLike 本番相当の環境か。真なら既定値を許さない
     */
    public static JwtSecret of(String configured, boolean productionLike) {
        String candidate = configured == null || configured.isBlank()
                ? DEVELOPMENT_SECRET
                : configured;

        if (productionLike && DEVELOPMENT_SECRET.equals(candidate)) {
            throw new IllegalStateException(
                    "本番相当の環境で開発用の JWT 署名鍵が使われています。"
                            + "環境変数 CARGOTRACKER_JWT_SECRET を設定してください"
                            + "（Spring のリラックスバインディングはハイフンを除きます）");
        }
        if (candidate.getBytes(StandardCharsets.UTF_8).length < MINIMUM_BYTES) {
            throw new IllegalStateException(
                    "JWT の署名鍵は " + MINIMUM_BYTES + " バイト以上が要ります");
        }
        return new JwtSecret(candidate);
    }

    public String value() {
        return value;
    }
}
