/*
 *  Copyright (c) 2024 AVI-SPL, Inc. All Rights Reserved.
 */

package com.avispl.symphony.dal.infrastructure.management.nureva.console.dto;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * DeviceDTO
 *
 * @author Harry / Symphony Dev Team<br>
 * Created on 3/25/2024
 * @since 1.0.0
 */
public class DeviceDTO {
	private String organizationId;
	private String id;
	private String room;

	public DeviceDTO(String organizationId,String id, String room) {
		this.organizationId = organizationId;
		this.id = id;
		this.room = room;
	}

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
	 *
	 * @param devices
	 * @return
	 */
	public static int countDistinctRooms(List<DeviceDTO> devices) {
		Set<String> uniqueRooms = devices.stream().map(DeviceDTO::getRoom).collect(Collectors.toSet());
		return uniqueRooms.size();
	}
}
