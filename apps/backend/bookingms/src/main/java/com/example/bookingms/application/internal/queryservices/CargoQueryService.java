package com.example.bookingms.application.internal.queryservices;

import com.example.bookingms.domain.model.aggregates.Cargo;
import com.example.bookingms.domain.model.valueobjects.BookingId;
import com.example.bookingms.domain.ports.CargoRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 貨物クエリサービス（一覧・詳細検索）
 */
@Service
public class CargoQueryService {

    private final CargoRepository cargoRepository;

    public CargoQueryService(CargoRepository cargoRepository) {
        this.cargoRepository = cargoRepository;
    }

    /**
     * すべての貨物を取得する
     *
     * @return 貨物リスト
     */
    public List<Cargo> findAll() {
        return cargoRepository.findAll();
    }

    /**
     * 予約 ID で貨物を検索する
     *
     * @param bookingId 予約 ID
     * @return 貨物（存在しない場合は空）
     */
    public Optional<Cargo> findByBookingId(String bookingId) {
        return cargoRepository.findByBookingId(new BookingId(bookingId));
    }
}
