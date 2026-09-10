package com.example.cargotracker.shared.infrastructure.axon;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import javax.sql.DataSource;
import org.axonframework.common.function.ThrowingFunction;
import org.axonframework.common.tx.TransactionalExecutor;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.transaction.TransactionalExecutorProvider;

/**
 * 進行中の処理とは別の接続で SQL を回す。
 *
 * <p><b>退避先の書き込みは、失敗した処理と同じトランザクションでは書けない。</b>
 * PostgreSQL は 1 つの文が落ちるとトランザクション全体を中断し、以降の文を
 * {@code current transaction is aborted} で拒む。Axon の
 * {@code JdbcTransactionalExecutorProvider} は処理中の接続を使い回すので、
 * <b>退避しようとした瞬間にその接続はもう死んでいる</b>（実測）。</p>
 *
 * <p>要確認一覧を別トランザクションで書くのと同じ理由である（{@code AttentionItemRecorder}
 * の {@code REQUIRES_NEW}）。落ちた処理は巻き戻ってよいが、<b>落ちた事実は残す</b>。</p>
 */
public final class OwnConnectionExecutorProvider
        implements TransactionalExecutorProvider<Connection> {

    private final DataSource dataSource;

    public OwnConnectionExecutorProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public TransactionalExecutor<Connection> getTransactionalExecutor(ProcessingContext context) {
        return new TransactionalExecutor<>() {
            @Override
            public <R> CompletableFuture<R> apply(
                    ThrowingFunction<Connection, R, Exception> action) {
                // try-with-resources を使わない。生まれる「閉じるときに例外が
                // 出ていたか」の判定は、テストから踏み分けられない合成の枝で、
                // 網羅率の穴として残り続ける。閉じるのは自分で書く。
                Connection connection = null;
                try {
                    connection = dataSource.getConnection();
                    return inTransaction(connection, action);
                } catch (Exception failure) {
                    return CompletableFuture.failedFuture(failure);
                } finally {
                    close(connection);
                }
            }

            /**
             * 1 つの接続の中で、コミットまで済ませる。
             *
             * <p><b>自動コミットは元に戻す。</b> 接続はプールへ返るので、
             * 戻し忘れると次に借りた誰かが「コミットしない接続」を使う。</p>
             */
            private <R> CompletableFuture<R> inTransaction(Connection connection,
                    ThrowingFunction<Connection, R, Exception> action) throws SQLException {
                boolean autoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);
                try {
                    R result = action.apply(connection);
                    connection.commit();
                    return CompletableFuture.completedFuture(result);
                } catch (Exception failure) {
                    connection.rollback();
                    return CompletableFuture.failedFuture(failure);
                } finally {
                    connection.setAutoCommit(autoCommit);
                }
            }

            /**
             * 接続を返す。<b>閉じられなくても、そこで例外を投げ直さない。</b>
             * 投げると、退避が失敗した本当の理由が「閉じられなかった」に化ける。
             */
            private void close(Connection connection) {
                if (connection == null) {
                    return;
                }
                try {
                    connection.close();
                } catch (Exception ignored) {
                    // プールへ返せなかった。退避の成否とは別の話なので、
                    // ここで結果を書き換えない。
                }
            }
        };
    }
}
