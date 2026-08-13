package com.example.cargotracker.handling.application.internal.queryservices;

import com.example.cargotracker.handling.domain.model.valueobjects.CustomsStatusChange;
import java.util.List;
import java.util.Optional;

/** 通関申告の読み取り（US29。CQRS のクエリ側）。 */
public interface CustomsQueryService {

    /**
     * 申告を検索する（受入基準「貨物 ID・追跡番号・通関状態で検索できる」）。
     *
     * <p><strong>並び順は「留置が長引いているものを先に、申告の新しい順」。</strong>
     * この一覧は「放置するとコストが発生する仕事の待ち行列」であり、
     * 片づいたものが上に来ると、いま何をすべきかが読めない。
     *
     * @param keyword 追跡番号・申告番号・予約 ID の部分一致。空なら絞らない
     * @param status  通関状態の列挙子名。空なら絞らない
     */
    List<CustomsDeclarationView> search(String keyword, String status);

    /**
     * 留置が長引いている申告だけに絞る（C33）。
     *
     * <p><strong>数えた対象にそのまま行けるようにする。</strong> ダッシュボードの
     * カードは「3 日を超えた申告」の件数を出す。留置の全件へ飛ばすと、
     * カードが 2 件と言い、開くと 20 件並ぶ。
     *
     * @param heldDays 留置が続いた日数の下限。{@code null} なら絞らない
     */
    List<CustomsDeclarationView> search(String keyword, String status, Integer heldDays);

    Optional<CustomsDeclarationView> findById(long declarationId);

    /** 変更履歴を古い順で返す（申告詳細）。 */
    List<CustomsStatusChange> findHistory(long declarationId);

    /** 留置が長引いている申告の件数（ダッシュボードのカード）。 */
    int countHeldTooLong();
}
