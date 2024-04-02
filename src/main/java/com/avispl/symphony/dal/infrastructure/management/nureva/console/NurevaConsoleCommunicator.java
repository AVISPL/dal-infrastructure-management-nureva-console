/*
 *  Copyright (c) 2024 AVI-SPL, Inc. All Rights Reserved.
 */

package com.avispl.symphony.dal.infrastructure.management.nureva.console;

import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.util.CollectionUtils;

import com.fasterxml.jackson.databind.JsonNode;
import javax.security.auth.login.FailedLoginException;

import com.avispl.symphony.api.dal.control.Controller;
import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;
import com.avispl.symphony.api.dal.dto.monitor.Statistics;
import com.avispl.symphony.api.dal.dto.monitor.aggregator.AggregatedDevice;
import com.avispl.symphony.api.dal.error.ResourceNotReachableException;
import com.avispl.symphony.api.dal.monitor.Monitorable;
import com.avispl.symphony.api.dal.monitor.aggregator.Aggregator;
import com.avispl.symphony.dal.communicator.RestCommunicator;
import com.avispl.symphony.dal.infrastructure.management.nureva.console.common.NurevaConsoleCommand;
import com.avispl.symphony.dal.infrastructure.management.nureva.console.common.NurevaConsoleConstant;
import com.avispl.symphony.dal.infrastructure.management.nureva.console.common.PingMode;
import com.avispl.symphony.dal.infrastructure.management.nureva.console.dto.DeviceDTO;
import com.avispl.symphony.dal.util.StringUtils;


public class NurevaConsoleCommunicator extends RestCommunicator implements Aggregator, Monitorable, Controller {
	/**
	 * Process that is running constantly and triggers collecting data from Nureva Console SE API endpoints, based on the given timeouts and thresholds.
	 *
	 * @author Harry
	 * @since 1.0.0
	 */
	class NurevaConsoleDataLoader implements Runnable {
		private volatile boolean inProgress;
		private volatile boolean flag = false;

		public NurevaConsoleDataLoader() {
			inProgress = true;
		}

		@Override
		public void run() {
			loop:
			while (inProgress) {
				try {
					TimeUnit.MILLISECONDS.sleep(500);
				} catch (InterruptedException e) {
					// Ignore for now
				}

				if (!inProgress) {
					break loop;
				}

				// next line will determine whether Nureva Console monitoring was paused
				updateAggregatorStatus();
				if (devicePaused) {
					continue loop;
				}
				if (logger.isDebugEnabled()) {
					logger.debug("Fetching other than aggregated device list");
				}
				long currentTimestamp = System.currentTimeMillis();
				if (!flag && nextDevicesCollectionIterationTimestamp <= currentTimestamp) {
					populateDeviceDetails();
					flag = true;
				}

				while (nextDevicesCollectionIterationTimestamp > System.currentTimeMillis()) {
					try {
						TimeUnit.MILLISECONDS.sleep(1000);
					} catch (InterruptedException e) {
						//
					}
				}

				if (!inProgress) {
					break loop;
				}
				if (flag) {
					nextDevicesCollectionIterationTimestamp = System.currentTimeMillis() + 30000;
					flag = false;
				}

				if (logger.isDebugEnabled()) {
					logger.debug("Finished collecting devices statistics cycle at " + new Date());
				}
			}
			// Finished collecting
		}

		/**
		 * Triggers main loop to stop
		 */
		public void stop() {
			inProgress = false;
		}
	}

	/**
	 * Indicates whether a device is considered as paused.
	 * True by default so if the system is rebooted and the actual value is lost -> the device won't start stats
	 * collection unless the {@link NurevaConsoleCommunicator#retrieveMultipleStatistics()} method is called which will change it
	 * to a correct value
	 */
	private volatile boolean devicePaused = true;

	/**
	 * We don't want the statistics to be collected constantly, because if there's not a big list of devices -
	 * new devices' statistics loop will be launched before the next monitoring iteration. To avoid that -
	 * this variable stores a timestamp which validates it, so when the devices' statistics is done collecting, variable
	 * is set to currentTime + 30s, at the same time, calling {@link #retrieveMultipleStatistics()} and updating the
	 */
	private long nextDevicesCollectionIterationTimestamp;

