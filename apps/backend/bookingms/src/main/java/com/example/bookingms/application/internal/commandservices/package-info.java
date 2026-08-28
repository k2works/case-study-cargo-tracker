/**
 * 予約コンテキストのユースケース。
 *
 * <p>業務の手順（何を、どの順で行うか）をここに書く。<strong>業務の規則そのものは
 * ドメインモデルが持つ。</strong> ここに規則を書くと、別の入口から同じ規則が破られる。
 *
 * <p>外部への依存は {@code domain.repository} と {@code application.internal.outboundservices.acl}
 * の契約経由に限る。
 * 実装クラスを直接参照してはならない（ArchUnit が検証する）。
 */
package com.example.bookingms.application.internal.commandservices;
