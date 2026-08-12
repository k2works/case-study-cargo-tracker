package com.example.cargotracker.booking.domain.model;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingCommandType;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingStatus;

/**
 * 遷移表に無い状態遷移が試みられたことを表す。
 *
 * <p>{@code domain-model.md}「BookingStatus 状態遷移表」の不変条件
 * 「表に無い遷移はすべて拒否する」に対応する。
 */
public class InvalidBookingStatusTransitionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final BookingStatus from;
    private final BookingCommandType command;

    public InvalidBookingStatusTransitionException(BookingStatus from, BookingCommandType command) {
        super("状態「%s」に対して %s は実行できません".formatted(from.displayName(), command));
        this.from = from;
        this.command = command;
    }

    public BookingStatus from() {
        return from;
    }

    public BookingCommandType command() {
        return command;
    }
}