	/**
	 * This parameter holds timestamp of when we need to stop performing API calls
	 * It used when device stop retrieving statistic. Updated each time of called #retrieveMultipleStatistics
	 */
	private volatile long validRetrieveStatisticsTimestamp;

	/**
	 * Update the status of the device.
	 * The device is considered as paused if did not receive any retrieveMultipleStatistics()
	 * calls during {@link NurevaConsoleCommunicator}
	 */
	private synchronized void updateAggregatorStatus() {
		devicePaused = validRetrieveStatisticsTimestamp < System.currentTimeMillis();
	}

	/**
	 * Executor that runs all the async operations, that is posting and
	 */
	private ExecutorService executorService;

	/**
	 * A private field that represents an instance of the NurevaConsoleLoader class, which is responsible for loading device data for Nureva Console
	 */
	private NurevaConsoleDataLoader deviceDataLoader;

	/**
	 * A private final ReentrantLock instance used to provide exclusive access to a shared resource
	 * that can be accessed by multiple threads concurrently. This lock allows multiple reentrant
	 * locks on the same shared resource by the same thread.
	 */
	private final ReentrantLock reentrantLock = new ReentrantLock();

	/**
	 * Private variable representing the local extended statistics.
	 */
	private ExtendedStatistics localExtendedStatistics;

	private List<String> organizations = Collections.synchronizedList(new ArrayList<>());

	private List<DeviceDTO> deviceList = Collections.synchronizedList(new ArrayList<>());

	private String token;

	/**
	 * save time get token
	 */
	private Long tokenExpire;

	/**
	 * time the token expires
	 */
	private Long expiresIn = 1500L * 1000;

	/**
	 * number of threads
	 */
	private String numberThreads;

	private PingMode pingMode = PingMode.ICMP;

	/**
	 * Retrieves {@link #pingMode}
	 *
	 * @return value of {@link #pingMode}
	 */
	public String getPingMode() {
		return pingMode.name();
	}

	/**
	 * Sets {@link #pingMode} value
	 *
	 * @param pingMode new value of {@link #pingMode}
	 */
	public void setPingMode(String pingMode) {
		this.pingMode = PingMode.ofString(pingMode);
	}

	/**
	 * Retrieves {@link #numberThreads}
	 *
	 * @return value of {@link #numberThreads}
	 */
	public String getNumberThreads() {
		return numberThreads;
	}

	/**
	 * Sets {@link #numberThreads} value
	 *
	 * @param numberThreads new value of {@link #numberThreads}
	 */
	public void setNumberThreads(String numberThreads) {
		this.numberThreads = numberThreads;
	}

