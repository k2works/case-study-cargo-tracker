/**
 * 荷主コンテキストのコマンドサービス（ユースケース）。
 *
 * <p>集約をまたぐ調整とトランザクション境界を担う。<strong>業務判断そのものは集約に置き、
 * ここには置かない。</strong> ここに業務ロジックが溜まると、集約が値の入れ物に退化する。
 *
 * <p>永続化への参照はドメイン層で定義した出力ポート経由に限る（DIP）。
 */
package com.example.cargotracker.shipper.application.internal.commandservices;
