/** Copyright (c) 2026 AVI-SPL, Inc. All Rights Reserved. */
package com.avispl.symphony.dal.infrastructure.management.nureva.console.common;

/**
 * Represents aggregator properties of an aggregator device.
 *
 * @author Kevin / Symphony Dev Team
 * @since 1.0.0
 */
public enum AggregatorProperty {
	ADAPTER_BUILD_DATE("AdapterBuildDate"),
	ADAPTER_UPTIME("AdapterUptime"),
	ADAPTER_UPTIME_MIN("AdapterUptime(min)"),
	ADAPTER_VERSION("AdapterVersion"),
	LAST_MONITORING_CYCLE_DURATION("LastMonitoringCycleDuration(sec)"),
	MONITORED_DEVICES_TOTAL("MonitoredDevicesTotal"),
	MONITORED_CYCLE_INTERVAL("MonitoringCycleInterval(min)");

	private final String defaultName;

	AggregatorProperty(String defaultName) {
		this.defaultName = defaultName;
	}

	public String getDefaultName() {
		return defaultName;
	}
}
