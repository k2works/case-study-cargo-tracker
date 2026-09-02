package com.example.handlingms.interfaces.rest;

/** 通関申告 API が受け取る形。 */
public final class CustomsRequests {

    private CustomsRequests() {
    }

    /**
     * 申告の登録（US29-1）。
     *
     * <p><strong>状態を受け取らない。</strong>初期状態は集約が決める——登録の時点で
     * 通関済を選べると、引取のガードが最初から素通りになる。
     */
    public record RegisterCustomsDeclarationRequest(String trackingNumber,
            String declarationNumber, String declaredAt, String remarks) {
    }

    /** 状態の更新（US29-2）。**理由は必須**であり、集約が断る。 */
    public record UpdateCustomsStatusRequest(String status, String reason) {
    }
}
