/**
 * 認証コンテキストの依存関係の組み立て。
 *
 * <p>ユースケースとポート実装の結線をここで行う。<strong>業務の判断を書かない。</strong>
 *
 * <p>{@code Clock} は業務タイムゾーンで注入する。UTC で判断すると、時差の分だけ
 * 「当日」の扱いがずれる時間帯ができる。
 */
package com.example.authms.config;
