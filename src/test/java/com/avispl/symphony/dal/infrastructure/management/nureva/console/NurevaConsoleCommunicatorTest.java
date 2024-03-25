/*
 *  Copyright (c) 2024 AVI-SPL, Inc. All Rights Reserved.
 */

package com.avispl.symphony.dal.infrastructure.management.nureva.console;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

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
}
