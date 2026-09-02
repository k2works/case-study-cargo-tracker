package com.example.simulationms.application.internal.outboundservices.acl;

/**
 * 業務 API の呼び出しが失敗した。
 *
 * <p><strong>理由を必ず持つ。</strong>US35 が求めるのは「どこで止まったか」だけでなく
 * 「なぜ止まったか」である。応答コードとメッセージをそのまま運ぶ。
 */
public class BusinessCallFailedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public BusinessCallFailedException(String message) {
        super(message);
    }

    public BusinessCallFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
