package com.example.cargotracker.quote.application.internal.commandservices;

/**
 * 利用可能なルート候補が存在しない場合にスローされる例外。
 */
public class NoRouteAvailableException extends RuntimeException {

    public NoRouteAvailableException(String originLocode, String destinationLocode) {
        super("利用可能なルートが見つかりません: " + originLocode + " → " + destinationLocode);
    }
}
