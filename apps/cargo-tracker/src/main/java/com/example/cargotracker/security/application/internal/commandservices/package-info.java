/**
 * 認証・認可の支援サブドメインのユースケース（更新系）。
 *
 * <p>ロック解除は<strong>理由を必須</strong>とし、監査ログに残す。
 * 誰がなぜ解除したかを追えないと、ログは「解除された」事実しか残さない。
 */
package com.example.cargotracker.security.application.internal.commandservices;
