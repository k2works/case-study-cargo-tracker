package com.example.cargotracker.shared.infrastructure.axon;

import io.axoniq.axonserver.connector.AxonServerConnection;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * 起動時に Axon Server への接続と、context が DCB であることを検査する。
 *
 * <p>なぜ要るか。Axon Server に繋がらないとき、また context が DCB でないとき、
 * アプリケーションは<b>起動を止めずに無限に再接続を試み続ける</b>（IT1 スパイクで実測。
 * 2026.0.4 では {@code AXONIQ-1302 default: not found in any replication group}）。
 * 起動が成功してしまうので、投影が永久に進まないことに誰も気づかない。</p>
 *
 * <p>判定はログの検出ではなく context への問い合わせで行う。ログの文言は版で変わるうえ、
 * 「出なかった」ことを検査にできないため（[ADR-0001] 決定 5 の第 6 項目）。</p>
 */
public class AxonServerStartupCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AxonServerStartupCheck.class);

    private final AxonServerConnectionManager connectionManager;
    private final Duration timeout;

    public AxonServerStartupCheck(AxonServerConnectionManager connectionManager, Duration timeout) {
        this.connectionManager = connectionManager;
        this.timeout = timeout;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String context = connectionManager.getDefaultContext();
        AxonServerConnection connection = connectionManager.getConnection(context);

        awaitConnected(connection, context);
        verifyDcb(connection, context);

        log.info("Axon Server の接続と DCB を確認した（context={}）", context);
    }

    private void awaitConnected(AxonServerConnection connection, String context) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (connection.isConnected()) {
                return;
            }
            try {
                Thread.sleep(200L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AxonServerUnavailableException("接続の確認が中断された（context=" + context + "）", e);
            }
        }
        throw new AxonServerUnavailableException(
                "Axon Server に " + timeout.toSeconds() + " 秒以内に接続できなかった（context=" + context
                        + "）。接続できないまま起動すると、投影が永久に進まないことに気づけない");
    }

    /**
     * DCB 専用の読み取り呼び出しを 1 回だけ行う。context が DCB でなければここで失敗する。
     * 書き込みではないので Event Store を汚さない。
     */
    private void verifyDcb(AxonServerConnection connection, String context) {
        try {
            connection.dcbEventChannel().head().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AxonServerUnavailableException("DCB の確認が中断された（context=" + context + "）", e);
        } catch (Exception e) {
            throw new AxonServerUnavailableException(
                    "context '" + context + "' が DCB ではない。Axon Server に "
                            + "AXONIQ_AXONSERVER_STANDALONE_DCB=true（クラスタは dcb=true）が要る。"
                            + "@EventSourced(tagKey) は DCB 前提のため、無いと集約を保存できない", e);
        }
    }

    /** 起動を止めるための例外。握りつぶさない。 */
    public static class AxonServerUnavailableException extends IllegalStateException {

        private static final long serialVersionUID = 1L;

        public AxonServerUnavailableException(String message) {
            super(message);
        }

        public AxonServerUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
