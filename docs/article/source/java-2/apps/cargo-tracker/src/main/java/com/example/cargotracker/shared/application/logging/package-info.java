/**
 * 業務操作ログに出す値の整形。
 *
 * <p><strong>インフラ層ではなくアプリケーション層に置く。</strong> 監査ログを出すのは
 * コマンドサービス（アプリケーション層）であり、アプリケーション層はインフラ層を
 * 直接参照してはならない（ArchUnit ルール 3）。ログの出力先はインフラの関心事だが、
 * 「何を記録に残してよいか」は業務の関心事である。
 */
package com.example.cargotracker.shared.application.logging;
