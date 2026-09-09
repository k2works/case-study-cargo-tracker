package com.example.cargotracker.shared.infrastructure.axon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 退避先の書き込みは、失敗した処理とは別の接続で行う（[ADR-0014] 決定 3）。
 *
 * <p>同じ接続を使い回すと、PostgreSQL は落ちた文のあと
 * {@code current transaction is aborted} で以降を全部拒む。<b>退避しようとした
 * 瞬間にその接続はもう死んでいる</b>ので、退避そのものが失敗する（実測）。</p>
 */
class OwnConnectionExecutorProviderTest {

    @Test
    @DisplayName("処理中の接続ではなく、DataSource から取り直す")
    void takesItsOwnConnection() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);

        new OwnConnectionExecutorProvider(dataSource)
                .getTransactionalExecutor(null)
                .apply(given -> given)
                .join();

        verify(dataSource).getConnection();
    }

    @Test
    @DisplayName("成功したら確定する")
    void commitsOnSuccess() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);

        Object result = new OwnConnectionExecutorProvider(dataSource)
                .getTransactionalExecutor(null)
                .apply(given -> "書けた")
                .join();

        assertThat(result).isEqualTo("書けた");
        verify(connection).commit();
        verify(connection, never()).rollback();
    }

    @Test
    @DisplayName("失敗したら巻き戻し、失敗として返す（黙って握りつぶさない）")
    void rollsBackOnFailure() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);

        var outcome = new OwnConnectionExecutorProvider(dataSource)
                .getTransactionalExecutor(null)
                .apply(given -> {
                    throw new IllegalStateException("書けない");
                });

        assertThat(outcome).isCompletedExceptionally();
        verify(connection).rollback();
        verify(connection, never()).commit();
    }

    @Test
    @DisplayName("接続そのものが取れなければ、失敗として返す")
    void reportsWhenNoConnectionIsAvailable() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new java.sql.SQLException("接続できない"));

        var outcome = new OwnConnectionExecutorProvider(dataSource)
                .getTransactionalExecutor(null)
                .apply(given -> given);

        assertThat(outcome).isCompletedExceptionally();
        verify(dataSource).getConnection();
        verify(dataSource, never()).getConnection(any(), any());
    }
    @Test
    @DisplayName("確定そのものが落ちても、失敗として返す")
    void reportsWhenCommitFails() throws Exception {
        // **黙って成功にしない。** 確定できていないのに成功を返すと、
        // 退避したつもりのイベントがどこにも残らない。
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        org.mockito.Mockito.doThrow(new java.sql.SQLException("確定できない"))
                .when(connection).commit();

        var outcome = new OwnConnectionExecutorProvider(dataSource)
                .getTransactionalExecutor(null)
                .apply(given -> "書けた");

        assertThat(outcome).isCompletedExceptionally();
    }

    @Test
    @DisplayName("元の自動確定の設定に戻す（接続はプールへ返る）")
    void restoresAutoCommit() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);

        new OwnConnectionExecutorProvider(dataSource)
                .getTransactionalExecutor(null)
                .apply(given -> given)
                .join();

        verify(connection).setAutoCommit(false);
        verify(connection).setAutoCommit(true);
    }
    @Test
    @DisplayName("接続が返らなかったら失敗として返す（そこで例外を投げ散らかさない）")
    void reportsWhenTheDataSourceReturnsNothing() throws Exception {
        // try-with-resources が生む null 判定の枝である。ここを通っていないと、
        // プールが接続を返さなかったときに何が起きるか誰も確かめていないことになる。
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(null);

        var outcome = new OwnConnectionExecutorProvider(dataSource)
                .getTransactionalExecutor(null)
                .apply(given -> given);

        assertThat(outcome).isCompletedExceptionally();
    }
    @Test
    @DisplayName("接続の設定を触れなくても、接続は閉じて失敗として返す")
    void closesTheConnectionEvenWhenSetupFails() throws Exception {
        // **接続を返さないまま失敗しない。** プールは有限なので、漏らすと
        // 何度目かの退避で接続が枯れ、退避そのものができなくなる。
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenThrow(new java.sql.SQLException("触れない"));

        var outcome = new OwnConnectionExecutorProvider(dataSource)
                .getTransactionalExecutor(null)
                .apply(given -> given);

        assertThat(outcome).isCompletedExceptionally();
        verify(connection).close();
    }
}
