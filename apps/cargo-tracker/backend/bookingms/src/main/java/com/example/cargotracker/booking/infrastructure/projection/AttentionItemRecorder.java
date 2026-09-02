package com.example.cargotracker.booking.infrastructure.projection;

import com.example.cargotracker.booking.infrastructure.persistence.AttentionItemMapper;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 要確認一覧への記録。<b>必ず別トランザクションで書く。</b>
 *
 * <p>一意制約で弾かれた直後の接続は、PostgreSQL では中断状態にある。同じ
 * トランザクションで記録しようとしても書き込めず、「弾いたログは出るのに
 * 要確認一覧には出ない」という、いちばん気づきにくい形で消える
 * （IT1 タスク 2.6 の受け入れテストで実測）。</p>
 */
@Component
public class AttentionItemRecorder {

    private final AttentionItemMapper attentionItems;

    public AttentionItemRecorder(AttentionItemMapper attentionItems) {
        this.attentionItems = attentionItems;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String kind, String targetType, String targetId, String assignedRole,
            String reason, String payloadJson, Instant occurredAt) {
        attentionItems.insert(new AttentionItemMapper.AttentionItemRow(
                UUID.randomUUID().toString(), kind, targetType, targetId, assignedRole,
                reason, payloadJson, occurredAt, null, null));
    }
}
