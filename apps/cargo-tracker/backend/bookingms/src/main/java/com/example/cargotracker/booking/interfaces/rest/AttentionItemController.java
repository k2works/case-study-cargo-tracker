package com.example.cargotracker.booking.interfaces.rest;

import com.example.cargotracker.booking.infrastructure.persistence.AttentionItemMapper;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 要確認一覧（S70）。投影が弾いたものを担当ロール向けに出す。
 *
 * <p>既定は自ロール宛。Gateway がロールを伝えるまでは指定を受け取る。</p>
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
            @RequestParam(name = "role", defaultValue = "ROLE_SALES") String role) {
        return new AttentionItemListView(attentionItems.findOpenByRole(role).stream()
                .map(row -> new AttentionItemView(row.itemId(), row.kind(), row.targetType(),
                        row.targetId(), row.assignedRole(), row.reason(), row.occurredAt()))
                .toList());
    }
}
