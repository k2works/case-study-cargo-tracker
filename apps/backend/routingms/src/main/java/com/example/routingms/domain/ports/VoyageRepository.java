package com.example.routingms.domain.ports;

import com.example.routingms.domain.model.aggregates.Voyage;
import com.example.routingms.domain.model.valueobjects.VoyageNumber;

import java.util.List;
import java.util.Optional;

/**
 * 航海リポジトリのドメインポート（インターフェース）
 * ヘキサゴナルアーキテクチャにおける出力ポート
 */
public interface VoyageRepository {

    /**
     * Voyage を保存する（新規作成）
     *
     * @param voyage 保存する Voyage
     * @return 保存後の Voyage（id 付き）
     */
    Voyage save(Voyage voyage);

    /**
     * Voyage を更新する
     *
     * @param voyage 更新する Voyage
     */
    void update(Voyage voyage);

    /**
     * 航海番号で Voyage を検索する
     *
     * @param voyageNumber 航海番号
     * @return Voyage（存在しない場合は空）
     */
    Optional<Voyage> findByVoyageNumber(VoyageNumber voyageNumber);

    /**
     * すべての Voyage を取得する
     *
     * @return Voyage リスト
     */
    List<Voyage> findAll();

    /**
     * 航海番号で Voyage を削除する
     *
     * @param voyageNumber 航海番号
     */
    void deleteByVoyageNumber(VoyageNumber voyageNumber);
}
