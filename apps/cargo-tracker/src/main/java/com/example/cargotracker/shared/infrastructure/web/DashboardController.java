package com.example.cargotracker.shared.infrastructure.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * ダッシュボード（ロール別の作業入口）。
 *
 * <p><strong>ログイン画面と分けている。</strong> カードの件数は各 BC の
 * {@code @ControllerAdvice} が載せており（例外の件数は Tracking が載せる）、
 * ログイン画面と同じクラスに置くと<strong>認証前の画面でも件数を数える
 * 問い合わせが走る</strong>。
 *
 * <p><strong>件数をここで集めない。</strong> 集めると {@code shared} が全 BC の
 * クエリサービスを参照することになり、カードが増えるたびに共有の画面が太る
 * （ArchUnit ルール 4）。
 */
@Controller
public class DashboardController {

    @GetMapping("/")
    public String dashboard() {
        return "dashboard";
    }
}
