/**
 * 認証・認可の支援サブドメインの画面（管理者向け）。
 *
 * <p>ロック解除は ROLE_ADMIN のみ。<strong>画面で必須にするだけでは、
 * 再送や URL の直叩きで通ってしまう</strong>ため、サーバ側でも拒否する。
 */
package com.example.cargotracker.security.interfaces.web;
