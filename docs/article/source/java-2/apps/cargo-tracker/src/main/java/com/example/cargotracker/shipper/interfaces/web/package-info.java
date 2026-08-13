/**
 * 荷主コンテキストの画面（Thymeleaf + Spring MVC）。
 *
 * <p>URL とロールの対応は docs/design/ui_design.md のナビゲーション構成が正典である。
 *
 * <p><strong>業務の不変条件をここに書かない。</strong> 画面の検証は利用者への案内であり、
 * モデルの正しさはドメイン層が担保する。ここに書くと、別の入口（API・バッチ）から
 * 同じ不変条件が破られる。
 */
package com.example.cargotracker.shipper.interfaces.web;
