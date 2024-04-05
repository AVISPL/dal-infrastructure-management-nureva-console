/*
 *  Copyright (c) 2024 AVI-SPL, Inc. All Rights Reserved.
 */

package com.avispl.symphony.dal.infrastructure.management.nureva.console.common;

import java.util.Arrays;
import java.util.Optional;

/**
 * Enum representing aggregated types.
 *
 * @author Harry / Symphony Dev Team<br>
 * Created on 4/3/2024
 * @since 1.0.0
 */
public enum AggregatedTypeEnum {
	HDL200("HDL200"),
	HDL300("HDL300"),
	HDL310("HDL310"),
	HDL410("HDL410"),
	CV30("CV30");

	private final String name;

	AggregatedTypeEnum(String name) {
		this.name = name;
	}

	/**
	 * Retrieves {@link #name}
	 *
	 * @return value of {@link #name}
	 */
	public String getName() {
		return name;
	}

	/**
	 * Retrieves the aggregated type corresponding to the given name.
	 *
	 * @param name The name to search for.
	 * @return The aggregated type if found, otherwise {@link NurevaConsoleConstant#NONE}.
	 */
	public static String getType(String name) {
		Optional<AggregatedTypeEnum> property = Arrays.stream(AggregatedTypeEnum.values()).filter(item -> name.toLowerCase().contains(item.getName().toLowerCase())).findFirst();
		if (property.isPresent()) {
			return property.get().getName();
		}
		return NurevaConsoleConstant.NONE;
	}
}
