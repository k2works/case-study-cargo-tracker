package com.example.cargotracker.routing.domain.model;

import com.example.cargotracker.shared.domain.model.Location;
import java.time.LocalDate;

/**
 * 経路探索の条件（US08 / US10）。
 *
 * <p><strong>当初の希望期限を保持する。</strong> US10 で期限を延ばして経路を確定した
 * 場合、荷主には「何日延びたか」を伝える必要がある（US12）。延長後の期限だけを
 * 持つと、延ばした事実そのものが消える。
 *
 * @param origin                  出発地。誤配の再設計では貨物の現在地が入る（US28）
 * @param destination             目的地
 * @param arrivalDeadline         希望到着期限。US10 で緩められる
 * @param originalArrivalDeadline 当初の希望到着期限
 * @param cargoType               貨物種別
 * @param weight                  重量
 * @param maxTransitCount         経由回数の上限
 */
public record RoutingCriteria(
        Location origin,
        Location destination,
        LocalDate arrivalDeadline,
        LocalDate originalArrivalDeadline,
        RoutingCargoType cargoType,
        RoutingWeight weight,
        int maxTransitCount) {

    public RoutingCriteria {
        if (origin == null || destination == null) {
            throw new IllegalArgumentException("出発地と目的地は必須です");
        }
        if (origin.equals(destination)) {
            throw new IllegalArgumentException("出発地と目的地が同じです: " + origin.unlocode());
        }
        if (arrivalDeadline == null || originalArrivalDeadline == null) {
            throw new IllegalArgumentException("希望到着期限は必須です");
        }
        if (cargoType == null) {
            throw new IllegalArgumentException("貨物種別は必須です");
        }
        if (weight == null) {
            throw new IllegalArgumentException("重量は必須です");
        }
        // **探索の打ち切り条件である。** 上限が無いと、港が増えるほど組み合わせが増える
        if (maxTransitCount < 0) {
            throw new IllegalArgumentException("経由回数の上限は 0 以上です: " + maxTransitCount);
        }
    }

    /** 新規の探索条件。当初の期限は希望期限と同じである。 */
    public static RoutingCriteria of(
            Location origin,
            Location destination,
            LocalDate arrivalDeadline,
            RoutingCargoType cargoType,
            RoutingWeight weight,
            int maxTransitCount) {
        return new RoutingCriteria(origin, destination, arrivalDeadline, arrivalDeadline,
                cargoType, weight, maxTransitCount);
    }

    /** 期限を緩めた条件（US10）。<strong>当初の期限は変えない。</strong> */
    public RoutingCriteria withDeadline(LocalDate newDeadline) {
        return new RoutingCriteria(origin, destination, newDeadline, originalArrivalDeadline,
                cargoType, weight, maxTransitCount);
    }

    /** 経由回数の上限を緩めた条件（US10）。 */
    public RoutingCriteria withMaxTransitCount(int newMax) {
        return new RoutingCriteria(origin, destination, arrivalDeadline, originalArrivalDeadline,
                cargoType, weight, newMax);
    }

    /** 期限を延ばしているか。延ばしていれば荷主への通知に差分を含める（US12）。 */
    public boolean isDeadlineRelaxed() {
        return arrivalDeadline.isAfter(originalArrivalDeadline);
    }
}
