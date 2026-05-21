package com.example.authms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * authms（認証マイクロサービス）のエントリポイント。
 *
 * <p>JWT 発行・ユーザー管理・ロール管理を担う。デフォルトプロファイルは
 * {@code local-h2}（H2 インメモリ DB + Kafka 無し）で、Docker 環境では
 * {@code local-docker}、Heroku 環境では {@code heroku} プロファイルが
 * 有効化される。</p>
 */
@SpringBootApplication
public class AuthMsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthMsApplication.class, args);
    }
}
