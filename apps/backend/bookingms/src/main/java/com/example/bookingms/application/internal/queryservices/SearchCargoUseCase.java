package com.example.bookingms.application.internal.queryservices;

import com.example.bookingms.domain.repository.CargoRepository;
import com.example.bookingms.domain.repository.CargoSummary;
import com.example.bookingms.domain.model.valueobjects.BookingStatus;
import com.example.bookingms.domain.model.valueobjects.CargoType;
import com.example.bookingms.domain.model.valueobjects.RoutingStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 貨物予約を探す。
 *
 * <p>件数の上限を必ず置く。上限が無いと、予約が増えた日に一覧が開かなくなる。
 * 上限で切ったことは総件数と合わせて示す（黙って切ると「全件見た」と受け取られる）。
 */
@Service
public class SearchCargoUseCase {

    /** 一覧に返す件数の上限。 */
    public static final int DEFAULT_LIMIT = 100;

    private final CargoRepository cargoes;

    public SearchCargoUseCase(CargoRepository cargoes) {
        this.cargoes = cargoes;
    }

    public Result search(CargoType type, String keyword) {
        return search(type, keyword, null, null);
    }

    /**
     * 経路の状況でも絞れる形。
     *
     * <p>「経路設計待ち」の件数を出すだけでは仕事は進まない。そこから対象の一覧へ
     * 行けるようにするため、同じ条件で絞った一覧を返せるようにする。
     */
    public Result search(CargoType type, String keyword,
            Collection<RoutingStatus> routingStatuses, BookingStatus bookingStatus) {
        List<CargoSummary> found = cargoes.search(type, keyword, routingStatuses, bookingStatus,
                DEFAULT_LIMIT);
        return new Result(found,
                cargoes.count(type, keyword, routingStatuses, bookingStatus), DEFAULT_LIMIT);
    }

    /**
     * @param cargoes 上限で切った一覧（新しい順）
     * @param totalCount 絞り込み条件に合う総件数
     * @param limit 適用した上限
     */
    public record Result(List<CargoSummary> cargoes, long totalCount, int limit) {

        /** 上限で切られているか。画面が「全件ではない」ことを示せるようにする。 */
        public boolean truncated() {
            return totalCount > cargoes.size();
        }
    }
}
