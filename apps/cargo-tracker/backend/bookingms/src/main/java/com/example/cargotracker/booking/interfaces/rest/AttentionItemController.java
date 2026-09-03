package com.example.cargotracker.booking.interfaces.rest;

import com.example.cargotracker.booking.infrastructure.persistence.AttentionItemMapper;
import com.example.cargotracker.booking.infrastructure.persistence.ShipperMapper;
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
    private final ShipperMapper shippers;

    public AttentionItemController(AttentionItemMapper attentionItems, ShipperMapper shippers) {
        this.attentionItems = attentionItems;
        this.shippers = shippers;
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
     * 重複相手の荷主 ID。payload のメールアドレスはサーバの中だけで使う。
     *
     * <p>引けなければ {@code null}。相手が居ないことは起こりうる（相手のほうが
     * 先に削除された、payload の形が古い）ので、ここで落とさない。</p>
     */
    private String relatedShipperId(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return null;
        }
        try {
            JsonNode email = JSON.readTree(payloadJson).get("email");
            if (email == null || email.isNull()) {
                return null;
            }
            return shippers.findIdByEmail(email.asText());
        } catch (Exception e) {
            return null;
        }
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
