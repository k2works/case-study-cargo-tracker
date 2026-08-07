/**
 * 予約コンテキストのユースケース（更新系）。
 *
 * <p>集約をまたぐ確認（荷主の存在・港マスタの照合）は<strong>ここで行う</strong>。
 * 集約の中で確認しようとすると BC 間の直接参照になる。
 */
package com.example.cargotracker.booking.application.internal.commandservices;
