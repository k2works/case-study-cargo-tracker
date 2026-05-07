package com.example.routingms.application.service;

import com.example.routingms.domain.model.aggregates.Voyage;
import com.example.routingms.domain.model.valueobjects.VoyageNumber;
import com.example.routingms.domain.ports.VoyageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 航海の検索・一覧取得を担うアプリケーションサービス（CQRS クエリ側）
 */
@Service
@Transactional(readOnly = true)
public class VoyageQueryService {

    private final VoyageRepository voyageRepository;

    public VoyageQueryService(VoyageRepository voyageRepository) {
        this.voyageRepository = voyageRepository;
    }

    /**
     * すべての Voyage を取得する
     *
     * @return Voyage リスト
     */
    public List<Voyage> findAll() {
        return voyageRepository.findAll();
    }

    /**
     * 航海番号で Voyage を検索する
     *
     * @param voyageNumber 航海番号
     * @return Voyage（存在しない場合は空）
     */
    public Optional<Voyage> findByVoyageNumber(VoyageNumber voyageNumber) {
        return voyageRepository.findByVoyageNumber(voyageNumber);
    }
}
