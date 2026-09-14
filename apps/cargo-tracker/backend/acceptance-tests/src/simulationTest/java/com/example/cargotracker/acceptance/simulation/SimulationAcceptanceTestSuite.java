package com.example.cargotracker.acceptance.simulation;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

/** 業務シミュレーションのデモ項目を回す入口（UC23）。 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = "cucumber.glue",
        value = "com.example.cargotracker.acceptance.simulation")
class SimulationAcceptanceTestSuite {
}
