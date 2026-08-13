/**
 * 経路コンテキストの業務の要求を 1 つの型にまとめたもの。
 *
 * <p><strong>呼び出し側が組み立て、集約が受け取る。</strong> 引数を並べる代わりに型にすることで、
 * 同じ型の引数を取り違えてもコンパイルが通る形を避ける。
 *
 *
 * <p>他の BC のクラスを直接参照してはならない（ArchUnit ルール 4）。
 */
package com.example.cargotracker.routing.domain.model.commands;
