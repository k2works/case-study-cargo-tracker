/**
 * 一覧のページ送り。
 *
 * <p>貨物予約・荷主・航路の 3 つの一覧が同じ規則で動くように、境界の計算を 1 か所に集める。
 * インフラ層ではなくアプリケーション層に置くのは、クエリサービス（アプリケーション層）が
 * 使うためである（ArchUnit ルール 3）。
 */
package com.example.cargotracker.shared.application.paging;
