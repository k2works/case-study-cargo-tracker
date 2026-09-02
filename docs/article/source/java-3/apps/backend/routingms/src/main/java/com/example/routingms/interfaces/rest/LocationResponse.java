package com.example.routingms.interfaces.rest;

/** 地点の選択肢。UN/LOCODE を画面に直接入力させないために返す。 */
public record LocationResponse(String unLocode, String name) {
}
