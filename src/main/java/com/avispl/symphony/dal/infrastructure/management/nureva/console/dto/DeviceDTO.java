/*
 *  Copyright (c) 2024 AVI-SPL, Inc. All Rights Reserved.
 */

package com.avispl.symphony.dal.infrastructure.management.nureva.console.dto;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents a device with its properties.
 *
 * @author Harry / Symphony Dev Team<br>
 * Created on 3/25/2024
 * @since 1.0.0
 */
public class DeviceDTO {
	private String organizationId;
	private String id;
	private String room;

	/**
	 * Constructs a DeviceDTO object with the specified organization ID, ID, and room.
	 *
	 * @param organizationId The organization ID of the device.
	 * @param id The ID of the device.
	 * @param room The room associated with the device.
	 */
	public DeviceDTO(String organizationId, String id, String room) {
		this.organizationId = organizationId;
		this.id = id;
		this.room = room;
	}

	/**
	 * Default constructor.
	 */
	public DeviceDTO() {
	}

	/**
	 * Retrieves {@link #id}
	 *
	 * @return value of {@link #id}
	 */
	public String getId() {
		return id;
	}

	/**
	 * Sets {@link #id} value
	 *
	 * @param id new value of {@link #id}
	 */
	public void setId(String id) {
		this.id = id;
	}

	/**
	 * Retrieves {@link #room}
	 *
	 * @return value of {@link #room}
	 */
	public String getRoom() {
		return room;
	}

	/**
	 * Sets {@link #room} value
	 *
	 * @param room new value of {@link #room}
	 */
	public void setRoom(String room) {
		this.room = room;
	}

	/**
	 * Retrieves {@link #organizationId}
	 *
	 * @return value of {@link #organizationId}
	 */
	public String getOrganizationId() {
		return organizationId;
	}

	/**
	 * Sets {@link #organizationId} value
	 *
	 * @param organizationId new value of {@link #organizationId}
	 */
	public void setOrganizationId(String organizationId) {
		this.organizationId = organizationId;
	}

	/**
	 * Counts the number of distinct rooms among a list of devices.
	 *
	 * @param devices The list of DeviceDTO objects.
	 * @return The number of distinct rooms.
	 */
	public static int countDistinctRooms(List<DeviceDTO> devices) {
		Set<String> uniqueRooms = devices.stream().map(DeviceDTO::getRoom).collect(Collectors.toSet());
		return uniqueRooms.size();
	}
}
