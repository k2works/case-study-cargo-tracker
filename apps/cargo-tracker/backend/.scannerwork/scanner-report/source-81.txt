package com.example.cargotracker.booking.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import java.util.regex.Pattern;

/**
 * 危険物申告（IMO クラスと UN 番号）。危険物の予約には必ず添える。
 *
 * <p><b>書式まで見る。</b> 空白だけを見ていると `UN 1263`・`un1263`・`3.1` が
 * そのまま通り、経路設計（受け入れ可否の判断）と通関で表記ゆれとして効いてくる。
 * マニュアルには書式が書いてあったのに、システムは検査していなかった。
 * 書いてあるだけの規約は守られない。</p>
 *
 * <p>IMO クラスは 1〜9（副次危険性の `3.1` のような細分は本リリースでは扱わない）、
 * UN 番号は `UN` に続く 4 桁。</p>
 */
public record HazardousDeclaration(String imoClass, String unNumber) {

    private static final Pattern IMO_CLASS = Pattern.compile("[1-9]");
    private static final Pattern UN_NUMBER = Pattern.compile("UN\\d{4}");

    public HazardousDeclaration {
        if (imoClass == null || imoClass.isBlank()) {
            throw new BusinessRuleViolation("IMO クラスは必須です");
        }
        if (!IMO_CLASS.matcher(imoClass).matches()) {
            throw new BusinessRuleViolation("IMO クラスは 1 から 9 の数字です: " + imoClass);
        }
        if (unNumber == null || unNumber.isBlank()) {
            throw new BusinessRuleViolation("UN 番号は必須です");
        }
        if (!UN_NUMBER.matcher(unNumber).matches()) {
            throw new BusinessRuleViolation("UN 番号は UN に続く 4 桁です: " + unNumber);
        }
    }
}
