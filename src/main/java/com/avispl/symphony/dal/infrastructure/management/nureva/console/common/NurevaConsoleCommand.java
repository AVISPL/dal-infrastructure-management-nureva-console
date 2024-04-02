/*
 *  Copyright (c) 2024 AVI-SPL, Inc. All Rights Reserved.
 */

package com.avispl.symphony.dal.infrastructure.management.nureva.console.common;

/**
 * NurevaConsoleCommand
 *
 * @author Harry / Symphony Dev Team<br>
 * Created on 3/27/2024
 * @since 1.0.0
 */
public class NurevaConsoleCommand {
	public static final String AUTHENTICATION_COMMAND = "api/v1/tokens";
	public static final String ORGANIZATION_COMMAND = "api/v1/users/me/organizations";
	public static final String ALL_DEVICE_ID_COMMAND = "api/v1/organizations/%s/devices?start=%s&count=%s";
}
