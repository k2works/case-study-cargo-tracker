/**
 * 共有カーネルと横断的関心事。
 *
 * <p><strong>共有カーネルは {@code Location} と {@code ShipperId} の 2 要素のみである</strong>（ADR-005）。
 * 共有カーネルは最も変更コストが高く、放置すると「どこにも属さないもの置き場」に劣化するため、
 * ArchUnit で 2 要素以外が追加されていないことを検証する（test_strategy.md §3.3 ルール 6）。
 * 検証対象は共有カーネルそのもの、すなわち {@code shared.domain.model} である。
 * {@code shared.infrastructure} 配下は横断的な技術基盤であり共有カーネルではない。
 *
 * <p>{@code TransportStatus} は Tracking、{@code RoutingStatus} は Routing の所有であり、
 * ここには置かない。集約の内部状態を共有すると、状態を 1 つ増やすだけで全 BC の
 * 再ビルドとレビューを強制することになる。
 */
package com.example.cargotracker.shared;
