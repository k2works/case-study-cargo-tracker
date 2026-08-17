package com.example.cargotracker.demo;

import java.time.Instant;

/**
 * デモモードが起こした出来事ひとつ。
 *
 * <p><strong>担当を必ず持つ。</strong> 画面が勝手に変わるだけでは、利用者は
 * <strong>自分のどの画面を見ればそれが起きているのか</strong>分からない。
 * 「荷役作業員が船に積み込んだ」と分かって初めて、荷役管理を開く気になる。
 *
 * @param at             起きた時刻
 * @param what           何が起きたか
 * @param actor          誰の仕事か（開始・停止のときは空）
 * @param trackingNumber 対象の追跡番号（まだ発行前なら {@code null}）
 * @param shipperName    荷主の名前（まだ登録前なら {@code null}）
 */
record DemoModeEvent(
        Instant at, String what, String actor, String trackingNumber, String shipperName) {
}
