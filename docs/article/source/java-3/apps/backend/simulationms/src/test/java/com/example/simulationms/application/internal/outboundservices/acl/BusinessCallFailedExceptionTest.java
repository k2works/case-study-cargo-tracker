package com.example.simulationms.application.internal.outboundservices.acl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 業務 API の失敗は、理由を必ず持つ（US35-2・[ADR-030] 決定 5）。
 *
 * <p>「失敗しました」だけでは、経路候補が 0 件なのか、サービス間の設定が違うのか、
 * 通関で止まっているのかを切り分けられない。原因の切り分けに結局手作業が要る。
 */
@DisplayName("業務 API の失敗")
class BusinessCallFailedExceptionTest {

    @Test
    @DisplayName("応答の理由をそのまま運ぶ")
    void carriesTheReason() {
        BusinessCallFailedException failure =
                new BusinessCallFailedException("409 通関が完了していません");

        assertThat(failure).hasMessage("409 通関が完了していません");
        assertThat(failure.getCause()).isNull();
    }

    @Test
    @DisplayName("接続そのものに失敗したときは、原因の例外も残す")
    void keepsTheUnderlyingCause() {
        IOException cause = new IOException("Connection refused");

        BusinessCallFailedException failure =
                new BusinessCallFailedException("gatewayms へ接続できません", cause);

        assertThat(failure).hasMessage("gatewayms へ接続できません").hasCause(cause);
    }
}
