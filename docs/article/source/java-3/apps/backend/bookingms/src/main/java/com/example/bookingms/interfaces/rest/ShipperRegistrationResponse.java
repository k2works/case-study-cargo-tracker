package com.example.bookingms.interfaces.rest;

/**
 * 荷主登録の応答。
 *
 * <p>登録できた場合と、同じメールアドレスの荷主が既にある場合で本文の形が違う。
 * 共通の型を与えることで、応答の種類が 2 つに閉じていることをコンパイラが保証する
 * （ワイルドカードで受けると、あとから別の形を返しても誰も気づかない）。
 */
public sealed interface ShipperRegistrationResponse
        permits ShipperResponse, DuplicateShipperResponse {
}
