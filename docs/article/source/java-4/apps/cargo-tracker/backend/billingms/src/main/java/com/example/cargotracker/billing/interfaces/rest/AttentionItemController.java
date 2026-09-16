package com.example.cargotracker.billing.interfaces.rest;

import com.example.cargotracker.billing.infrastructure.persistence.AttentionItemMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 要確認一覧（S70）の請求ぶん。bookingms・routingms の同名クラスと同じ契約で返す。
 *
 * <p><b>記録するだけでは誰にも見えない。</b> IT13 は請求書を作れなかった事実
 * （重量が分からない・区間が分からない・予約に有効な請求書が既にある）を
 * {@code attention_item} へ書き始めたが、<b>読み出す経路を置き忘れていた</b>——
 * 書いた事実が経理の画面に一度も出ないまま、受け入れテストは DB を直接読んで
 * 緑になっていた（IT13 のレビューで発見）。routingms が IT3 で踏んだのと同じ形である。</p>
 *
 * <p>出すのは<b>自分の担当宛</b>だけ。ロールは Gateway が JWT から取り出して
 * {@code X-Auth-Roles} で伝える（ADR-0001 決定 4）。クライアントの指定は信じない。</p>
 *
 * <p>{@code payload} は応答に載せない（ADR-0003。個人情報を要確認一覧に残さない）。
 * 載せない形を BC 間で揃えておくと、画面は 1 つの型で全部を扱える。</p>
 */
@RestController
@RequestMapping("/api/v1/billing/attention-items")
public class AttentionItemController {

    private final AttentionItemMapper attentionItems;
    private final Clock clock;

    public AttentionItemController(AttentionItemMapper attentionItems, Clock clock) {
        this.attentionItems = attentionItems;
        this.clock = clock;
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

    /** 確認の跡。<b>誰がいつ</b>を返す（「消えた」だけでは、押したことが残らない）。 */
    public record AcknowledgedView(String itemId, String acknowledgedBy, Instant acknowledgedAt) {
    }

    /**
     * 要確認を確認済にする（IT14 引き継ぎ A）。
     *
     * <p><b>記録するだけ・読めるだけでは、仕事が終わらない。</b> 片づけた印が
     * 無いと、同じ行を毎朝読み直すことになり、件数もいつまでも減らない。</p>
     *
     * <p><b>担当宛かどうかはサーバで見る。</b> ロールは Gateway が JWT から
     * 取り出して伝える（ADR-0001 決定 4）。見えない行を片づけられてはいけないので、
     * 一覧に出す条件と同じ条件を更新にも置く（判定をもう 1 か所に書き直さない）。</p>
     *
     * <p><b>2 度目は 404 にする。</b> 上書きすると、最初に確認した人の跡が消える。</p>
     */
    @PostMapping("/{itemId}/acknowledge")
    public AcknowledgedView acknowledge(@PathVariable String itemId,
            @RequestHeader(name = "X-Auth-Roles", required = false) String roles,
            @RequestHeader(name = "X-Auth-Username", required = false) String username) {
        List<String> callerRoles = rolesOf(roles);
        if (callerRoles.isEmpty()) {
            // 既定を置かない。伝達が壊れていることに気づかないまま他ロール宛を
            // 片づけられるほうが重い。
            throw notFound(itemId);
        }
        if (username == null || username.isBlank()) {
            // 誰が確認したか分からない跡は残さない（跡の意味が無くなる）。
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "確認した人が分かりません");
        }
        Instant acknowledgedAt = clock.instant();
        if (attentionItems.acknowledge(itemId, callerRoles, username, acknowledgedAt) == 0) {
            throw notFound(itemId);
        }
        return new AcknowledgedView(itemId, username, acknowledgedAt);
    }

    private static ResponseStatusException notFound(String itemId) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                "確認できる要確認がありません: " + itemId);
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
