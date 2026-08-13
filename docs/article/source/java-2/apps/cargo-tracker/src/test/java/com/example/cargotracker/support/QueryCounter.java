package com.example.cargotracker.support;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

/**
 * SQL の実行回数を数える（N+1 を判別するため）。
 *
 * <p><strong>時間で測らない。</strong> 経過時間のアサートは、遅いマシンでは偽陽性、
 * 速いマシンでは<strong>N+1 を残したままでも緑になる</strong>。
 * 「何回問い合わせたか」は実装の形をそのまま表す。
 *
 * <p>本クラスは<strong>テスト専用</strong>である。本番の構成には載せない。
 */
@Intercepts({
    @Signature(type = Executor.class, method = "query",
            args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
    @Signature(type = Executor.class, method = "update",
            args = {MappedStatement.class, Object.class}),
})
public class QueryCounter implements Interceptor {

    private final AtomicInteger count = new AtomicInteger();

    /**
     * 書き込みの回数（US23）。
     *
     * <p><strong>読み取りと分けて数える。</strong> 「件数に比例しない」だけでは、
     * 毎回すべての行を UPDATE する実装を判別できない。
     */
    private final AtomicInteger updateCount = new AtomicInteger();

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        count.incrementAndGet();
        if ("update".equals(invocation.getMethod().getName())) {
            updateCount.incrementAndGet();
        }
        return invocation.proceed();
    }

    @Override
    public void setProperties(Properties properties) {
        // 設定は使わない
    }

    /** 数え直す。**測る前に必ず呼ぶ。** */
    public void reset() {
        count.set(0);
        updateCount.set(0);
    }

    /** 数えた回数（読み書きの合計）。 */
    public int count() {
        return count.get();
    }

    /** 数えた書き込みの回数。 */
    public int updateCount() {
        return updateCount.get();
    }
}
