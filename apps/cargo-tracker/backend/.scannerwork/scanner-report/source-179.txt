package com.example.cargotracker.routing.interfaces.rest;

import com.example.cargotracker.routing.infrastructure.persistence.AttentionItemMapper;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 要確認一覧（S70）の経路設計ぶん。bookingms の同名クラスと同じ契約で返す。
 *
 * <p><b>記録するだけでは誰にも見えない。</b> 投影は航海番号が一意制約で弾かれたときに
 * {@code attention_item} へ書くが、IT3 の途中まで routingms にはそれを読み出す経路が
 * 無く、経路設計者の画面には出なかった。記録した先に読み口を対で置く。</p>
 *
 * <p>出すのは<b>自分の担当宛</b>だけ。ロールは Gateway が JWT から取り出して
 * {@code X-Auth-Roles} で伝える（ADR-0001 決定 4）。クライアントの指定は信じない。</p>
 *
 * <p>{@code payload} は応答に載せない。bookingms と同じ理由（個人情報を要確認一覧に
 * 残さない。ADR-0003）に加え、載せない形を BC 間で揃えておくと、画面は 1 つの型で
 * 両方を扱える。</p>
 */
@RestController
@RequestMapping("/api/v1/routing/attention-items")
public class AttentionItemController {

    private final AttentionItemMapper attentionItems;

    public AttentionItemController(AttentionItemMapper attentionItems) {
        this.attentionItems = attentionItems;
    }

    /** 画面に出す 1 件。bookingms の {@code AttentionItemView} と同じ項目。 */
    public record AttentionItemView(String itemId, String kind, String targetType, String targetId,
            String assignedRole, String reason, String relatedShipperId, Instant occurredAt) {
    }

    public record AttentionItemListView(List<AttentionItemView> items) {
    }

    @GetMapping
    public AttentionItemListView list(
            @RequestHeader(name = "X-Auth-Roles", required = false) String roles) {
        // ロールが 1 つも伝わっていなければ何も出さない。既定を置くと、伝達が壊れて
        // いることに気づかないまま他ロールの担当分が見える。
        return new AttentionItemListView(rolesOf(roles).stream()
                .flatMap(role -> attentionItems.findOpenByRole(role).stream())
                .distinct()
                .map(row -> new AttentionItemView(row.itemId(), row.kind(), row.targetType(),
                        row.targetId(), row.assignedRole(), row.reason(), null, row.occurredAt()))
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
