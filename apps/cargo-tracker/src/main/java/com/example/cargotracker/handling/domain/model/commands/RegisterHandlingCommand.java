package com.example.cargotracker.handling.domain.model.commands;
import com.example.cargotracker.handling.domain.model.valueobjects.HandledCargo;
import com.example.cargotracker.handling.domain.model.valueobjects.HandlingDetails;

import com.example.cargotracker.shared.domain.model.valueobjects.Location;
import java.time.Instant;

/**
 * 荷役作業の登録コマンド（US15 / US16）。
 *
 * <p>追跡番号から予約を引き当てるのはアプリケーション層の仕事である。
 * <strong>その結果である予約 ID と、読み取った番号そのものの両方を持つ。</strong>
 *
 * <p><strong>IT7 で判断を変えた。</strong> IT6 は「集約が知る必要があるのは
 * 『どの予約に対する作業か』だけである」として追跡番号を持たせていなかったが、
 * 2 つの点でそれは狭かった。
 *
 * <ol>
 *   <li><strong>読み取った番号は作業そのものの事実である。</strong> 予約 ID から
 *       逆算して表示すると、誤読した場合にその痕跡が消える</li>
 *   <li>作業員が手にしているのは追跡番号だけであり、予約 ID は紙にもラベルにも無い
 *       （IT6 レビュー H12）</li>
 * </ol>
 *
 * @param cargo          作業の対象となった貨物（読み取った番号と引き当てた予約のひと組）
 * @param details        荷役種別と、その種別に応じて要る詳細のひと組
 * @param completionTime 作業日時
 * @param location       作業場所
 * @param note           担当者メモ（任意）。代理受領の理由などを残す
 * @param operatorName   作業員名（任意）
 */
public record RegisterHandlingCommand(
        HandledCargo cargo,
        HandlingDetails details,
        Instant completionTime,
        Location location,
        String note,
        String operatorName) {
}
