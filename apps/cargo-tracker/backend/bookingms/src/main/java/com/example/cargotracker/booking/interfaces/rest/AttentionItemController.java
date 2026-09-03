package com.example.cargotracker.booking.interfaces.rest;

import com.example.cargotracker.booking.infrastructure.persistence.AttentionItemMapper;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 要確認一覧（S70）。投影が弾いたものを担当ロール向けに出す。
 *
 * <p>出すのは<b>自分の担当宛</b>だけ。ロールは Gateway が JWT から取り出して
 * {@code X-Auth-Roles} で伝える（署名の再検証はしない。ADR-0001 決定 4）。
 * クライアントの指定を信じると、他ロール宛の要確認まで見えてしまう。</p>
 */
@RestController
@RequestMapping("/api/v1/booking/attention-items")
public class AttentionItemController {

    private final AttentionItemMapper attentionItems;

    public AttentionItemController(AttentionItemMapper attentionItems) {
        this.attentionItems = attentionItems;
    }

    public record AttentionItemView(String itemId, String kind, String targetType, String targetId,
            String assignedRole, String reason, Instant occurredAt) {
    }

    public record AttentionItemListView(List<AttentionItemView> items) {
    }

    @GetMapping
    public AttentionItemListView list(
            @RequestHeader(name = "X-Auth-Roles", required = false) String roles) {
        // ロールが 1 つも伝わっていなければ何も出さない。既定で営業宛を出すと、
        // 伝達が壊れていることに気づかないまま他ロールの担当分が見える。
        return new AttentionItemListView(rolesOf(roles).stream()
                .flatMap(role -> attentionItems.findOpenByRole(role).stream())
                .distinct()
                .map(row -> new AttentionItemView(row.itemId(), row.kind(), row.targetType(),
                        row.targetId(), row.assignedRole(), row.reason(), row.occurredAt()))
                .toList());
    }

    private static List<String> rolesOf(String header) {
        if (header == null || header.isBlank()) {
            return List.of();
        }
        return Arrays.stream(header.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .toList();
    }
}
