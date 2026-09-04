package com.example.cargotracker.routing.infrastructure.projection;

import com.example.cargotracker.routing.infrastructure.persistence.AttentionItemMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 要確認一覧への記録。<b>必ず別トランザクションで書く。</b>
 *
 * <p>bookingms の同名クラスと同じ形にしている。投影の書き込みが巻き戻っても、弾いた
 * 事実は残す。同じトランザクションにすると、投影側が巻き戻ったときに記録も一緒に消え、
 * 「登録したのに一覧に出ない」が誰にも見えないまま残る。</p>
 */
@Component
public class AttentionItemRecorder {

    private final AttentionItemMapper attentionItems;

    public AttentionItemRecorder(AttentionItemMapper attentionItems) {
        this.attentionItems = attentionItems;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void add(String kind, String targetType, String targetId, String assignedRole,
            String reason, String payloadJson, Instant occurredAt) {
        attentionItems.insert(new AttentionItemMapper.AttentionItemRow(
                itemIdOf(kind, targetType, targetId, reason), kind, targetType, targetId,
                assignedRole, reason, payloadJson, occurredAt, null, null));
    }

    /**
     * 同じ事実には同じ識別子を与える。
     *
     * <p><b>{@code UUID.randomUUID()} にしない。</b> {@code attention_item} は追記専用で
     * リプレイでも消さない。毎回新しい識別子を振ると、投影を読み直すたびに同じ内容の行が
     * 積み上がり、経路設計者が毎朝見る一覧が信用されなくなる。</p>
     */
    private static String itemIdOf(String kind, String targetType, String targetId,
            String reason) {
        String seed = String.join("|", kind, targetType, targetId, reason);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(seed.getBytes(StandardCharsets.UTF_8));
            String hex = HexFormat.of().formatHex(digest);
            return hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16)
                    + "-" + hex.substring(16, 20) + "-" + hex.substring(20, 32);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 が使えません", e);
        }
    }
}
