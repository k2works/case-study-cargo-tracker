package com.example.authms.interfaces.rest;

/**
 * 認証 API の応答。
 *
 * <p>成功と失敗で本文の形が違う。共通の型を与えることで、応答の種類が 2 つに閉じていることを
 * コンパイラが保証する（ワイルドカードで受けると、あとから別の形を返しても誰も気づかない）。
 */
public sealed interface AuthenticationResponse permits LoginResponse, ErrorResponse {
}
