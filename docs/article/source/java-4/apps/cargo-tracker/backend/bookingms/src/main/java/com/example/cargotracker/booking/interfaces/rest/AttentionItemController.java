package com.example.cargotracker.booking.interfaces.rest;

import com.example.cargotracker.booking.infrastructure.persistence.AttentionItemMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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
    private final Clock clock;

    public AttentionItemController(AttentionItemMapper attentionItems, Clock clock) {
        this.attentionItems = attentionItems;
        this.clock = clock;
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
