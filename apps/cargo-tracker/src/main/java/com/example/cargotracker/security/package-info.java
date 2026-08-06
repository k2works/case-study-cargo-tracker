/**
 * 認証・認可の支援サブドメイン。
 *
 * <p><strong>これは業務の境界付けられたコンテキストではない。</strong> 貨物輸送という業務そのものを
 * 表さず、すべての BC の入口に横断的に効く関心事であるため、独立したトップレベルパッケージに置く
 * （docs/design/domain-model.md「9. Security サブドメイン」）。
 *
 * <p><strong>共有カーネル（{@code shared}）には置かない。</strong> 「全 BC から使うから shared へ」は
 * 常に正しく聞こえるが、共有カーネルの構成要素は {@code Location} と {@code ShipperId} の
 * 2 つのみと定めている（ADR-005）。ここに {@code UserAccount} を入れると、ロールを 1 つ増やす
 * だけで全 BC の再ビルドとレビューを強制する。
 */
package com.example.cargotracker.security;
