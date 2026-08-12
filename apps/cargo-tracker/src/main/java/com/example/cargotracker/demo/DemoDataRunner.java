package com.example.cargotracker.demo;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 起動時に動作確認用データを投入する。
 *
 * <p><strong>本番では走らない。</strong> {@code cargo-tracker.demo.install} が
 * {@code true} のときだけ有効になり、この設定は {@code application-local.yml} と
 * {@code application-dev.yml} にしか無い。{@code db/demo} の Flyway locations と
 * <strong>同じ条件で入る</strong>ようにしてある。
 *
 * <p><strong>投入そのものは {@link DemoDataInstaller} が持つ。</strong> ここに置くと
 * 起動しないと確かめられなくなる —— 投入の中身は検査で固定したい。
 */
@Component
@ConditionalOnProperty(name = "cargo-tracker.demo.install", havingValue = "true")
public class DemoDataRunner implements ApplicationRunner {

    private final DemoDataInstaller installer;

    public DemoDataRunner(DemoDataInstaller installer) {
        this.installer = installer;
    }

    @Override
    public void run(ApplicationArguments args) {
        installer.install();
    }
}
