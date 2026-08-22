package com.example.bookingms.application.port;

/**
 * 経路候補を確認できなかった（[ADR-019]）。
 *
 * <p><strong>「確認できなかった」と「候補に無かった」は違う。</strong>相手が応答しないことを
 * 「候補に無い」と扱うと、呼び出し側は「航海スケジュールが変わった」と誤診し、経路設計者は
 * 何度探し直しても直らない作業に入る。
 *
 * <p>これは入力の誤り（400）でも状態の不一致（409）でもない。相手の不調であり、
 * 利用者にできるのは時間をおいて試すことだけである。
 */
public class RouteCandidateUnavailableException extends RuntimeException {

    public RouteCandidateUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
