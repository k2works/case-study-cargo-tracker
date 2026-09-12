package com.example.cargotracker.booking.interfaces.rest.dto;

import com.example.cargotracker.booking.domain.model.valueobjects.CargoSpecification;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoType;
import com.example.cargotracker.booking.domain.model.valueobjects.Dimensions;
import com.example.cargotracker.booking.domain.model.valueobjects.HazardousDeclaration;
import com.example.cargotracker.booking.domain.model.valueobjects.TemperatureRequirement;
import com.example.cargotracker.booking.domain.model.valueobjects.Weight;
import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;

/**
 * 入力から貨物仕様を組み立てる（US04・US32・US01）。
 *
 * <p><b>入口ごとに書かない。</b> 受付・修正・見積が同じ組み立てをする——写すと、
 * 項目を足したときに片方だけが拾う。</p>
 *
 * <p><b>種別ごとの必須項目は検査しない。</b> 判断は集約が持ち、入口は形だけを
 * 見る。両方に置くと、集約を直したときに入口だけが古い規則で弾く。</p>
 */
public final class CargoSpecificationAssembler {

    private CargoSpecificationAssembler() {
    }

    /**
     * 種別ごとの付帯情報を組み立てる。
     *
     * <p>空文字は「入力していない」として {@code null} に寄せる。空文字のまま渡すと、
     * 「危険物申告がある」と判断されて集約の検査を素通りする。</p>
     */
    public static CargoSpecification from(BookingDtos.CargoFields request) {
        return new CargoSpecification(
                cargoType(request.cargoType()),
                new Weight(request.weightKg()),
                new Dimensions(request.lengthCm(), request.widthCm(), request.heightCm()),
                request.quantity(),
                request.productName(),
                blank(request.hazardImoClass()) && blank(request.hazardUnNumber())
                        ? null
                        : new HazardousDeclaration(request.hazardImoClass(),
                                request.hazardUnNumber()),
                request.temperatureMinC() == null && request.temperatureMaxC() == null
                        ? null
                        : new TemperatureRequirement(request.temperatureMinC(),
                                request.temperatureMaxC()));
    }

    /**
     * 知らない貨物種別を素の例外にしない。
     *
     * <p>{@code CargoType.valueOf} の {@code IllegalArgumentException} は
     * {@code ApiExceptionHandler} の対象外なので 500 に化ける。入力の誤りは
     * 業務規則違反として 422 で返す。</p>
     */
    public static CargoType cargoType(String name) {
        try {
            return CargoType.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleViolation("知らない貨物種別です: " + name);
        }
    }

    /**
     * 空白は「入れていない」と同じに扱う。
     *
     * <p><b>入口ごとに違う扱いをしない。</b> 画面は選択肢を切り替えたときに
     * 空文字を送る——{@code null} しか見ないと、空文字が「入力された」ことに
     * なり、値オブジェクトが先に断って<b>集約の守りが画面から踏まれなくなる</b>。</p>
     */
    public static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
