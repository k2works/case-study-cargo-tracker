/**
 * 荷役コンテキスト。荷役作業の記録と通関申告の管理を責務とする。
 *
 * <p><strong>独立した境界付けられたコンテキストである</strong>（ADR-010。ADR-002 を
 * 置き換えた）。かつては Tracking Context 内のモジュールだったが、実装すると
 * 言語は分岐していた（{@code HandlingType} と {@code TrackingEventType} など、
 * 対応する型を 3 組も別々に定義していた）。<strong>統合されていたのではなく、
 * 境界が引かれていなかった。</strong>
 *
 * <p>他の BC のクラスを直接参照してはならない。Tracking への連携も
 * {@code TrackingEvents} ポートを通す。<strong>呼び出しは同期・同一トランザクション</strong>
 * であり（ADR-009）、結果整合は採らない。荷役だけが記録されて追跡に現れない
 * 中間状態を作らないためである。
 *
 * <p>URL は {@code /handling/*} のままである。<strong>URL は利用者から見た業務の
 * 区切りであり、内部のコンテキスト構成に追随させない</strong>（ADR-002 の判断を維持）。
 */
package com.example.cargotracker.handling;
