/*
 *  Copyright (c) 2024 AVI-SPL, Inc. All Rights Reserved.
 */

package com.avispl.symphony.dal.infrastructure.management.nureva.console.common;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Enum representing aggregated properties.
 *
 * @author Harry / Symphony Dev Team<br>
 * Created on 3/25/2024
 * @since 1.0.0
 */
public enum AggregatedProperty {
	ROOM_NAME("RoomName", "", ""),
	DEVICE_NAME("DeviceName", NurevaConsoleConstant.INFORMATION, "type"),
	FIRMWARE_VERSION("FirmwareCurrentVersion", NurevaConsoleConstant.INFORMATION, "firmwareVersion"),
	MACHINE_NAME("MachineName", NurevaConsoleConstant.INFORMATION, "machineName"),
	DEVICE_STATUS("DeviceStatus", NurevaConsoleConstant.INFORMATION, "deviceStatus"),
	HARDWARE_COMPONENTS("HardwareComponents", NurevaConsoleConstant.INFORMATION, "hardwareComponents"),
	VOICE_AMPLIFICATION("VoiceAmplification", NurevaConsoleConstant.SETTINGS, "voiceAmplificationEnabled"),
	AUX_IN_LEVEL("VoiceAmplificationAuxInLevel", NurevaConsoleConstant.SETTINGS, "voiceAmplificationAuxInLevel"),
	VOICE_AMPLIFICATION_LEVEL("VoiceAmplificationLevel", NurevaConsoleConstant.SETTINGS, "voiceAmplificationLevel"),
	ACTIVE_ZONE_TYPE("ActiveZoneType", NurevaConsoleConstant.SETTINGS, "activeZoneType"),
	ACTIVE_ZONE_CONTROL("ActiveZoneControl", NurevaConsoleConstant.SETTINGS, "activeZoneControlEnabled"),
	WIDTH("ActiveZoneWidth(ft)", NurevaConsoleConstant.SETTINGS, "activeZoneWidthFeet"),
	LENGTH("ActiveZoneLength(ft)", NurevaConsoleConstant.SETTINGS, "activeZoneLengthFeet"),
	AUXILIARY_OUTPUT_STATE("AuxiliaryOutputState", NurevaConsoleConstant.SETTINGS, "auxiliaryOutputState"),
	SLEEP_MODE("SleepMode", NurevaConsoleConstant.SETTINGS, "sleepModeEnabled"),
	SPEAKER_TREBLE_LEVEL("SpeakerTrebleLevel", NurevaConsoleConstant.SETTINGS, "speakerTrebleLevel"),
	SPEAKER_BASS_LEVEL("SpeakerBassLevel", NurevaConsoleConstant.SETTINGS, "speakerBassLevel"),
	PICKUP_STATE("MicrophonePickupState", NurevaConsoleConstant.SETTINGS, "microphonePickupState"),
	MICROPHONE_GAIN("MicrophoneGain", NurevaConsoleConstant.SETTINGS, "microphoneGain"),
	ECHO_REDUCTION_LEVEL("ReductionEchoLevel", NurevaConsoleConstant.SETTINGS, "echoReductionLevel"),
	NOISE_REDUCTION_LEVEL("ReductionNoiseLevel", NurevaConsoleConstant.SETTINGS, "noiseReductionLevel"),
	DISPLAY_THEME("DisplayTheme", NurevaConsoleConstant.SETTINGS, "displayTheme"),
	DISPLAY_CLOCK("DisplayClock", NurevaConsoleConstant.SETTINGS, "displayClockEnabled"),
	DISPLAY_TIME_FORMAT("DisplayTimeFormat", NurevaConsoleConstant.SETTINGS, "displayTimeFormat"),
	;
	private final String propertyName;
	private final String group;
	private final String value;

	/**
	 * Constructor for AggregatedProperty.
	 *
	 * @param defaultName The default name of the property.
	 * @param propertyName The name of the property.
	 * @param value The code of the control.
	 */
	AggregatedProperty(String defaultName, String propertyName, String value) {
		this.propertyName = defaultName;
		this.group = propertyName;
		this.value = value;
	}

	/**
	 * Retrieves {@link #propertyName}
	 *
	 * @return value of {@link #propertyName}
	 */
	public String getPropertyName() {
		return propertyName;
	}

	/**
	 * Retrieves {@link #group}
	 *
	 * @return value of {@link #group}
	 */
	public String getGroup() {
		return group;
	}

	/**
	 * Retrieves {@link #value}
	 *
	 * @return value of {@link #value}
	 */
	public String getValue() {
		return value;
	}

	/**
	 * Retrieves a list of aggregated properties filtered by type.
	 *
	 * @param type The type of aggregated properties to retrieve.
	 * @return A list of aggregated properties with the specified type.
	 */
	public static List<AggregatedProperty> getListByType(String type) {
		return Arrays.stream(AggregatedProperty.values()).filter(item -> item.getGroup().equals(type))
				.collect(Collectors.toList());
	}

	/**
	 * Retrieves an aggregated property by its default name, ignoring case.
	 *
	 * @param name The default name of the aggregated property to retrieve.
	 * @return The aggregated property with the specified default name, or null if not found.
	 */
	public static AggregatedProperty getByDefaultName(String name) {
		Optional<AggregatedProperty> property = Arrays.stream(AggregatedProperty.values()).filter(item -> item.getPropertyName().equalsIgnoreCase(name)).findFirst();
		return property.orElse(null);
	}
}
