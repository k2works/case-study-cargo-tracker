package com.example.cargotracker.handling;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.handling.domain.model.valueobjects.CargoSnapshot;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * その貨物に通関が要るか（US29 / IT12 の C29）。
 *
 * <p><strong>IT11 の引取の拒否は「申告が登録されている貨物」にしか効かなかった。</strong>
 * 申告を出し忘れた輸入貨物は引取が通る。**実務では「申告を出し忘れている貨物」こそ
 * 引き取らせてはいけない対象である**（IT11 レビュー C29）。
 *
 * <p><strong>判断はドメインの述語として置く。</strong> 「申告があるかどうか」は
 * 手続きの有無であって、通関が要るかどうかではない。国をまたぐ輸送には通関が要り、
 * 同じ国の中で完結する輸送には要らない。
 *
 * <p>国は<strong>UN/LOCODE の先頭 2 文字</strong>である（ISO 3166-1 alpha-2）。
 * マスタを引かずに判断できる — <strong>引取の可否を決める場所で
 * DB を 1 回増やさない</strong>。
 */
@DisplayName("通関が要るかの判断（C29）")
class CustomsRequirementTest {

    private static CargoSnapshot 貨物(String origin, String destination) {
        return new CargoSnapshot("11111111-1111-1111-1111-111111111111",
                origin, destination, "受取花子", null, List.of());
    }

    /** <strong>国をまたぐ輸送には通関が要る。</strong> */
    @Test
    void 国をまたぐ輸送には通関が要る() {
        assertThat(貨物("JPOSA", "USLAX").requiresCustoms()).isTrue();
        assertThat(貨物("KRPUS", "USSEA").requiresCustoms()).isTrue();
        assertThat(貨物("NLRTM", "DEHAM").requiresCustoms()).isTrue();
    }

    /**
     * <strong>同じ国の中で完結する輸送には要らない。</strong>
     *
     * <p>「要ること」だけを確かめると、常に true を返す実装でも緑になる。
     * その実装だと<strong>国内輸送の引取がすべて止まる</strong>。
     */
    @Test
    void 同じ国の中で完結する輸送には通関は要らない() {
        assertThat(貨物("JPOSA", "JPTYO").requiresCustoms()).isFalse();
        assertThat(貨物("USLAX", "USNYC").requiresCustoms()).isFalse();
    }

    /**
     * <strong>判断できないときは要るとみなす。</strong>
     *
     * <p>港コードが読めない場合、通関が要らないと決めてしまうと
     * <strong>引取の守りが黙って外れる</strong>。守りを外す側に倒さない。
     */
    @Test
    void 判断できないときは通関が要るとみなす() {
        assertThat(貨物("JP", "USLAX").requiresCustoms()).isTrue();
        assertThat(貨物("JPOSA", "").requiresCustoms()).isTrue();
    }
}
