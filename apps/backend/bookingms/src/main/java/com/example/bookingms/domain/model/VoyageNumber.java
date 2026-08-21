package com.example.bookingms.domain.model;

/**
 * 航海番号。
 *
 * <p><strong>Routing Context にも同名の型があるが、別の型である。</strong>共有カーネルへは
 * 引き上げない（`architecture_backend.md` が「各コンテキスト固有型として定義し、共有しない」と
 * 決めている）。同じ名前のまま持つのは、<strong>指すものが同じだから</strong>である
 * （IT4 の {@code RouteSpecification} は名前が同じで意味が違ったため改名した）。
 * 取り違えは ArchUnit の BC 分離ルールが弾く。相手の型はここへ持ち込まず、ACL で変換する。
 *
 * @param value 航海番号の文字列
 */
public record VoyageNumber(String value) {

    /** 新規に組み立てる。ここでだけ検査する。 */
    public static VoyageNumber of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("航海番号は必須です");
        }
        return new VoyageNumber(value.trim());
    }

    /** 永続化された行から復元する。ここでは検査しない。 */
    public static VoyageNumber restore(String value) {
        return value == null ? null : new VoyageNumber(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
