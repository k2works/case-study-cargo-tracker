package com.example.billingms.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * ShedLock 設定（IT8 T2.1 / ADR-0017）。
 *
 * <p>billingms multi-instance デプロイ時の {@code @Scheduled} 排他制御を提供する。
 * 既存 {@code billing_read_db} 内の {@code shedlock} テーブル（Flyway V3）を使用するため
 * 追加 DataSource は不要。</p>
 *
 * <p>{@code @EnableSchedulerLock(defaultLockAtMostFor = "PT19H")} は、ロック保持の最大時間。
 * cron 周期（24h）の 80% を目安に設定。万一プロセスがクラッシュしてロックを返却できなくても、
 * 19 時間後には自動的に他 instance が取得できる。</p>
 *
 * <p>具体的なロック名と保持時間は {@link com.example.billingms.interfaces.scheduling.OverdueScheduler}
 * の {@code @SchedulerLock(name = "billing-overdue-scheduler", lockAtMostFor, lockAtLeastFor)} で指定する。</p>
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT19H")
public class ShedLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build()
        );
    }
}
