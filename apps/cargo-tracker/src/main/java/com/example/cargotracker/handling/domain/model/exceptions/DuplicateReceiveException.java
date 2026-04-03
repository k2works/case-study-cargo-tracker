package com.example.cargotracker.handling.domain.model.exceptions;

/**
 * 同一予約に RECEIVE イベントが重複して登録されようとした場合にスローされる例外。
 * RECEIVE は 1 予約につき 1 回のみ記録可能というドメインルール違反を表す。
 */
public class DuplicateReceiveException extends RuntimeException {

    public DuplicateReceiveException() {
        super("この予約には既に引取が記録されています。二重登録はできません。");
    }
}
