/**
 * ログイン中の利用者を、業務の側から見るための約束。
 *
 * <p><strong>「誰か」ではなく「何ができるか・どこまで見えるか」を運ぶ。</strong>
 * 荷主として紐づいた利用者は自社の予約だけが見える（US34）。
 *
 * <p>Spring Security の型を各 BC へ持ち込まないための層である。
 */
package com.example.cargotracker.shared.application.security;
