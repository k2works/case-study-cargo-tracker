package com.example.cargotracker.shared.conventions;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.infrastructure.security.JwtSecret;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * クラスタに配る JWT 署名鍵が、開発用の既定値でないこと。
 *
 * <p>マニフェストは {@code CARGOTRACKER_PRODUCTIONLIKE=true} を渡す。この状態で
 * {@link JwtSecret#DEVELOPMENT_SECRET} と同じ値を配ると、gatewayms と authms は
 * 起動時に落ちる（実際に CrashLoopBackOff を踏んだ）。</p>
 *
 * <p><b>この検査を文章で代替しない。</b> マニフェストのコメントは守られたかどうかを
 * 教えてくれず、気づくのはクラスタに載せたあとになる。</p>
 */
class ClusterJwtSecretTest {

    /**
     * マニフェストの場所。テストの作業ディレクトリはサブプロジェクトなので、
     * リポジトリのルートまで遡って探す。相対の段数を数えて書くと、
     * サブプロジェクトが増えたときに黙って見つからなくなる。
     */
    private static Path kustomization() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve("ops/k8s/base/kustomization.yaml");
            if (Files.exists(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("ops/k8s/base/kustomization.yaml が見つかりません");
    }

    @Test
    @DisplayName("クラスタの JWT 署名鍵は開発用の既定値ではない")
    void doesNotShipTheDevelopmentSecret() throws IOException {
        String manifest = Files.readString(kustomization());

        assertThat(manifest)
                .as("マニフェストが読めていない（パスが変わると検査は空振りする）")
                .contains("cargo-tracker-jwt");
        assertThat(manifest)
                .as("PRODUCTIONLIKE=true のもとで開発用の既定鍵を配ると、"
                        + "gatewayms と authms が起動できない")
                .doesNotContain(JwtSecret.DEVELOPMENT_SECRET);
    }
}
