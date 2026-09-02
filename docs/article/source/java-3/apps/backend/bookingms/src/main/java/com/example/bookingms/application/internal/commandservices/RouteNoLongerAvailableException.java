package com.example.bookingms.application.internal.commandservices;

/**
 * 選んだ経路がもう成立しない（[ADR-019] 決定 2）。
 *
 * <p>候補を出してから確定するまでのあいだに、航海が更新・欠航・削除されたときに投げる。
 * <strong>利用者の側で直せる</strong>——経路をもう一度探せばよい。だから 409 で返す。
 *
 * <p><strong>専用の型にする理由。</strong>以前は素の {@link IllegalStateException} で投げており、
 * 同じ型を投げる<strong>こちら側の不備</strong>（地点マスタの欠落）まで 409 と
 * 「経路をもう一度探してください」で返っていた。経路設計者は何度探し直しても直らない作業に入り、
 * しかも原因は記録に残らない。射程を絞るには、断る理由ごとに型を分ける。
 *
 * <p>{@link IllegalStateException} を継承しているのは、集約の状態違反と同じく 409 で返る
 * 系統に属するためである（分類を変えるのではなく、その中で名指しできるようにしている）。
 */
public class RouteNoLongerAvailableException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public RouteNoLongerAvailableException(String message) {
        super(message);
    }
}
