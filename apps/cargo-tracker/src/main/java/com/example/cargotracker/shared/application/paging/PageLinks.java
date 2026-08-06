package com.example.cargotracker.shared.application.paging;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ページ送りのリンクに引き継ぐ絞り込み条件。
 *
 * <p><strong>テンプレートで文字列を連結しない。</strong> 荷主名に {@code &} や空白が
 * 入っただけで「次へ」のリンクが壊れ、**2 ページ目に行くと検索条件が消える**。
 * エンコードを 1 か所に集める。
 */
public final class PageLinks {

    private final Map<String, String> parameters = new LinkedHashMap<>();

    /** 値が空でないパラメータだけを引き継ぐ。 */
    public PageLinks with(String name, String value) {
        if (value != null && !value.isBlank()) {
            parameters.put(name, value);
        }
        return this;
    }

    /**
     * {@code page} を除いたクエリ文字列。末尾に {@code &} を付けて返す。
     *
     * @return 例: {@code "keyword=%E5%B1%B1%E7%94%B0&"}。条件が無ければ空文字
     */
    public String queryPrefix() {
        if (parameters.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        parameters.forEach((k, v) -> sb
                .append(URLEncoder.encode(k, StandardCharsets.UTF_8))
                .append('=')
                .append(URLEncoder.encode(v, StandardCharsets.UTF_8))
                .append('&'));
        return sb.toString();
    }
}
