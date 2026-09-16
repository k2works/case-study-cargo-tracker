package com.example.cargotracker.acceptance.handling;

import com.example.cargotracker.handling.HandlingApplication;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 荷役（handlingms）の受け入れテストの土台。handlingms だけを起動する。
 *
 * <p><b>他サービスとは別のソースセットに置く。</b> Cucumber は 1 つの glue
 * パッケージにつき 1 つのコンテキストしか持てない。</p>
 */
@CucumberContextConfiguration
@SpringBootTest(classes = HandlingApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class HandlingCucumberConfiguration extends AbstractAxonIntegrationTest {
}
