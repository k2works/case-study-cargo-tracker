/**
 * 経路コンテキストのドメインモデル。
 *
 * <p>集約ルートは {@link com.example.routingms.domain.model.aggregates.Voyage}。「つながっているか」
 * 「その貨物を運べるか」の判断はここにあり、経路候補算出（IT4）もこの判断を使う。
 * 判断を呼び出し側へ散らかすと、画面と経路探索が別々の答えを出すようになる。
 */
package com.example.routingms.domain.model;
