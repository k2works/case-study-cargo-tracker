package com.example.bookingms.application.port;

import com.example.bookingms.domain.model.Estimate;
import java.util.List;
import java.util.Optional;

/** 見積の永続化（出力ポート・US01）。 */
public interface EstimateRepository {

    /**
     * 見積を保存する。
     *
     * <p><strong>候補ごと保存する。</strong>見積だけ保存して候補を落とすと、
     * 開き直したときに「候補が 0 件の見積」になる——荷主に出した数字が消える。
     */
    void save(Estimate estimate);

    /** 識別子（UUID）から引く。 */
    Optional<Estimate> findById(String estimateId);

    /** 見積番号から引く（荷主が電話で読み上げた番号で探す）。 */
    Optional<Estimate> findByNumber(String estimateNumber);

    /** 一覧。**新しい順**——直近に作ったものから見る。 */
    List<Estimate> findAll();

    /** 見積番号を採番する（[ADR-011] と同じ形）。 */
    com.example.bookingms.domain.model.EstimateNumber nextNumber();
}
