package com.example.cargotracker.handling.domain.model.commands;

import com.example.cargotracker.handling.domain.model.valueobjects.HandlingType;
import java.time.Instant;
import com.example.cargotracker.handling.domain.model.valueobjects.CustomsStatus;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 荷役作業を記録する（UC13 / US15）。
 *
 * <p><b>冪等キーはクライアントが作る</b>（{@code activityId}）。サーバが採ると、
 * 通信断で再送したときに別の鍵になって二重に記録される。現場は電波の届かない
 * 岸壁で使う。</p>
 *
 * <p><b>{@code offRoute} と {@code finalPort} は application 層が解決して載せる</b>
 * （不変条件 2）。Axon のコマンドハンドラは読み取りモデルを引数に取れないので、
 * {@code CargoSnapshot} を引ける層で判定してから渡す。<b>集約は載った値を信じきらず、
 * 必須項目と時刻だけは自分で検査する</b>。</p>
 *
 * @param consigneeName 荷受人の確認（署名または確認コード）。<b>引取のときだけ必須</b>で、
 *     それ以外の種別には載せない（載せたら断る——黙って捨てると、現場は確認を
 *     取ったつもりのまま記録が残らない）
 * @param completedAt 作業が終わった時刻。<b>過去は通し、未来は拒む</b>——通信不能時は
 *     紙に控えて後から入れる運用がある
 * @param customsStatus 判定に使った通関状態。{@code offRoute} と同じく application 層が
 *     解決して載せる（`domain-model.md`）。<b>申告が無ければ {@code null}</b>——
 *     「無い」と「審査中」は違う
 * @param customsStatusAsOf その通関状態を読んだ時点。<b>断るときに返す</b>ので、
 *     画面が「直近で変わった可能性があります」と再確認へ導ける
 */
public record RegisterHandlingActivityCommand(
        @TargetEntityId String activityId,
        String trackingNumber,
        String bookingId,
        HandlingType type,
        String unLocode,
        String voyageNumber,
        boolean offRoute,
        boolean finalPort,
        String consigneeName,
        CustomsStatus customsStatus,
        Instant customsStatusAsOf,
        String operator,
        Instant completedAt) {
}
