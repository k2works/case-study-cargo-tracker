package com.example.cargotracker.acceptance.routing;

import com.example.cargotracker.routing.RoutingApplication;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 航海スケジュールの受け入れテストの土台。routingms だけを起動する。
 *
 * <p><b>bookingms とは別のソースセットに置いている。</b> 同じクラスパスに 2 つの
 * サービスを載せると、双方の {@code db/migration/V001__create_axon_tables.sql} が
 * 衝突して Flyway が起動しない（Found more than one migration with version 001）。
 * サービスを増やすたびにソースセットを増やす形にして、V001 の番号取りを
 * サービス間で調整しなくて済むようにする。</p>
 */
@CucumberContextConfiguration
@SpringBootTest(classes = RoutingApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class RoutingCucumberConfiguration extends AbstractAxonIntegrationTest {
}
