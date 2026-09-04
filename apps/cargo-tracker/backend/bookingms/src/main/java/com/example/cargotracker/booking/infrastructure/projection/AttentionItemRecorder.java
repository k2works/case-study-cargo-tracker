package com.example.cargotracker.booking.infrastructure.projection;

import com.example.cargotracker.booking.infrastructure.persistence.AttentionItemMapper;
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
 * <p>投影の書き込みが巻き戻っても、弾いた事実は残す。同じトランザクションに
 * すると、投影側が巻き戻ったときに記録も一緒に消え、「登録したのに一覧に出ない」
 * が誰にも見えないまま残る。</p>
 *
 * <p>なお、弾く側は例外ではなく {@code ON CONFLICT DO NOTHING} の戻り値で見る
 * （IT2）。例外にすると PostgreSQL がトランザクションを中断し、捕まえても外側の
 * 投影とトークンが書けなくなって、<b>その 1 件で投影全体が止まる</b>。</p>
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
     * リプレイでも消さない（`data-model.md`）。毎回新しい識別子を振ると、投影を読み直す
     * たびに同じ内容の行が積み上がり、営業が毎朝見る一覧が信用されなくなる。
     * 「何が・どの対象で・なぜ」が同じなら同じ行として扱う（`ON CONFLICT DO NOTHING`）。</p>
     */
    private static String itemIdOf(String kind, String targetType, String targetId,
            String reason) {
        String seed = String.join("\u0000", kind, targetType, targetId, reason);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(seed.getBytes(StandardCharsets.UTF_8));
            String hex = HexFormat.of().formatHex(digest);
            // VARCHAR(36) に収める。UUID と同じ形にして、既存の行と見分けが付くようにしない
            // （見分ける必要が無い。どちらも 1 つの事実を指す）。
            return hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16)
                    + "-" + hex.substring(16, 20) + "-" + hex.substring(20, 32);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 が使えません", e);
        }
    }
}
