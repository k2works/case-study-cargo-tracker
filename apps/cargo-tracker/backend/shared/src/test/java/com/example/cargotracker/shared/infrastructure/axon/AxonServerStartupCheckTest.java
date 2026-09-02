package com.example.cargotracker.shared.infrastructure.axon;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.cargotracker.shared.infrastructure.axon.AxonServerStartupCheck.AxonServerUnavailableException;
import io.axoniq.axonserver.connector.AxonServerConnection;
import io.axoniq.axonserver.connector.event.DcbEventChannel;
import io.axoniq.axonserver.grpc.event.dcb.GetHeadResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 起動時接続検査が「働くこと」を分岐ごとに固定する。
 *
 * <p>実環境で DCB を無効にすると接続そのものが確立しないため、DCB 判定の分岐は
 * 実環境の実験では踏めない（IT1 タスク 1.4 で実測）。踏まない守りは、壊れても
 * 誰も気づかないので、ここで分岐を直接固定する。</p>
 */
class AxonServerStartupCheckTest {

    private static final Duration SHORT = Duration.ofMillis(300);

    private static AxonServerConnectionManager managerReturning(AxonServerConnection connection) {
        AxonServerConnectionManager manager = mock(AxonServerConnectionManager.class);
        when(manager.getDefaultContext()).thenReturn("default");
        when(manager.getConnection("default")).thenReturn(connection);
        return manager;
    }

    @Test
    @DisplayName("接続できないと起動を止める")
    void failsWhenNeverConnects() {
        AxonServerConnection connection = mock(AxonServerConnection.class);
        when(connection.isConnected()).thenReturn(false);

        assertThatThrownBy(() -> new AxonServerStartupCheck(managerReturning(connection), SHORT).run(null))
                .isInstanceOf(AxonServerUnavailableException.class)
                .hasMessageContaining("接続できなかった");
    }

    @Test
    @DisplayName("接続できても context が DCB でなければ起動を止める")
    void failsWhenContextIsNotDcb() {
        AxonServerConnection connection = mock(AxonServerConnection.class);
        when(connection.isConnected()).thenReturn(true);

        DcbEventChannel channel = mock(DcbEventChannel.class);
        when(channel.head()).thenReturn(
                CompletableFuture.failedFuture(new IllegalStateException("context is not DCB")));
        when(connection.dcbEventChannel()).thenReturn(channel);

        assertThatThrownBy(() -> new AxonServerStartupCheck(managerReturning(connection), SHORT).run(null))
                .isInstanceOf(AxonServerUnavailableException.class)
                .hasMessageContaining("DCB ではない")
                .hasMessageContaining("AXONIQ_AXONSERVER_STANDALONE_DCB=true");
    }

    @Test
    @DisplayName("接続でき context が DCB なら起動を通す")
    void passesWhenConnectedToDcbContext() {
        AxonServerConnection connection = mock(AxonServerConnection.class);
        when(connection.isConnected()).thenReturn(true);

        DcbEventChannel channel = mock(DcbEventChannel.class);
        when(channel.head()).thenReturn(
                CompletableFuture.completedFuture(GetHeadResponse.newBuilder().build()));
        when(connection.dcbEventChannel()).thenReturn(channel);

        assertThatCode(() -> new AxonServerStartupCheck(managerReturning(connection), SHORT).run(null))
                .doesNotThrowAnyException();
    }
}
