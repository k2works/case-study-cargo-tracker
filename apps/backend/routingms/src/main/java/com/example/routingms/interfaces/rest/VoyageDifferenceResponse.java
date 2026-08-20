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
