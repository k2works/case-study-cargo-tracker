package com.example.cargotracker.booking.interfaces.rest;

import com.example.cargotracker.booking.infrastructure.persistence.AttentionItemMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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

    private static final ObjectMapper JSON = new ObjectMapper();

    private final AttentionItemMapper attentionItems;

    public AttentionItemController(AttentionItemMapper attentionItems) {
        this.attentionItems = attentionItems;
    }

    /**
     * 画面に出す 1 件。
     *
     * <p><b>{@code payload} は載せない。</b> 受け付けた内容には氏名・メール・電話・
     * 住所が入っており、これを応答に出すと、鍵を破棄しても要確認一覧に平文の個人情報が
     * 残る。削除要求に応えられなくなり、[ADR-0003] の目的が崩れる。</p>
     *
     * <p>代わりに {@code relatedShipperId} を返す。サーバの中だけで payload の
     * メールアドレスから重複相手を引き、<b>識別子だけ</b>を渡す。画面はそこから
     * 既存の荷主を開ける。</p>
     */
    public record AttentionItemView(String itemId, String kind, String targetType, String targetId,
            String assignedRole, String reason, String relatedShipperId, Instant occurredAt) {
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
                        row.targetId(), row.assignedRole(), row.reason(),
                        relatedShipperId(row.payload()), row.occurredAt()))
                .toList());
    }

    /**
     * 重複相手の荷主 ID。
     *
     * <p>{@code payload} には個人情報を入れず、投影が弾いた時点で引いた識別子だけを
     * 持たせている（ADR-0003 決定 6）。ここでメールアドレスから引き直しません。</p>
     *
     * <p><b>catch は解析だけを囲む。</b> DB 読み出しまで広げると、障害が
     * 「重複相手が居ない」に化けて原因が残りません。</p>
     */
    private static String relatedShipperId(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return null;
        }
        JsonNode node;
        try {
            node = JSON.readTree(payloadJson);
        } catch (Exception e) {
            // 古い形の payload。読めないことは起こりうるので落とさない。
            return null;
        }
        JsonNode existing = node.get("existingShipperId");
        return existing == null || existing.isNull() ? null : existing.asText();
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
