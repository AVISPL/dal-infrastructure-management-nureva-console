/*
 *  Copyright (c) 2024 AVI-SPL, Inc. All Rights Reserved.
 */

package com.avispl.symphony.dal.infrastructure.management.nureva.console;

import java.util.Map;

import org.junit.Assert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;

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

	@Test
	void testGetAggregatorData() throws Exception {
		extendedStatistic = (ExtendedStatistics) nurevaConsoleCommunicator.getMultipleStatistics().get(0);
		Map<String, String> statistics = extendedStatistic.getStatistics();
		Assert.assertEquals(2, statistics.size());
	}
}
