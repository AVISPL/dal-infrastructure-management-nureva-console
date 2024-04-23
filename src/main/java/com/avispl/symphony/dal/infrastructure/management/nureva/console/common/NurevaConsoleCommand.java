/*
 *  Copyright (c) 2024 AVI-SPL, Inc. All Rights Reserved.
 */

package com.avispl.symphony.dal.infrastructure.management.nureva.console.common;

/**
 * Class containing constants for Nureva console commands.
 *
 * @author Harry / Symphony Dev Team<br>
 * Created on 3/27/2024
 * @since 1.0.0
 */
public class NurevaConsoleCommand {
	public static final String AUTHENTICATION_COMMAND = "api/v1/tokens";
	public static final String ORGANIZATION_COMMAND = "api/v1/users/me/organizations";
	public static final String ALL_DEVICE_ID_COMMAND = "api/v1/organizations/%s/devices?start=%s&count=%s";
	public static final String DEVICE_INFO_COMMAND = "api/v2/devices/%s/systemInformation";
	public static final String LATEST_SETTINGS_INFO_COMMAND = "api/v1/organizations/%s/devices/%s/settings/latest";
	public static final String CREATE_CONTROL_COMMAND = "api/v1/deviceCommands";
	public static final String GET_STATUS_CONTROL_COMMAND = "api/v1/deviceCommands/%s";
	public static final String GET_LATEST_FIRMWARE_VERSION = "api/v1/versions/firmware/%s/latest";
}
