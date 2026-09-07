package com.example.cargotracker.booking.infrastructure.persistence;

import com.example.cargotracker.booking.application.port.TrackingNumberGenerator;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * {@link TrackingNumberGenerator} の実装。<b>衝突検査つきの乱数で採る</b>（ADR-0011）。
 *
 * <p><b>なぜ連番をやめたか。</b> 追跡番号は荷主に共有され、認証なしで照会できる
 * （US18 の公開照会）。連番だと 1 つ知れば前後がすべて推測でき、他人の貨物の
 * 状態・現在地・到着予定が読める。IT7 の実装は {@code T-2026-000001} の連番で、
 * クラスタの実データが {@code -000001}・{@code -000005}・{@code -000007} と並んでいた。</p>
 *
 * <p><b>採番の場所は投影側のまま</b>（ADR-0010 決定 2 / data-model.md「採番は投影側」）。
 * 集約は「発行してよいか」だけを判断する。変えたのは採り方だけで、場所ではない。</p>
 *
 * <p><b>衝突は乱数だけに任せない。</b> 36^10 でも同じ番号が 2 つ出れば、別の貨物が
 * 同じ追跡に見える。採るたびに投影テーブルへ問い合わせ、空いている番号だけを返す。
 * {@code cargo_summary.tracking_number} の UNIQUE 制約が最後の砦として残る。</p>
 */
@Component
public class RandomTrackingNumberGenerator implements TrackingNumberGenerator {

    /** 正典（domain-model.md:1431）: {@code TRK-} + 大文字英数字 10 桁。 */
    private static final String PREFIX = "TRK-";
    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int LENGTH = 10;

    /** 空きが見つからないまま回り続けないための上限。ここに達したら異常。 */
    private static final int MAX_ATTEMPTS = 10;

    private final CargoSummaryMapper cargos;
    private final SecureRandom random = new SecureRandom();

    public RandomTrackingNumberGenerator(CargoSummaryMapper cargos) {
        this.cargos = cargos;
    }

    @Override
    public String next() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = candidate();
            if (!cargos.trackingNumberExists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "追跡番号の空きが " + MAX_ATTEMPTS + " 回連続で見つかりませんでした");
    }

    private String candidate() {
        StringBuilder number = new StringBuilder(PREFIX);
        for (int i = 0; i < LENGTH; i++) {
            number.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return number.toString();
    }
}
