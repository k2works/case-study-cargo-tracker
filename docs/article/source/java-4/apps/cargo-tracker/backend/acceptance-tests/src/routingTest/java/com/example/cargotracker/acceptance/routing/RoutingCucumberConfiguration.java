package com.example.cargotracker.acceptance.routing;

import com.example.cargotracker.routing.RoutingApplication;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 航海スケジュールの受け入れテストの土台。routingms だけを起動する。
 *
 * <p><b>bookingms とは別のソースセットに置いている。</b> Cucumber は 1 つの glue
 * パッケージにつき 1 つのコンテキストしか持てず、起動するサービスが違えば
 * コンテキストも別になる。各スイートのクラスパスに、そのサービスの分だけを載せる。</p>
 */
@CucumberContextConfiguration
@SpringBootTest(classes = RoutingApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class RoutingCucumberConfiguration extends AbstractAxonIntegrationTest {
}
