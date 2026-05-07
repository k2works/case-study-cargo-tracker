package com.example.routingms.application.service;

import com.example.routingms.domain.model.aggregates.Voyage;
import com.example.routingms.domain.model.valueobjects.Schedule;
import com.example.routingms.domain.model.valueobjects.VoyageNumber;
import com.example.routingms.domain.ports.VoyageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 航海の登録・更新・削除を担うアプリケーションサービス（CQRS コマンド側）
 */
@Service
@Transactional
public class VoyageCommandService {

    private final VoyageRepository voyageRepository;

    public VoyageCommandService(VoyageRepository voyageRepository) {
        this.voyageRepository = voyageRepository;
    }

    /**
     * 新規 Voyage を登録する
     *
     * @param voyage 登録する Voyage（id なし）
     * @return 登録後の Voyage（id 付き）
     * @throws IllegalStateException 同一航海番号が既に存在する場合
     */
    public Voyage register(Voyage voyage) {
        VoyageNumber voyageNumber = voyage.getVoyageNumber();
        voyageRepository.findByVoyageNumber(voyageNumber).ifPresent(existing -> {
            throw new IllegalStateException(
                    "Voyage already exists: " + voyageNumber.getNumber());
        });
        return voyageRepository.save(voyage);
    }

    /**
     * 既存 Voyage のスケジュールを更新する
     *
     * @param voyageNumber 更新対象の航海番号
     * @param newSchedule  新しいスケジュール
     * @return 更新後の Voyage
     * @throws IllegalArgumentException 指定した航海番号が存在しない場合
     */
    public Voyage updateSchedule(VoyageNumber voyageNumber, Schedule newSchedule) {
        Voyage voyage = voyageRepository.findByVoyageNumber(voyageNumber)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Voyage not found: " + voyageNumber.getNumber()));
        voyage.updateSchedule(newSchedule);
        voyageRepository.update(voyage);
        return voyage;
    }

    /**
     * Voyage を削除する
     *
     * @param voyageNumber 削除対象の航海番号
     * @throws IllegalArgumentException 指定した航海番号が存在しない場合
     */
    public void delete(VoyageNumber voyageNumber) {
        voyageRepository.findByVoyageNumber(voyageNumber)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Voyage not found: " + voyageNumber.getNumber()));
        voyageRepository.deleteByVoyageNumber(voyageNumber);
    }
}
