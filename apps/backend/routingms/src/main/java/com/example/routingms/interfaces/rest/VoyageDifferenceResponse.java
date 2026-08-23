package com.example.routingms.interfaces.rest;

import com.example.routingms.domain.model.VoyageDifference;
import java.util.List;

/**
 * 既にある航海との差分（US25）。
 *
 * <p>何が変わるか分からないまま上書きさせないため、更新前と更新後を並べて返す。
 */
public record VoyageDifferenceResponse(
        String message,
        boolean hasChanges,
        VoyageResponse existing,
        List<ChangeResponse> changes) {

        public VoyageDifferenceResponse {
        // 受け取った一覧を写して持つ。呼び出し元が渡したものをそのまま抱えると、
        // 渡したあとの書き換えがこちらの中身を変える。null は許す——項目が無いことと
        // 空であることは違う
        changes = changes == null ? null : List.copyOf(changes);
        }


    public record ChangeResponse(String item, String before, String after) {
    }

    public static VoyageDifferenceResponse of(VoyageResponse existing, VoyageDifference difference) {
        return new VoyageDifferenceResponse(
                difference.hasChanges()
                        ? "同じ航海番号のスケジュールが既に登録されています"
                        : "同じ航海番号のスケジュールが既に登録されています。変更はありません",
                difference.hasChanges(),
                existing,
                difference.changes().stream()
                        .map(change -> new ChangeResponse(
                                change.item(), change.before(), change.after()))
                        .toList());
    }
}
