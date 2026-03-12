/*
 *  Copyright (c) 2024 AVI-SPL, Inc. All Rights Reserved.
 */

package com.avispl.symphony.dal.infrastructure.management.nureva.console;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Assert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;
import com.avispl.symphony.api.dal.dto.monitor.aggregator.AggregatedDevice;

/**
 * NurevaConsoleCommunicatorTest
 *
 * @author Harry / Symphony Dev Team<br>
 * Created on 9/8/2023
 * @since 1.0.0
 */
public class NurevaConsoleCommunicatorTest {
	private ExtendedStatistics extendedStatistic;
	private NurevaConsoleCommunicator nurevaConsoleCommunicator;

	@BeforeEach
	void setUp() throws Exception {
		nurevaConsoleCommunicator = new NurevaConsoleCommunicator();
		nurevaConsoleCommunicator.setHost("");
		nurevaConsoleCommunicator.setLogin("");
		nurevaConsoleCommunicator.setPassword("");
		nurevaConsoleCommunicator.setPort(443);
		nurevaConsoleCommunicator.init();
		nurevaConsoleCommunicator.connect();
	}

	@AfterEach
	void destroy() throws Exception {
		nurevaConsoleCommunicator.disconnect();
		nurevaConsoleCommunicator.destroy();
	}

	/**
	 * Test case to verify the retrieval of aggregator data.
	 */
	@Test
	void testGetAggregatorData() throws Exception {
		extendedStatistic = (ExtendedStatistics) nurevaConsoleCommunicator.getMultipleStatistics().get(0);
		Map<String, String> statistics = extendedStatistic.getStatistics();
		Assert.assertEquals(2, statistics.size());
	}

	/**
	 * Test case to verify the retrieval of aggregator information.
	 */
	@Test
	void testGetAggregatorInformation() throws Exception {
		extendedStatistic = (ExtendedStatistics) nurevaConsoleCommunicator.getMultipleStatistics().get(0);
		Map<String, String> statistics = extendedStatistic.getStatistics();
		Assert.assertEquals(3, statistics.get("NumberOfDevices"));
		Assert.assertEquals(3, statistics.get("NumberOfRooms"));
	}

	/**
	 * Test case to verify the warnings of room.
	 */
	@Test
	void testWarningsOfRoom() throws Exception {
		nurevaConsoleCommunicator.getMultipleStatistics();
		nurevaConsoleCommunicator.retrieveMultipleStatistics();
		Thread.sleep(30000);
		nurevaConsoleCommunicator.retrieveMultipleStatistics();
		extendedStatistic = (ExtendedStatistics) nurevaConsoleCommunicator.getMultipleStatistics().get(0);
		Map<String, String> statistics = extendedStatistic.getStatistics();
		Assert.assertEquals(3, statistics.size());
	}

	/**
	 * Test case to verify the retrieval of multiple statistics.
	 */
	@Test
	void testGetMultipleStatistics() throws Exception {
		nurevaConsoleCommunicator.getMultipleStatistics();
		nurevaConsoleCommunicator.retrieveMultipleStatistics();
		Thread.sleep(30000);
		List<AggregatedDevice> aggregatedDeviceList = nurevaConsoleCommunicator.retrieveMultipleStatistics();
		Assert.assertEquals(3, aggregatedDeviceList.size());
	}

	/**
	 * Test case to verify the aggregated information.
	 */
	@Test
	void testAggregatedInformation() throws Exception {
		nurevaConsoleCommunicator.getMultipleStatistics();
		nurevaConsoleCommunicator.retrieveMultipleStatistics();
		Thread.sleep(30000);
		List<AggregatedDevice> aggregatedDeviceList = nurevaConsoleCommunicator.retrieveMultipleStatistics();
		String deviceId = "0ebefec7-d28a-4ff6-84d0-3392d74dde6f";
		Optional<AggregatedDevice> aggregatedDevice = aggregatedDeviceList.stream().filter(item -> item.getDeviceId().equals(deviceId)).findFirst();
		if (aggregatedDevice.isPresent()) {
			Map<String, String> stats = aggregatedDevice.get().getProperties();
			Assert.assertEquals("Online", stats.get("DeviceStatus"));
			Assert.assertEquals("3.1.0", stats.get("FirmwareCurrentVersion"));
			Assert.assertEquals("True", stats.get("FirmwareUpdateAvailable"));
			Assert.assertEquals("3.1.9", stats.get("Subscriptions"));
			Assert.assertEquals("WinDaemonHost", stats.get("MachineName"));
			Assert.assertEquals("HDL300", stats.get("RoomName"));
		}
	}

