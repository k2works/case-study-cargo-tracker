package com.example.cargotracker.shared.domain.attention;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 要確認一覧（{@code attention_item}）の識別子。<b>採番せず、事実から導く。</b>
 *
 * <p>{@code attention_item} は追記専用で、投影を読み直しても消さない
 * （{@code data-model.md}）。{@code UUID.randomUUID()} で採番すると、読み直すたびに
 * 同じ内容の行が積み上がり、担当ロールが毎朝見る一覧が信用されなくなる（IT2 で実在した
 * 欠陥）。「何が・どの対象で・なぜ」が同じなら同じ行として扱う。</p>
 *
 * <p><b>ここに 1 本だけ置く。</b> IT2 の修正では導出が bookingms と routingms に 1 本ずつ
 * 書かれ、区切り文字が食い違ったまま（NUL とパイプ）残っていた。同じ表に同じ意味で書く
 * 値なので、片方だけ直せる場所に置くと、次に直した人が食い違いに気づけない（IT4 R.2）。</p>
 *
 * <p><b>UUID の見た目に整形しない。</b> 導出値をハイフンで区切って UUID に見せると、
 * 採番された値だと誤解した変更を招く（IT4 R.1）。</p>
 */
public record AttentionItemId(String value) {

    /**
     * 種を組み立てる区切り。本文に現れうる文字を使うと、要素の付け替え（"AB"+"C" と
     * "A"+"BC"）が同じ種になり、別々の事実が 1 行に潰れる。US（Unit Separator, U+001F）は
     * 業務の文字列に現れない。
     */
    private static final char SEPARATOR = 0x1f;

    /** {@code attention_item.item_id} は VARCHAR(36)。SHA-256 の先頭 128 ビットを使う。 */
    private static final int LENGTH = 32;

    public AttentionItemId {
        if (value == null || !value.matches("^[0-9a-f]{" + LENGTH + "}$")) {
            throw new BusinessRuleViolation("要確認一覧の識別子は 16 進 " + LENGTH + " 文字です: " + value);
        }
    }

    /** 「何が・どの対象で・なぜ」から導く。 */
    public static AttentionItemId of(String kind, String targetType, String targetId,
            String reason) {
        String seed = String.join(String.valueOf(SEPARATOR),
                required(kind, "kind"), required(targetType, "targetType"),
                required(targetId, "targetId"), required(reason, "reason"));
        return new AttentionItemId(digestOf(seed));
    }

    private static String required(String component, String name) {
        if (component == null || component.isBlank()) {
            // 空白を通すと、欠けた要素どうしの事実が同じ種になり 1 行に潰れる。
            throw new BusinessRuleViolation("要確認一覧の識別子には " + name + " が要ります");
        }
        return component;
    }

    private static String digestOf(String seed) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(seed.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 が使えません", e);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
