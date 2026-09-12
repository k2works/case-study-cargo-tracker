package com.example.cargotracker.booking.domain.model.commands;

import com.example.cargotracker.booking.domain.model.valueobjects.CargoType;
import com.example.cargotracker.booking.domain.model.valueobjects.HazardousDeclaration;
import com.example.cargotracker.booking.domain.model.valueobjects.QuotedRoute;
import com.example.cargotracker.booking.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.booking.domain.model.valueobjects.Weight;
import java.util.List;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 輸送見積を作る（UC01 / US01）。
 *
 * <p><b>候補は外で数えてから渡す。</b> 集約は経路探索を知らない（ACL 越しの
 * 問い合わせは application 層の仕事で、集約に持ち込むと集約が他サービスの
 * 都合で落ちる）。概算も {@code QuotationEstimator} が先に付ける。</p>
 *
 * <p><b>候補 0 件でも受け付ける。</b> 期限に間に合う経路が無いことも荷主に
 * 伝えるべき答えである（正典の不変条件）。</p>
 *
 * @param candidates 概算つきのルート候補。空でもよい
 * @param hazardousDeclaration 危険物申告。<b>危険物のときだけ</b>必須
 */
public record CreateQuotationCommand(
        @TargetEntityId String quotationId,
        RouteSpecification routeSpecification,
        CargoType cargoType,
        Weight weight,
        HazardousDeclaration hazardousDeclaration,
        List<QuotedRoute> candidates,
        String createdBy) {
}
