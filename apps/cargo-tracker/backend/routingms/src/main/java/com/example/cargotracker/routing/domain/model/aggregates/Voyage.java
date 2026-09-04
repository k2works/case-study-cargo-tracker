package com.example.cargotracker.routing.domain.model.aggregates;

import com.example.cargotracker.routing.domain.model.commands.RegisterVoyageCommand;
import com.example.cargotracker.routing.domain.model.events.VoyageRegisteredEvent;
import com.example.cargotracker.routing.domain.model.valueobjects.CargoType;
import com.example.cargotracker.routing.domain.model.valueobjects.CarrierMovement;
import com.example.cargotracker.routing.domain.model.valueobjects.VoyageNumber;
import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.error.IllegalTransition;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.extension.spring.stereotype.EventSourced;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;

/**
 * 航海（UC19 / US24）。
 *
 * <p>不変条件（domain-model.md「Voyage 集約の不変条件」）:</p>
 * <ul>
 *   <li>1: {@code VoyageNumber} は不変。同一番号の再登録は三段で拒否（ここが 1 段目）</li>
 *   <li>2: 寄港地は時刻昇順で港が連結（{@code Schedule} が守る）</li>
 *   <li>3: 到着は出発より後（{@code CarrierMovement} が守る）</li>
 *   <li>4: {@code acceptedCargoTypes} が空なら一般貨物のみ</li>
 * </ul>
 */
@EventSourced(idType = String.class, tagKey = "voyageNumber")
public class Voyage {

    private String voyageNumber;
    private boolean cancelled;

    @EntityCreator
    public Voyage() {
        // Axon がイベント再生で呼ぶ。
    }

    /**
     * 航海を登録する。
     *
     * <p><b>static ではなくインスタンスのハンドラにしている。</b> static（作る側）と
     * インスタンス（既にある側）を両方置くと、集約が既に存在しても static のほうが
     * 呼ばれ、2 度目の登録が通ってしまう（IT2 に {@code Cargo} で実測）。
     * {@code @EntityCreator} が空の集約を用意するので、インスタンス側だけで両方を扱える。</p>
     */
    @CommandHandler
    public String register(RegisterVoyageCommand command, EventAppender appender) {
        if (voyageNumber != null) {
            // 不変条件 1 の 1 段目。同時登録のレースはここを素通りするので、
            // 投影の UNIQUE と attention_item が残りの 2 段を担う。
            throw new IllegalTransition("航海 " + voyageNumber + " は既に登録されています");
        }
        validate(command);

        appender.append(new VoyageRegisteredEvent(
                command.voyageNumber(),
                command.carrier().carrierCode(),
                command.carrier().carrierName(),
                command.vesselName().value(),
                command.schedule().movements().stream()
                        .map(Voyage::toMovement)
                        .toList(),
                acceptedCargoTypeNames(command.acceptedCargoTypes()),
                command.registeredBy()));
        return command.voyageNumber();
    }

    private static VoyageRegisteredEvent.Movement toMovement(CarrierMovement movement) {
        return new VoyageRegisteredEvent.Movement(
                movement.departure().unLocode().value(),
                movement.arrival().unLocode().value(),
                movement.departureTime(),
                movement.arrivalTime());
    }

    /**
     * 不変条件 4。空なら一般貨物のみ。
     *
     * <p>空のまま保存すると、読む側が「制限なし」と「一般貨物のみ」を区別できない。
     * 集約の側で {@code GENERAL} に決めておけば、絞り込みのクエリは値の有無を
     * 気にせず書ける。</p>
     */
    private static List<String> acceptedCargoTypeNames(Set<CargoType> types) {
        Set<CargoType> resolved = types == null || types.isEmpty()
                ? Set.of(CargoType.GENERAL)
                : new TreeSet<>(types);
        return resolved.stream().map(Enum::name).toList();
    }

    private static void validate(RegisterVoyageCommand command) {
        // 値オブジェクトを通す。ここで文字列のまま空白だけを見ていると、
        // 長さの規則は VoyageNumber に書いてあるのに本番経路に載らない。
        // 20 文字を超える番号は集約を素通りしてイベントになり、投影の
        // voyage_number VARCHAR(20) で落ちて Processing Group が止まる。
        new VoyageNumber(command.voyageNumber());
        if (command.carrier() == null) {
            throw new BusinessRuleViolation("運送会社は必須です");
        }
        if (command.vesselName() == null) {
            throw new BusinessRuleViolation("船名は必須です");
        }
        if (command.schedule() == null) {
            throw new BusinessRuleViolation("寄港地を 1 件以上入力してください");
        }
    }

    @EventSourcingHandler
    void on(VoyageRegisteredEvent event) {
        this.voyageNumber = event.voyageNumber();
        this.cancelled = false;
    }

    /** 復元した航海番号。登録済みかどうかの判断に使う。 */
    public String voyageNumber() {
        return voyageNumber;
    }

    /** キャンセル済みか（不変条件 5。US25 以降で使う）。 */
    public boolean cancelled() {
        return cancelled;
    }
}