	/**
	 * Constructs a new instance of NurevaConsoleCommunicator.
	 *
	 * @throws IOException If an I/O error occurs while loading the properties mapping YAML file.
	 */
	public NurevaConsoleCommunicator() throws IOException {
		this.setTrustAllCertificates(true);
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 *
	 * Check for available devices before retrieving the value
	 * ping latency information to Symphony
	 */
	@Override
	public int ping() throws Exception {
		if (this.pingMode == PingMode.ICMP) {
			return super.ping();
		} else if (this.pingMode == PingMode.TCP) {
			if (isInitialized()) {
				long pingResultTotal = 0L;

				for (int i = 0; i < this.getPingAttempts(); i++) {
					long startTime = System.currentTimeMillis();

					try (Socket puSocketConnection = new Socket(this.host, this.getPort())) {
						puSocketConnection.setSoTimeout(this.getPingTimeout());
						if (puSocketConnection.isConnected()) {
							long pingResult = System.currentTimeMillis() - startTime;
							pingResultTotal += pingResult;
							if (this.logger.isTraceEnabled()) {
								this.logger.trace(String.format("PING OK: Attempt #%s to connect to %s on port %s succeeded in %s ms", i + 1, host, this.getPort(), pingResult));
							}
						} else {
							if (this.logger.isDebugEnabled()) {
								this.logger.debug(String.format("PING DISCONNECTED: Connection to %s did not succeed within the timeout period of %sms", host, this.getPingTimeout()));
							}
							return this.getPingTimeout();
						}
					} catch (SocketTimeoutException | ConnectException tex) {
						throw new RuntimeException("Socket connection timed out", tex);
					} catch (UnknownHostException ex) {
						throw new UnknownHostException(String.format("Connection timed out, UNKNOWN host %s", host));
					} catch (Exception e) {
						if (this.logger.isWarnEnabled()) {
							this.logger.warn(String.format("PING TIMEOUT: Connection to %s did not succeed, UNKNOWN ERROR %s: ", host, e.getMessage()));
						}
						return this.getPingTimeout();
					}
				}
				return Math.max(1, Math.toIntExact(pingResultTotal / this.getPingAttempts()));
			} else {
				throw new IllegalStateException("Cannot use device class without calling init() first");
			}
		} else {
			throw new IllegalArgumentException("Unknown PING Mode: " + pingMode);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<Statistics> getMultipleStatistics() throws Exception {
		reentrantLock.lock();
		try {
			if (!checkValidApiToken()) {
				throw new FailedLoginException("API Token cannot be null or empty, please enter valid password and username field.");
			}
			Map<String, String> statistics = new HashMap<>();
			ExtendedStatistics extendedStatistics = new ExtendedStatistics();
			retrieveOrganizations();
			retrieveSystemInfo();
			populateSystemInfo(statistics);
			extendedStatistics.setStatistics(statistics);
			localExtendedStatistics = extendedStatistics;
		} finally {
			reentrantLock.unlock();
		}
		return Collections.singletonList(localExtendedStatistics);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void controlProperty(ControllableProperty controllableProperty) throws Exception {

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void controlProperties(List<ControllableProperty> controllableProperties) throws Exception {
		if (CollectionUtils.isEmpty(controllableProperties)) {
			throw new IllegalArgumentException("ControllableProperties can not be null or empty");
		}
		for (ControllableProperty p : controllableProperties) {
			try {
				controlProperty(p);
			} catch (Exception e) {
				logger.error(String.format("Error when control property %s", p.getProperty()), e);
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<AggregatedDevice> retrieveMultipleStatistics() throws Exception {
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<AggregatedDevice> retrieveMultipleStatistics(List<String> list) throws Exception {
		return retrieveMultipleStatistics().stream().filter(aggregatedDevice -> list.contains(aggregatedDevice.getDeviceId())).collect(Collectors.toList());
	}

	/**
	 * {@inheritDoc}
	 * set API Key into Header of Request
	 */
	@Override
	protected HttpHeaders putExtraRequestHeaders(HttpMethod httpMethod, String uri, HttpHeaders headers) {
		headers.setBearerAuth(token);
		return headers;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void authenticate() throws Exception {

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void internalInit() throws Exception {
		if (logger.isDebugEnabled()) {
			logger.debug("Internal init is called.");
		}
		executorService = Executors.newFixedThreadPool(1);
		executorService.submit(deviceDataLoader = new NurevaConsoleDataLoader());
		super.internalInit();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void internalDestroy() {
		if (logger.isDebugEnabled()) {
			logger.debug("Internal destroy is called.");
		}
		if (deviceDataLoader != null) {
			deviceDataLoader.stop();
			deviceDataLoader = null;
		}
		if (executorService != null) {
			executorService.shutdownNow();
			executorService = null;
		}
		if (localExtendedStatistics != null && localExtendedStatistics.getStatistics() != null && localExtendedStatistics.getControllableProperties() != null) {
			localExtendedStatistics.getStatistics().clear();
			localExtendedStatistics.getControllableProperties().clear();
		}
		nextDevicesCollectionIterationTimestamp = 0;
		super.internalDestroy();
	}

	/**
	 * Check API token validation
	 * If the token expires, we send a request to get a new token
	 *
	 * @return boolean
	 */
	private boolean checkValidApiToken() throws Exception {
		if (StringUtils.isNullOrEmpty(getLogin()) || StringUtils.isNullOrEmpty(getPassword())) {
			return false;
		}
		if (StringUtils.isNullOrEmpty(token) || System.currentTimeMillis() - tokenExpire >= expiresIn) {
			token = getToken();
		}
		return StringUtils.isNotNullOrEmpty(token);
	}

	/**
	 * Retrieves a token using the provided username and password
	 *
	 * @return the token string
	 */
	private String getToken() throws Exception {
		String accessToken = NurevaConsoleConstant.EMPTY;
		tokenExpire = System.currentTimeMillis();

		Map<String, String> valueMap = new HashMap<>();
		valueMap.put("grant_type", "client_credentials");
		valueMap.put("client_id", this.getLogin());
		valueMap.put("client_secret", this.getPassword());
		try {
			JsonNode response = this.doPost(NurevaConsoleCommand.AUTHENTICATION_COMMAND, valueMap, JsonNode.class);
			if (response != null && response.has(NurevaConsoleConstant.ACCESS_TOKEN)) {
				accessToken = response.get(NurevaConsoleConstant.ACCESS_TOKEN).asText();
			}
		} catch (Exception e) {
			throw new FailedLoginException("Failed to retrieve an access token for account with from username and password. Please username id and password");
		}
		return accessToken;
	}

	/**
	 * Retrieves organizations from the Nureva console and populates the 'organizations' list.
	 * If no organizations are found or an error occurs during the retrieval process, a ResourceNotReachableException is thrown.
	 */
	private void retrieveOrganizations() {
		try {
			JsonNode response = this.doGet(NurevaConsoleCommand.ORGANIZATION_COMMAND, JsonNode.class);
			if (response != null && response.has(NurevaConsoleConstant.ORGANIZATIONS)) {
				if (response.get(NurevaConsoleConstant.ORGANIZATIONS).size() == 0) {
					throw new ResourceNotReachableException("Error: No organizations found.");
				} else {
					organizations.clear();
					for (JsonNode item : response.get(NurevaConsoleConstant.ORGANIZATIONS)) {
						organizations.add(item.get(NurevaConsoleConstant.ID).asText());
					}
				}
			}
		} catch (Exception e) {
			logger.error("Error when retrieve system information", e);
		}
	}

	/**
	 * Retrieves system information for each organization in the 'organizations' list and populates the 'deviceList'.
	 * If an error occurs during the retrieval process, a ResourceNotReachableException is thrown.
	 */
	private void retrieveSystemInfo() {
		try {
			int start = 0;
			int count = 0;
			deviceList.clear();
			for (String item : organizations) {
				do {
					JsonNode response = sendAllDeviceIdCommand(item, start);
					if (response != null && response.has(NurevaConsoleConstant.RESULTS) && response.has(NurevaConsoleConstant.COUNT) && response.has(NurevaConsoleConstant.START)) {
						count = response.get(NurevaConsoleConstant.COUNT).asInt();
						start = response.get(NurevaConsoleConstant.START).asInt() + count;
						for (JsonNode node : response.get(NurevaConsoleConstant.RESULTS)) {
							DeviceDTO device = new DeviceDTO(item, node.get(NurevaConsoleConstant.ID).asText(), node.get("roomName").asText());
							deviceList.add(device);
						}
					}
				} while (count == NurevaConsoleConstant.DEFAULT_COUNT_PARAM);
			}
		} catch (Exception e) {
			throw new ResourceNotReachableException(String.format("Error when retrieve system information. %s", e.getMessage()), e);
		}
	}

	/**
	 * Sends a command to retrieve information about all devices associated with a specific organization starting from a given index.
	 *
	 * @param orgId The ID of the organization to retrieve device information for.
	 * @param start The index from which to start retrieving devices.
	 * @return The JSON response containing device information, or null if an error occurs during retrieval.
	 */
	private JsonNode sendAllDeviceIdCommand(String orgId, int start) {
		try {
			JsonNode response = this.doGet(String.format(NurevaConsoleCommand.ALL_DEVICE_ID_COMMAND, orgId, start, NurevaConsoleConstant.DEFAULT_COUNT_PARAM), JsonNode.class);
			if (response != null) {
				return response;
			}
		} catch (Exception e) {
			logger.error(String.format("Error when retrieve system information. %s", e.getMessage()), e);
		}
		return null;
	}

	/**
	 * Populates statistics about the system using the information stored in the 'deviceList'.
	 *
	 * @param stats A map to populate with system statistics.
	 */
	private void populateSystemInfo(Map<String, String> stats) {
		stats.put("NumberOfDevices", String.valueOf(deviceList.size()));
		stats.put("NumberOfRooms", String.valueOf(DeviceDTO.countDistinctRooms(deviceList)));
	}

	/**
	 * Populates device details by making a POST request to retrieve information from NurevaConsole.
	 * The method clears the existing aggregated device list, processes the response, and updates the list accordingly.
	 * Any error during the process is logged.
	 */
	private void populateDeviceDetails() {

	}
}
