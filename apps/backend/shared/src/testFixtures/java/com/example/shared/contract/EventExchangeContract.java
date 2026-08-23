package com.example.shared.contract;

import java.util.Map;

/**
 * 交換機そのものの契約（[ADR-022] 決定 4）。
 *
 * <p><strong>名前が一致しているだけでは足りない。</strong>交換機は、耐久性・自動削除・引数まで
 * 含めて同じでなければ再宣言できない。食い違うと、後から接続したほうが
 * {@code PRECONDITION_FAILED - inequivalent arg} で落ちる。しかも既存の交換機は
 * <strong>宣言し直せない</strong>ため、落ちたサービスは後続のキュー宣言まで止まる。
 *
 * <p>IT7 の kind 統合で実際に踏んだ。Testcontainers は毎回まっさらな交換機を作るので、
 * <strong>この壊れ方はテストでは出ない</strong>。守っているのが各サービスのコメントだけ
 * だったため、契約として置き直す。
 */
public final class EventExchangeContract {

    private EventExchangeContract() {
    }

    /**
     * どのキューにも結びつかなかったイベントの行き先。
     *
     * <p>デッドレターが守るのは「受け取ったが処理できなかった」だけである。ルーティングキーの
     * 綴りが違う・購読側がまだ配線されていない場合、イベントはどのキューにも入らず黙って消え、
     * 発行側は成功を返す。
     */
    public static final String UNROUTABLE_EXCHANGE = "cargo.unroutable";

    public static final String UNROUTABLE_QUEUE = "cargo.unroutable.queue";

    /** 再起動で消えない。 */
    public static final boolean DURABLE = true;

    /** 購読者がいなくなっても消さない。 */
    public static final boolean AUTO_DELETE = false;

    /** 業務のイベントを流す交換機が持つ引数。<strong>全サービスで同一であること</strong>。 */
    public static final Map<String, Object> ARGUMENTS =
            Map.of("alternate-exchange", UNROUTABLE_EXCHANGE);
}
