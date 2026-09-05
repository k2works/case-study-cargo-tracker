package com.example.cargotracker.routing.interfaces.rest;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.error.IllegalTransition;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 問い合わせの送り口。<b>業務の断りを 500 に化けさせない。</b>
 *
 * <p>各 Controller が {@code try / catch (Exception)} を持つと、問い合わせ側が業務の判断で
 * 断ったことまで {@code IllegalStateException} に包まれ、画面には 500 が出る。利用者は
 * 「壊れた」のか「入力が悪い」のかを判断できない（IT4 R.3）。同じ包み方が Controller の
 * 数だけ書かれていたので、ここへ寄せる（IT4 R.2）。</p>
 *
 * <p><b>型で見分けない。</b> サービスを越えた例外は根の型が置き換わるので、種類は文言の
 * 印で運ぶ（ADR-0001 決定 5 第 12 項。{@link ApiExceptionHandler} と同じ判断）。</p>
 */
@Component
public class QueryDispatcher {

    private static final long TIMEOUT_SECONDS = 5;

    /** 問い合わせの送出。テストから差し替えられるように切り出す。 */
    @FunctionalInterface
    interface Gateway {
        CompletableFuture<?> query(Object query, Class<?> responseType);
    }

    private final Gateway gateway;

    // コンストラクタが 2 つあるので、どちらで組み立てるかを明示する。
    // 明示しないと既定コンストラクタを探しに行き、起動時に落ちる。
    @Autowired
    public QueryDispatcher(QueryGateway queryGateway) {
        this(queryGateway::query);
    }

    QueryDispatcher(Gateway gateway) {
        this.gateway = gateway;
    }

    public <T> T query(Object query, Class<T> responseType) {
        try {
            return responseType.cast(
                    gateway.query(query, responseType).get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            // 飲み込むと上位が止まれない。
            Thread.currentThread().interrupt();
            throw new IllegalStateException("問い合わせが中断されました", e);
        } catch (Exception e) {
            throw translated(e);
        }
    }

    /**
     * 連鎖のどこかに印があれば、その業務の断りとして返す。
     *
     * <p>包みは 2 枚以上になることがあるので、直下の cause だけを見ない（IT3 で 409 が
     * 422 に化けたのと同じ形）。印が無いものは障害なので包んだままにする。障害を業務の
     * 断りに化けさせると、原因が残らない。</p>
     */
    private static RuntimeException translated(Exception failure) {
        String transition = messageWith(failure, IllegalTransition.MARKER);
        if (transition != null) {
            return new IllegalTransition(BusinessRuleViolation.strip(transition));
        }
        String businessRule = messageWith(failure, BusinessRuleViolation.MARKER);
        if (businessRule != null) {
            return new BusinessRuleViolation(BusinessRuleViolation.strip(businessRule));
        }
        return new IllegalStateException("問い合わせに失敗しました", failure);
    }

    private static String messageWith(Throwable throwable, String marker) {
        for (Throwable t = throwable; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains(marker)) {
                return t.getMessage();
            }
        }
        return null;
    }
}
