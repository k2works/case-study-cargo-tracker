/**
 * 開発環境の動作確認用データを投入する。
 *
 * <p><strong>BC ではない。</strong> 合成ルート（{@code CargoTrackerApplication}）と
 * 同じ立場にあり、業務ロジックを持たない。<strong>画面と同じ順番で各 BC の
 * サービスを呼ぶだけ</strong>である（ArchUnit で BC 間参照の対象外にしている）。
 *
 * <p><strong>本番では走らない。</strong> {@code cargo-tracker.demo.install=true} の
 * ときだけ有効であり、この設定は local / dev のプロファイルにしかない。
 *
 * <p><strong>SQL で作らない理由</strong>は {@code DemoDataInstaller} に書いてある ——
 * 追跡の記録も請求書も、荷役を登録した結果としてドメインイベントが作る派生データである。
 */
package com.example.cargotracker.demo;
