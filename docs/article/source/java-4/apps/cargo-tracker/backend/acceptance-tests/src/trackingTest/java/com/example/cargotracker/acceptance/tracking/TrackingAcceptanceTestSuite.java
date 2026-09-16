package com.example.cargotracker.acceptance.tracking;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

/** 追跡（trackingms）のデモ項目を回す入口。 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = "cucumber.glue",
        value = "com.example.cargotracker.acceptance.tracking")
class TrackingAcceptanceTestSuite {
}
