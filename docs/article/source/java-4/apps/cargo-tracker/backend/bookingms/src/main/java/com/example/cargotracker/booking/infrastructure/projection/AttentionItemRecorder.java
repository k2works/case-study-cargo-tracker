package com.example.cargotracker.booking.infrastructure.projection;

import com.example.cargotracker.booking.infrastructure.persistence.AttentionItemMapper;
import com.example.cargotracker.shared.domain.attention.AttentionItemId;
import java.time.Instant;
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
 *
 * <p>識別子は採番せず事実から導く（{@link AttentionItemId}）。導出を各 BC に写すと、
 * 同じ表に書く値なのに片方だけ直せてしまう（IT4 R.2 で実際に区切り文字が食い違っていた）。</p>
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
                AttentionItemId.of(kind, targetType, targetId, reason).value(),
                kind, targetType, targetId,
                assignedRole, reason, payloadJson, occurredAt, null, null));
    }
}
