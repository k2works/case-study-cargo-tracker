package com.example.cargotracker.booking.domain.repository;

import com.example.cargotracker.booking.domain.model.BookingId;
import com.example.cargotracker.booking.domain.model.Cargo;
import java.util.Optional;
import javax.annotation.CheckReturnValue;

/**
 * 貨物予約の出力ポート。実装はインフラ層に置く（DIP）。
 *
 * <p><strong>楽観的ロックの結果を返すメソッドには {@link CheckReturnValue} を付ける。</strong>
 * IT6 では戻り値 {@code boolean} で衝突を知らせる 3 か所で結果を捨てており、
 * 衝突すると荷役だけが記録されて追跡も誤配も黙って落ちていた（レビュー H1）。
 *
 * <p>IT5 の Try「例外を投げる経路を {@code grep} で数える」は例外については守れていたが、
 * <strong>戻り値で結果を返す安全装置には同じ数え方が適用されていなかった</strong>
 * （IT6 ふりかえり P2）。<strong>規律はあったが適用範囲が狭かった。</strong>
 * 人が毎回 {@code grep} する運用に戻さず、SpotBugs に数えさせる。
 */
public interface CargoRepository {

    /** 新規登録する。 */
    void save(Cargo cargo);

    /**
     * 更新する。
     *
     * <p>楽観的ロックにより、読み取り時から version が変わっていれば更新しない。
     *
     * @return 更新できたなら {@code true}。他の更新が先行していたなら {@code false}
     */
    @CheckReturnValue
    boolean update(Cargo cargo);

    /**
     * 経路の割り当てを保存する（US09 / US11）。
     *
     * <p>経路状態と旅程を<strong>1 つの操作として書く</strong>。旅程は丸ごと
     * 入れ替える。
     *
     * @return 他の更新が先行していれば {@code false}（楽観的ロック）
     */
    @CheckReturnValue
    boolean updateRouting(Cargo cargo);

    /**
     * 追跡番号の発行を保存する（US14）。
     *
     * <p>予約状態と追跡番号を<strong>1 つの操作として書く</strong>。
     *
     * @return 他の更新が先行していれば {@code false}（楽観的ロック）
     */
    @CheckReturnValue
    boolean updateTrackingNumber(Cargo cargo);

    Optional<Cargo> findById(BookingId bookingId);

    /**
     * 追跡番号から引き当てる（US15 / US18）。
     *
     * <p>荷役作業員が手に持っているのは追跡番号だけである。
     */
    Optional<Cargo> findByTrackingNumber(String trackingNumber);
}
