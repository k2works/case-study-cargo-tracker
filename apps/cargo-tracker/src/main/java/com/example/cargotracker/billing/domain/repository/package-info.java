/**
 * 請求コンテキストの永続化の約束（interface）。
 *
 * <p><strong>実装は infrastructure に置く</strong>（依存性逆転）。ここに置くのは
 * 「集約をどう出し入れするか」だけであり、SQL もマッパーも知らない。
 */
package com.example.cargotracker.billing.domain.repository;