	/**
	 * Test case to verify the aggregated settings.
	 */
	@Test
	void testAggregatedSettings() throws Exception {
		nurevaConsoleCommunicator.getMultipleStatistics();
		nurevaConsoleCommunicator.retrieveMultipleStatistics();
		Thread.sleep(30000);
		List<AggregatedDevice> aggregatedDeviceList = nurevaConsoleCommunicator.retrieveMultipleStatistics();
		String deviceId = "0ebefec7-d28a-4ff6-84d0-3392d74dde6f";
		Optional<AggregatedDevice> aggregatedDevice = aggregatedDeviceList.stream().filter(item -> item.getDeviceId().equals(deviceId)).findFirst();
		if (aggregatedDevice.isPresent()) {
			Map<String, String> stats = aggregatedDevice.get().getProperties();
			Assert.assertEquals("25", stats.get("Settings#ActiveZoneLength(ft)"));
			Assert.assertEquals("25", stats.get("Settings#ActiveZoneWidth"));
			Assert.assertEquals("Line level", stats.get("Settings#AuxiliaryOutputState"));
			Assert.assertEquals("6", stats.get("Settings#MicrophoneGain"));
			Assert.assertEquals("Mono", stats.get("Settings#MicrophonePickupState"));
			Assert.assertEquals("High", stats.get("Settings#ReductionEchoLevel"));
			Assert.assertEquals("Medium", stats.get("Settings#ReductionNoiseLevel"));
			Assert.assertEquals("Enable", stats.get("Settings#SleepMode"));
			Assert.assertEquals("Disable", stats.get("Settings#VoiceAmplification"));
			Assert.assertEquals("Line", stats.get("Settings#VoiceAmplificationAuxInLevel"));
			Assert.assertEquals("Medium", stats.get("Settings#VoiceAmplificationLevel"));
		}
	}

	/**
	 * Test case to verify the Speaker Treble Level Control
	 */
	@Test
	void testSpeakerBassLevelControl() throws Exception {
		nurevaConsoleCommunicator.getMultipleStatistics();
		nurevaConsoleCommunicator.retrieveMultipleStatistics();
		Thread.sleep(20000);
		nurevaConsoleCommunicator.retrieveMultipleStatistics();
		ControllableProperty controllableProperty = new ControllableProperty();
		String property = "Settings#SpeakerBassLevel";
		String value = "15";
		String deviceId = "0ebefec7-d28a-4ff6-84d0-3392d74dde6f";
		controllableProperty.setProperty(property);
		controllableProperty.setValue(value);
		controllableProperty.setDeviceId(deviceId);
		nurevaConsoleCommunicator.controlProperty(controllableProperty);
	}

	/**
	 * Test case to verify the active zone control.
	 */
	@Test
	void testActiveZoneControl() throws Exception {
		nurevaConsoleCommunicator.getMultipleStatistics();
		nurevaConsoleCommunicator.retrieveMultipleStatistics();
		Thread.sleep(20000);
		nurevaConsoleCommunicator.retrieveMultipleStatistics();
		ControllableProperty controllableProperty = new ControllableProperty();
		String property = "Settings#ActiveZoneControl";
		String value = "1";
		String deviceId = "0ebefec7-d28a-4ff6-84d0-3392d74dde6f";
		controllableProperty.setProperty(property);
		controllableProperty.setValue(value);
		controllableProperty.setDeviceId(deviceId);
		nurevaConsoleCommunicator.controlProperty(controllableProperty);
	}

	/**
	 * Test case to verify the active zone type.
	 */
	@Test
	void testActiveZoneType() throws Exception {
		nurevaConsoleCommunicator.getMultipleStatistics();
		nurevaConsoleCommunicator.retrieveMultipleStatistics();
		Thread.sleep(20000);
		nurevaConsoleCommunicator.retrieveMultipleStatistics();
		ControllableProperty controllableProperty = new ControllableProperty();
		String property = "Settings#ActiveZoneType";
		String value = "0";
		String deviceId = "0ebefec7-d28a-4ff6-84d0-3392d74dde6f";
		controllableProperty.setProperty(property);
		controllableProperty.setValue(value);
		controllableProperty.setDeviceId(deviceId);
		nurevaConsoleCommunicator.controlProperty(controllableProperty);
	}

	/**
	 * Test case to verify the treble level control.
	 */
	@Test
	void testTrebleLevelControl() throws Exception {
		nurevaConsoleCommunicator.getMultipleStatistics();
		nurevaConsoleCommunicator.retrieveMultipleStatistics();
		Thread.sleep(20000);
		nurevaConsoleCommunicator.retrieveMultipleStatistics();
		ControllableProperty controllableProperty = new ControllableProperty();
		String property = "Settings#SpeakerTrebleLevel";
		String value = "15";
		String deviceId = "0ebefec7-d28a-4ff6-84d0-3392d74dde6f";
		controllableProperty.setProperty(property);
		controllableProperty.setValue(value);
		controllableProperty.setDeviceId(deviceId);
		nurevaConsoleCommunicator.controlProperty(controllableProperty);
	}
}
