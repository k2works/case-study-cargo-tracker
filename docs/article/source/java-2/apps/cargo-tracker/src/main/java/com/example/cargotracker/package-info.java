/**
 * 国際貨物輸送管理システム（Cargo Tracker）。
 *
 * <p><strong>トップレベルのパッケージは Bounded Context と 1 対 1 である</strong>
 * （ADR-010。ArchUnit で固定している）。
 *
 * <table>
 *   <caption>Bounded Context</caption>
 *   <tr><th>パッケージ</th><th>担うこと</th></tr>
 *   <tr><td>{@code booking}</td><td>貨物予約。予約から引き渡しまでの状態を持つ</td></tr>
 *   <tr><td>{@code shipper}</td><td>荷主と法人契約</td></tr>
 *   <tr><td>{@code routing}</td><td>航海スケジュールと経路の探索・確定</td></tr>
 *   <tr><td>{@code tracking}</td><td>貨物の現在地と例外（遅延・破損・紛失・誤配）</td></tr>
 *   <tr><td>{@code handling}</td><td>荷役作業と通関申告（ADR-010 で独立させた）</td></tr>
 *   <tr><td>{@code billing}</td><td>料金の算出・請求・入金</td></tr>
 *   <tr><td>{@code estimation}</td><td>輸送見積（予約前の照会）</td></tr>
 *   <tr><td>{@code security}</td><td>認証・認可。支援サブドメインであり共有カーネルではない（ADR-007）</td></tr>
 *   <tr><td>{@code shared}</td><td>共有カーネル。入れてよいのは {@code Location} と {@code ShipperId} だけ（ADR-005）</td></tr>
 *   <tr><td>{@code demo}</td><td>動作確認用データの投入。BC ではなく合成ルートと同じ立場である</td></tr>
 * </table>
 *
 * <p><strong>BC 間の通信はドメインイベントか ACL ポートに限る</strong>（ADR-012）。
 * 集約や値オブジェクトを直接参照してはならない。
 */
package com.example.cargotracker;
