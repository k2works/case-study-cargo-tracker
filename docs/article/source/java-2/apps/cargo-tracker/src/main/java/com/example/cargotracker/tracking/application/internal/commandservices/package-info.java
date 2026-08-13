/**
 * 追跡コンテキストのコマンドサービス（ユースケース実行）。
 *
 * <p>他 BC のイベントを受け取ったハンドラは、ここへ委譲する。
 * <strong>ハンドラがリポジトリを直接触らない</strong>（ArchUnit ルール 3）。
 */
package com.example.cargotracker.tracking.application.internal.commandservices;
