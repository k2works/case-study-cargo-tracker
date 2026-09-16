package com.example.cargotracker.auth.infrastructure.config;

import java.util.Arrays;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 動作確認用の利用者を読み込む場所を Flyway に足す（ADR-0004）。
 *
 * <p>既定は無効。{@code cargo-tracker.demo-users=true} を明示した環境だけが
 * {@code classpath:db/seed} を読む。書き忘れたら安全側（読まない）に倒れる。</p>
 *
 * <p>設定ファイルの locations に直接書かない理由は、環境変数の展開で場所を
 * 継ぎ足す形にすると「空文字を渡したつもりが有効なまま」といった取り違えが
 * 起きるため。条件を 1 つの真偽値に寄せ、その真偽値だけを検査する。</p>
 */
@Configuration
@ConditionalOnProperty(name = "cargo-tracker.demo-users", havingValue = "true")
public class DemoUserSeedConfiguration {

    /** 動作確認用の利用者を置く場所。 */
    public static final String SEED_LOCATION = "classpath:db/seed";

    @Bean
    public FlywayConfigurationCustomizer demoUserSeedLocation() {
        return configuration -> {
            // 置き換えではなく足す。置き換えると業務のスキーマ（db/migration）が
            // 読まれなくなり、テーブルの無い DB に利用者を入れようとして落ちる。
            String[] current = Arrays.stream(configuration.getLocations())
                    .map(Object::toString)
                    .toArray(String[]::new);
            String[] extended = Arrays.copyOf(current, current.length + 1);
            extended[current.length] = SEED_LOCATION;
            configuration.locations(extended);
        };
    }
}
