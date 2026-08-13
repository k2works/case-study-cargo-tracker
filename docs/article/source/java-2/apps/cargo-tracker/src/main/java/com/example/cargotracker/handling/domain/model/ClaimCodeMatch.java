package com.example.cargotracker.handling.domain.model;

import java.util.Locale;

/**
 * 引取確認コードの照合（US35）。
 *
 * <p><strong>コードを採番するのは Booking、照合するのは Handling である。</strong>
 * 境界を越えて運ばれてくるのは素の文字列であり（ADR-005）、
 * {@code ClaimCode} そのものは渡ってこない。<strong>照合の規則はここに 1 つだけ置く。</strong>
 *
 * <p><strong>大小文字と前後の空白は問わない。</strong> コードは電話や紙で伝わる。
 * 入力の揺れで拒むと、正しい荷受人が引き取れず、
 * <strong>現場は照合そのものを迂回したくなる</strong>（Booking 側の
 * {@code ClaimCode.matches} と同じ判断である）。
 */
public final class ClaimCodeMatch {

    private ClaimCodeMatch() {
    }

    /**
     * 採番されたコードと提示されたコードが一致するか。
     *
     * <p>空・{@code null} は<strong>不一致</strong>である。
     * 「入力が無いから照合しない」にすると、<strong>空欄で引き取れる</strong>。
     */
    public static boolean matches(String issued, String presented) {
        if (issued == null || issued.isBlank() || presented == null || presented.isBlank()) {
            return false;
        }
        return normalize(issued).equals(normalize(presented));
    }

    private static String normalize(String value) {
        return value.strip().toUpperCase(Locale.ROOT);
    }
}
