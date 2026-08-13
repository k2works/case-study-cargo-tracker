package com.example.cargotracker.tracking.application.internal.outboundservices.acl;

import java.util.List;
import java.util.Map;

/**
 * 港の名前を引く出力ポート。
 *
 * <p>US18 の受入基準は「位置（<strong>港湾名</strong>）が表示される」と定める。
 * <strong>荷主の総務担当や荷受人が {@code JPOSA} を読めることは期待できない。</strong>
 * とくに公開追跡は取引先へ転送される画面であり、読みやすさが会社の見え方に直結する。
 *
 * <p><strong>Booking の {@code KnownPorts} と共有しない。</strong> BC をまたいで
 * ポートを共有すると、片方の都合で他方の入口が変わる（ADR-005・ArchUnit ルール 4）。
 * 読む先の港マスタが同じであることは、インフラ側のアダプタが引き受ける。
 *
 * <p><strong>アプリケーション層からインフラの Mapper を直接呼ばない。</strong>
 * 呼ぶと依存の向きが逆になる（ArchUnit「アプリケーション層はインフラ層に依存しない」）。
 */
public interface PortNames {

    /**
     * UN/LOCODE から港の名前を引く。
     *
     * <p><strong>まとめて引く。</strong> イベント履歴は件数だけ港が並ぶため、
     * 1 件ずつ引くと表示のたびに N 回の問い合わせになる。
     *
     * @param unlocodes 引きたい港のコード
     * @return コードから名前への対応。マスタに無いコードは含まれない
     */
    Map<String, String> findNames(List<String> unlocodes);
}
