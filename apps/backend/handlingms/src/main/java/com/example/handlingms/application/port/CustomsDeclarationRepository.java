package com.example.handlingms.application.port;

import com.example.handlingms.domain.model.CargoBookingId;
import com.example.handlingms.domain.model.CustomsDeclaration;
import com.example.handlingms.domain.model.CustomsStatus;
import com.example.handlingms.domain.model.HandlingTrackingNumber;
import java.util.List;
import java.util.Optional;

/** 通関申告の保存先（出力ポート）。 */
public interface CustomsDeclarationRepository {

    /**
     * 新しい申告を保存する。
     *
     * <p><strong>更新とは別のメソッドにする。</strong>「常に INSERT する save」で更新まで
     * 賄うと、最初の更新のときに行が増える（過去 take の教訓）。
     */
    CustomsDeclaration save(CustomsDeclaration declaration);

    /**
     * 状態の更新を書き込む。履歴の新しい行も同じ呼び出しで積む。
     *
     * <p><strong>同じトランザクションで書く。</strong>別々になると、状態は変わったのに
     * 履歴に出ない行ができ、監査で「誰が変えたか」が読めない。
     */
    CustomsDeclaration updateStatus(CustomsDeclaration declaration);

    Optional<CustomsDeclaration> findById(long declarationId);

    /**
     * その貨物の決着していない申告（[ADR-025] 決定 7）。
     *
     * <p><strong>高々 1 件である。</strong>登録側がこれで 2 通目を断るため、
     * 「最新の 1 件」を暗黙に選ぶ必要がない。
     */
    Optional<CustomsDeclaration> findUnsettledByTrackingNumber(
            HandlingTrackingNumber trackingNumber);

    /** 引取のガードが引く（US29-3）。**通関済の申告があるか**を見る。 */
    Optional<CustomsDeclaration> findLatestByBookingId(CargoBookingId cargoBookingId);

    /**
     * 一覧・検索（US29-7）。条件は null で「指定なし」を表す。
     *
     * <p>{@code unsettledOnly} は<strong>未決着（審査中・留置）だけ</strong>に絞る。
     * 追跡管理者の朝の仕事は「未決着を上から片付ける」ことだが、状態の絞り込みは
     * 単一選択のため、この 2 つを同時に見る手段が要る。
     */
    List<CustomsDeclaration> search(String bookingId, String trackingNumber,
            CustomsStatus status, boolean unsettledOnly, int limit);

    /**
     * 同じ条件に合う<strong>総件数</strong>。
     *
     * <p><strong>上限で切ったことを黙らない。</strong>件数を知らせずに切ると、
     * 担当者は「一覧に出ていないから無い」と読む。
     */
    long count(String bookingId, String trackingNumber, CustomsStatus status,
            boolean unsettledOnly);
}
