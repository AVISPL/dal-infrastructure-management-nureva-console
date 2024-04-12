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
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.util.CollectionUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.security.auth.login.FailedLoginException;

import com.avispl.symphony.api.dal.control.Controller;
import com.avispl.symphony.api.dal.dto.control.AdvancedControllableProperty;
import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;
import com.avispl.symphony.api.dal.dto.monitor.Statistics;
import com.avispl.symphony.api.dal.dto.monitor.aggregator.AggregatedDevice;
import com.avispl.symphony.api.dal.error.ResourceNotReachableException;
import com.avispl.symphony.api.dal.monitor.Monitorable;
import com.avispl.symphony.api.dal.monitor.aggregator.Aggregator;
import com.avispl.symphony.dal.communicator.RestCommunicator;
import com.avispl.symphony.dal.infrastructure.management.nureva.console.common.AggregatedProperty;
import com.avispl.symphony.dal.infrastructure.management.nureva.console.common.AggregatedTypeEnum;
import com.avispl.symphony.dal.infrastructure.management.nureva.console.common.NurevaConsoleCommand;
import com.avispl.symphony.dal.infrastructure.management.nureva.console.common.NurevaConsoleConstant;
import com.avispl.symphony.dal.infrastructure.management.nureva.console.common.PingMode;
import com.avispl.symphony.dal.infrastructure.management.nureva.console.dto.DeviceDTO;
import com.avispl.symphony.dal.util.StringUtils;

/**
 * NurevaConsoleCommunicator
 * Supported features are:
 * Monitoring Aggregator Device:
 *  <ul>
 *  <li> - FirmwareWarnings</li>
 *  <li> - NumberOfConsoleRooms</li>
 *  <li> - NumberOfDevices</li>
 *  <ul>
 *
 * General Info Aggregated Device:
 * <ul>
 * <li> - ConsoleRoomName</li>
 * <li> - deviceId</li>
 * <li> - deviceName</li>
 * <li> - deviceOnline</li>
 * <li> - DeviceStatus</li>
 * <li> - FirmwareCurrentVersion</li>
 * <li> - FirmwareUpdateAvailable</li>
 * <li> - FirmwareUpdateVersion</li>
 * <li> - MachineName</li>
 * </ul>
 *
 * Hardware Component Group:
 * <ul>
 * <li> - Model</li>
 * <li> - PhysicalId(ppm)</li>
 * <li> - PortNumber</li>
 * </ul>
 *
 * Settings Group:
 * <ul>
 * <li> - ActiveZoneControl</li>
 * <li> - ActiveZoneLength(ft)</li>
 * <li> - ActiveZoneType</li>
 * <li> - ActiveZoneWidth(ft)</li>
 * <li> - AuxiliaryOutputState</li>
 * <li> - MicrophoneGain</li>
 * <li> - MicrophonePickupState</li>
 * <li> - ReductionEchoLevel</li>
 * <li> - ReductionNoiseLevel</li>
 * <li> - SleepMode</li>
 * <li> - SpeakerBassLevel</li>
 * <li> - SpeakerTrebleLevel</li>
 * <li> - VoiceAmplification</li>
 * <li> - VoiceAmplificationAuxInLevel</li>
 * <li> - VoiceAmplificationLevel</li>
 * </ul>
 *
 * @author Harry / Symphony Dev Team<br>
 * Created on 04/05/2024
 * @since 1.0.0
 */
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
	 * Aggregator inactivity timeout. If the {@link NurevaConsoleCommunicator#retrieveMultipleStatistics()}  method is not
	 * called during this period of time - device is considered to be paused, thus the Cloud API
	 * is not supposed to be called
	 */
	private static final long retrieveStatisticsTimeOut = 3 * 60 * 1000;

	/**
	 * Update the status of the device.
	 * The device is considered as paused if did not receive any retrieveMultipleStatistics()
	 * calls during {@link NurevaConsoleCommunicator}
	 */
	private synchronized void updateAggregatorStatus() {
		devicePaused = validRetrieveStatisticsTimestamp < System.currentTimeMillis();
	}

	/**
	 * Uptime time stamp to valid one
	 */
	private synchronized void updateValidRetrieveStatisticsTimestamp() {
		validRetrieveStatisticsTimestamp = System.currentTimeMillis() + retrieveStatisticsTimeOut;
		updateAggregatorStatus();
	}

	/**
	 * A mapper for reading and writing JSON using Jackson library.
	 * ObjectMapper provides functionality for converting between Java objects and JSON.
	 * It can be used to serialize objects to JSON format, and deserialize JSON data to objects.
	 */
	ObjectMapper objectMapper = new ObjectMapper();

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

	/**
	 * List of aggregated device
	 */
	private List<AggregatedDevice> aggregatedDeviceList = Collections.synchronizedList(new ArrayList<>());

	/**
	 * Cached data
	 */
	private Map<String, Map<String, String>> cachedMonitoringDevice = Collections.synchronizedMap(new HashMap<>());

	/**
	 * list of organizations
	 */
	private List<String> organizations = Collections.synchronizedList(new ArrayList<>());

	/**
	 * map of the latest firmware version by type
	 */
	private Map<String, String> latestFirmwareMapping = Collections.synchronizedMap(new HashMap<>());

	/**
	 * list of all devices
	 */
	private List<DeviceDTO> deviceList = Collections.synchronizedList(new ArrayList<>());

	/**
	 * count firmware warnings
	 */
	private Integer firmwareWarnings;

	/**
	 * token for header
	 */
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

	/**
	 * start index
	 */
	private int startIndex = NurevaConsoleConstant.START_INDEX;

	/**
	 * end index
	 */
	private int endIndex = NurevaConsoleConstant.NUMBER_DEVICE_IN_INTERVAL;

	/**
	 * ping mode
	 */
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
			retrieveLatestFirmwareVersion();
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
		reentrantLock.lock();
		try {
			String property = controllableProperty.getProperty();
			String deviceId = controllableProperty.getDeviceId();
			String value = String.valueOf(controllableProperty.getValue());

			String[] propertyList = property.split(NurevaConsoleConstant.HASH);
			String propertyName = property;
			if (property.contains(NurevaConsoleConstant.HASH)) {
				propertyName = propertyList[1];
			}
			Optional<AggregatedDevice> aggregatedDevice = aggregatedDeviceList.stream().filter(item -> item.getDeviceId().equals(deviceId)).findFirst();
			if (aggregatedDevice.isPresent()) {
				AggregatedProperty item = AggregatedProperty.getByDefaultName(propertyName);
				if (item == null) {
					throw new IllegalArgumentException("Error when control. Can't find property name.");
				}
				String attributes = item.getValue();
				switch (item) {
					case SPEAKER_TREBLE_LEVEL:
					case SPEAKER_BASS_LEVEL:
						value = String.valueOf((int) Float.parseFloat(value));
						sendControlCommand(deviceId, propertyName, attributes, Integer.parseInt(value));
						updateCachedValue(deviceId, propertyName, value);
						break;
					case ACTIVE_ZONE_CONTROL:
						String status = NurevaConsoleConstant.NUMBER_ONE.equalsIgnoreCase(value) ? NurevaConsoleConstant.TRUE : NurevaConsoleConstant.FALSE;
						sendControlCommand(deviceId, propertyName, attributes, Boolean.parseBoolean(status));
						updateCachedValue(deviceId, propertyName, status);
						break;
					case ACTIVE_ZONE_TYPE:
						status = NurevaConsoleConstant.NUMBER_ONE.equalsIgnoreCase(value) ? "\"Full\"" : "\"Partial\"";
						sendControlCommand(deviceId, propertyName, attributes, status);
						updateCachedValue(deviceId, propertyName, status);
						break;
					default:
						if (logger.isWarnEnabled()) {
							logger.warn(String.format("Unable to execute %s command on device %s: Not Supported", property, deviceId));
						}
						break;
				}
			}
		} finally {
			reentrantLock.unlock();
		}
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
		if (!checkValidApiToken()) {
			throw new FailedLoginException("Please enter valid password and username field.");
		}
		if (executorService == null) {
			executorService = Executors.newFixedThreadPool(1);
			executorService.submit(deviceDataLoader = new NurevaConsoleDataLoader());
		}
		nextDevicesCollectionIterationTimestamp = System.currentTimeMillis();
		updateValidRetrieveStatisticsTimestamp();
		if (cachedMonitoringDevice.isEmpty()) {
			return Collections.emptyList();
		}
		return cloneAndPopulateAggregatedDeviceList();
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
		aggregatedDeviceList.clear();
		cachedMonitoringDevice.clear();
		latestFirmwareMapping.clear();
		organizations.clear();
		firmwareWarnings = null;
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
	 * Retrieves the latest firmware version for each aggregated type from the Nureva console.
	 * This method iterates over all aggregated types and sends a request to retrieve the latest firmware version
	 * for each type. If a valid response containing the firmware version is received, it is stored in the
	 * latestFirmwareMapping map.
	 * If an error occurs during the retrieval process, it is logged with the corresponding request information.
	 */
	private void retrieveLatestFirmwareVersion() {
		String request = NurevaConsoleConstant.EMPTY;
		for (AggregatedTypeEnum item : AggregatedTypeEnum.values()) {
			try {
				request = String.format(NurevaConsoleCommand.GET_LATEST_FIRMWARE_VERSION, item.getName());
				JsonNode response = this.doGet(request, JsonNode.class);
				if (response != null && response.has("version")) {
					latestFirmwareMapping.put(item.getName(), response.get("version").asText());
				}
			} catch (Exception e) {
				logger.error("Error when retrieve request " + request);
			}
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
		stats.put("NumberOfConsoleRooms", String.valueOf(DeviceDTO.countDistinctRooms(deviceList)));
		if (firmwareWarnings != null) {
			stats.put("FirmwareWarnings", String.valueOf(firmwareWarnings));
		}
	}

	/**
	 * Populates device details by making a POST request to retrieve information from NurevaConsole.
	 * The method clears the existing aggregated device list, processes the response, and updates the list accordingly.
	 * Any error during the process is logged.
	 */
	private void populateDeviceDetails() {
		int numberOfThreads = getDefaultNumberOfThread();
		ExecutorService executorServiceForRetrieveAggregatedData = Executors.newFixedThreadPool(numberOfThreads);
		List<Future<?>> futures = new ArrayList<>();

		if (endIndex > deviceList.size()) {
			endIndex = deviceList.size();
		}
		synchronized (deviceList) {
			for (int i = startIndex; i < endIndex; i++) {
				int index = i;
				Future<?> future = executorServiceForRetrieveAggregatedData.submit(() -> processDeviceId(deviceList.get(index).getOrganizationId(), deviceList.get(index).getId()));
				futures.add(future);
			}
		}
		waitForFutures(futures, executorServiceForRetrieveAggregatedData);
		executorServiceForRetrieveAggregatedData.shutdown();
		if (endIndex == deviceList.size()) {
			startIndex = NurevaConsoleConstant.START_INDEX;
			endIndex = NurevaConsoleConstant.NUMBER_DEVICE_IN_INTERVAL;
		} else {
			startIndex = endIndex;
			endIndex += NurevaConsoleConstant.NUMBER_DEVICE_IN_INTERVAL;
		}
	}

	/**
	 * Clones and populates a new list of aggregated devices with mapped monitoring properties.
	 *
	 * @return A new list of {@link AggregatedDevice} objects with mapped monitoring properties.
	 */
	private List<AggregatedDevice> cloneAndPopulateAggregatedDeviceList() {
		synchronized (aggregatedDeviceList) {
			aggregatedDeviceList.clear();
			firmwareWarnings = 0;
			cachedMonitoringDevice.forEach((key, value) -> {
				AggregatedDevice aggregatedDevice = new AggregatedDevice();
				Map<String, String> cachedData = cachedMonitoringDevice.get(key);
				String deviceName = cachedData.get(AggregatedProperty.DEVICE_NAME.getPropertyName());
				String deviceStatus = cachedData.get(AggregatedProperty.DEVICE_STATUS.getPropertyName());
				aggregatedDevice.setDeviceId(key);
				aggregatedDevice.setDeviceOnline(false);
				if (deviceStatus != null) {
					aggregatedDevice.setDeviceOnline("Online".equals(deviceStatus));
				}
				if (deviceName != null) {
					aggregatedDevice.setDeviceName(deviceName.toUpperCase());
				}
				Map<String, String> stats = new HashMap<>();
				List<AdvancedControllableProperty> advancedControllableProperties = new ArrayList<>();
				populateMonitorProperties(key, cachedData, stats, advancedControllableProperties);
				aggregatedDevice.setProperties(stats);
				aggregatedDevice.setControllableProperties(advancedControllableProperties);
				aggregatedDeviceList.add(aggregatedDevice);
			});
		}
		return aggregatedDeviceList;
	}

	/**
	 * Waits for the completion of all futures in the provided list and then shuts down the executor service.
	 *
	 * @param futures The list of Future objects representing asynchronous tasks.
	 * @param executorService The ExecutorService to be shut down.
	 */
	private void waitForFutures(List<Future<?>> futures, ExecutorService executorService) {
		for (Future<?> future : futures) {
			try {
				future.get();
			} catch (Exception e) {
				logger.error("An exception occurred while waiting for a future to complete.", e);
			}
		}
		executorService.shutdown();
	}

	/**
	 * Processes device information for a specific organization and device ID.
	 * This method retrieves device information and settings for the given device ID.
	 *
	 * @param orgId The ID of the organization associated with the device.
	 * @param deviceId The ID of the device to process.
	 */
	private void processDeviceId(String orgId, String deviceId) {
		retrieveDeviceInfo(deviceId);
		retrieveDeviceSettings(orgId, deviceId);
	}

	/**
	 * Retrieves information about a device with the specified device ID.
	 *
	 * @param deviceId The ID of the device to retrieve information for.
	 */
	private void retrieveDeviceInfo(String deviceId) {
		try {
			JsonNode response = this.doGet(String.format(NurevaConsoleCommand.DEVICE_INFO_COMMAND, deviceId), JsonNode.class);
			if (response != null) {
				Map<String, String> mappingValue = new HashMap<>();
				List<AggregatedProperty> deviceInfoList = AggregatedProperty.getListByType(NurevaConsoleConstant.INFORMATION);
				for (AggregatedProperty item : deviceInfoList) {
					String value = NurevaConsoleConstant.EMPTY;
					JsonNode itemValueNode = response.get(item.getValue());
					if (itemValueNode != null) {
						if (itemValueNode.isArray()) {
							value = itemValueNode.toString();
						} else {
							value = itemValueNode.asText();
						}
					}
					mappingValue.put(item.getPropertyName(), value);
				}
				putMapIntoCachedData(deviceId, mappingValue);
			}
		} catch (Exception e) {
			logger.error(String.format("Error when retrieve device info by id %s", deviceId), e);
		}
	}

	/**
	 * Retrieves settings information for a device associated with a specific organization and device ID.
	 *
	 * @param orgId The ID of the organization associated with the device.
	 * @param deviceId The ID of the device to retrieve settings for.
	 */
	private void retrieveDeviceSettings(String orgId, String deviceId) {
		try {
			JsonNode response = this.doGet(String.format(NurevaConsoleCommand.LATEST_SETTINGS_INFO_COMMAND, orgId, deviceId), JsonNode.class);
			if (response != null) {
				Map<String, String> mappingValue = new HashMap<>();
				List<AggregatedProperty> deviceInfoList = AggregatedProperty.getListByType(NurevaConsoleConstant.SETTINGS);
				for (AggregatedProperty item : deviceInfoList) {
					String value = NurevaConsoleConstant.EMPTY;
					if (response.has(item.getValue())) {
						value = response.get(item.getValue()).asText();
					}
					mappingValue.put(item.getPropertyName(), value);
				}
				putMapIntoCachedData(deviceId, mappingValue);
			}
		} catch (Exception e) {
			logger.error(String.format("Error when retrieve device info by id %s", deviceId), e);
		}
	}

	/**
	 * Populates monitor properties based on the cached data, statistics map, and list of advanced controllable properties.
	 *
	 * @param cached The cached data map.
	 * @param stats The statistics map to populate.
	 * @param advancedControllableProperties The list of advanced controllable properties.
	 */
	private void populateMonitorProperties(String key, Map<String, String> cached, Map<String, String> stats, List<AdvancedControllableProperty> advancedControllableProperties) {
		for (AggregatedProperty item : AggregatedProperty.values()) {
			String propertyName = item.getPropertyName();
			if (NurevaConsoleConstant.SETTINGS.equalsIgnoreCase(item.getGroup())) {
				propertyName = item.getGroup() + NurevaConsoleConstant.HASH + item.getPropertyName();
			}
			String value = getDefaultValueForNullData(cached.get(item.getPropertyName()));
			if (NurevaConsoleConstant.NONE.equalsIgnoreCase(value) && !item.equals(AggregatedProperty.ROOM_NAME)) {
				continue;
			}
			switch (item) {
				case ROOM_NAME:
					Optional<String> roomNameOptional = deviceList.stream().filter(device -> device.getId().equals(key))
							.map(DeviceDTO::getRoom).findFirst();
					stats.put(propertyName, roomNameOptional.orElse(NurevaConsoleConstant.NONE));
					break;
				case DEVICE_NAME:
					break;
				case FIRMWARE_VERSION:
					stats.put(propertyName, value);
					String deviceType = AggregatedTypeEnum.getType(cached.get(AggregatedProperty.DEVICE_NAME.getPropertyName()));
					if (!NurevaConsoleConstant.NONE.equalsIgnoreCase(deviceType)) {
						String latestFirmware = getDefaultValueForNullData(latestFirmwareMapping.get(deviceType));
						if (!NurevaConsoleConstant.NONE.equalsIgnoreCase(latestFirmware)) {
							String availableFirmware = NurevaConsoleConstant.FALSE;
							if (!value.equalsIgnoreCase(latestFirmware)) {
								availableFirmware = NurevaConsoleConstant.TRUE;
								stats.put("FirmwareUpdateVersion", latestFirmware);
								firmwareWarnings++;
							}
							stats.put("FirmwareUpdateAvailable", availableFirmware);
						}
					}
					break;
				case AUX_IN_LEVEL:
					if ("Mic".equalsIgnoreCase(value) || "Line".equalsIgnoreCase(value)) {
						value = value.concat(" Level");
					}
					stats.put(propertyName, value);
					break;
				case SPEAKER_TREBLE_LEVEL:
				case SPEAKER_BASS_LEVEL:
					addAdvancedControlProperties(advancedControllableProperties, stats,
							createSlider(stats, propertyName, "0", "100", 0f, 100f, Float.parseFloat(value)), value);
					stats.put(propertyName + NurevaConsoleConstant.CURRENT_VALUE, value);
					break;
				case ACTIVE_ZONE_CONTROL:
					int status = NurevaConsoleConstant.TRUE.equalsIgnoreCase(value) ? 1 : 0;
					addAdvancedControlProperties(advancedControllableProperties, stats, createSwitch(propertyName, status, NurevaConsoleConstant.OFF, NurevaConsoleConstant.ON), String.valueOf(status));
					break;
				case ACTIVE_ZONE_TYPE:
					if (NurevaConsoleConstant.TRUE.equalsIgnoreCase(cached.get("ActiveZoneControl"))) {
						status = NurevaConsoleConstant.FULL.equalsIgnoreCase(value) ? 1 : 0;
						addAdvancedControlProperties(advancedControllableProperties, stats, createSwitch(propertyName, status, NurevaConsoleConstant.PARTIAL, NurevaConsoleConstant.FULL), String.valueOf(status));
					} else {
						stats.put(propertyName, value);
					}
					break;
				case HARDWARE_COMPONENTS:
					try {
						JsonNode hardwareComponents = objectMapper.readTree(value);
						if (hardwareComponents.isArray()) {
							int index = 0;
							for (JsonNode node : hardwareComponents) {
								index++;
								String group = "HardwareComponent" + index + NurevaConsoleConstant.HASH;
								if (node.has(NurevaConsoleConstant.MODEL)) {
									stats.put(group + "Model", getDefaultValueForNullData(node.get(NurevaConsoleConstant.MODEL).asText()));
								}
								if (node.has(NurevaConsoleConstant.PHYSICAL_ID)) {
									stats.put(group + "PhysicalId", getDefaultValueForNullData(node.get(NurevaConsoleConstant.PHYSICAL_ID).asText()));
								}
								if (node.has(NurevaConsoleConstant.PORT_NUMBER)) {
									stats.put(group + "PortNumber", getDefaultValueForNullData(node.get(NurevaConsoleConstant.PORT_NUMBER).asText()));
								}
							}
						}
					} catch (Exception e) {
						logger.error("Error while populate Hardware Components", e);
					}
					break;
				default:
					if (NurevaConsoleConstant.TRUE.equalsIgnoreCase(value)) {
						value = "Enable";
					} else if (NurevaConsoleConstant.FALSE.equalsIgnoreCase(value)) {
						value = "Disable";
					}
					stats.put(propertyName, uppercaseFirstCharacter(value));
			}
		}
	}

	/**
	 * Puts the provided mapping values into the cached monitoring data for the specified device ID.
	 *
	 * @param deviceId The ID of the device.
	 * @param mappingValue The mapping values to be added.
	 */
	private void putMapIntoCachedData(String deviceId, Map<String, String> mappingValue) {
		synchronized (cachedMonitoringDevice) {
			Map<String, String> map = new HashMap<>();
			if (cachedMonitoringDevice.get(deviceId) != null) {
				map = cachedMonitoringDevice.get(deviceId);
			}
			map.putAll(mappingValue);
			cachedMonitoringDevice.put(deviceId, map);
		}
	}

	/**
	 * Sends a control command to a specific device with the given attributes and value.
	 *
	 * @param deviceId The ID of the device to send the control command to.
	 * @param name The name of the control command.
	 * @param attributes The attributes of the control command.
	 * @param value The value of the control command.
	 * @throws IllegalArgumentException If there is an issue while creating or retrieving the control command,
	 * or if the response indicates failure.
	 */
	private void sendControlCommand(String deviceId, String name, String attributes, Object value) {
		String body = "{\n"
				+ "  \"type\": \"SetControlSettings\",\n"
				+ "  \"version\": 1,\n"
				+ "  \"payload\": {\n"
				+ "    \"attributes\": {\n"
				+ "      \"%s\": %s\n"
				+ "    }\n"
				+ "  },\n"
				+ "  \"deviceIds\": [\n"
				+ "    \"%s\"\n"
				+ "  ]\n"
				+ "}";
		try {
			String bodyRequest = String.format(body, attributes, value, deviceId);
			JsonNode commandResponse = this.doPost(NurevaConsoleCommand.CREATE_CONTROL_COMMAND, bodyRequest, JsonNode.class);
			if (commandResponse != null && commandResponse.has(NurevaConsoleConstant.ID)) {
				String commandId = commandResponse.get(NurevaConsoleConstant.ID).asText();
				JsonNode commandStatus = this.doGet(String.format(NurevaConsoleCommand.GET_STATUS_CONTROL_COMMAND, commandId), JsonNode.class);
				if (commandStatus != null && commandStatus.has(NurevaConsoleConstant.DEVICES)) {
					JsonNode deviceNode = commandStatus.get(NurevaConsoleConstant.DEVICES).get(0);
					if (!deviceNode.has(NurevaConsoleConstant.HAS_FAILED) || NurevaConsoleConstant.TRUE.equalsIgnoreCase(deviceNode.get(NurevaConsoleConstant.HAS_FAILED).asText())) {
						throw new IllegalArgumentException("The response returns failure");
					}
				} else {
					throw new IllegalArgumentException("Cannot get status for the control command.");
				}
			} else {
				throw new IllegalArgumentException("Cannot create control command.");
			}
		} catch (Exception e) {
			throw new IllegalArgumentException(String.format("Can't control %s with value is %s. %s", name, value, e.getMessage()));
		}
	}

	/**
	 * Updates the cached value for a specific device with the given name and value.
	 *
	 * @param deviceId The identifier of the device.
	 * @param name The name associated with the value.
	 * @param value The new value to be updated.
	 */
	private void updateCachedValue(String deviceId, String name, String value) {
		cachedMonitoringDevice.computeIfPresent(deviceId, (key, map) -> {
			map.put(name, value);
			return map;
		});
	}

	/**
	 * Gets the default number of threads based on the provided input or a default constant value.
	 *
	 * @return The default number of threads.
	 */
	private int getDefaultNumberOfThread() {
		int result;
		try {
			if (StringUtils.isNotNullOrEmpty(numberThreads)) {
				result = Integer.parseInt(numberThreads);
			} else {
				result = NurevaConsoleConstant.DEFAULT_NUMBER_THREAD;
			}
		} catch (Exception e) {
			result = NurevaConsoleConstant.DEFAULT_NUMBER_THREAD;
		}
		return result;
	}

	/**
	 * capitalize the first character of the string
	 *
	 * @param input input string
	 * @return string after fix
	 */
	private String uppercaseFirstCharacter(String input) {
		char firstChar = input.charAt(0);
		return Character.toUpperCase(firstChar) + input.substring(1);
	}

	/**
	 * check value is null or empty
	 *
	 * @param value input value
	 * @return value after checking
	 */
	private String getDefaultValueForNullData(String value) {
		return StringUtils.isNotNullOrEmpty(value) && !NurevaConsoleConstant.NULL.equalsIgnoreCase(value) ? value : NurevaConsoleConstant.NONE;
	}

	/**
	 * Add addAdvancedControlProperties if advancedControllableProperties different empty
	 *
	 * @param advancedControllableProperties advancedControllableProperties is the list that store all controllable properties
	 * @param stats store all statistics
	 * @param property the property is item advancedControllableProperties
	 * @throws IllegalStateException when exception occur
	 */
	private void addAdvancedControlProperties(List<AdvancedControllableProperty> advancedControllableProperties, Map<String, String> stats, AdvancedControllableProperty property, String value) {
		if (property != null) {
			for (AdvancedControllableProperty controllableProperty : advancedControllableProperties) {
				if (controllableProperty.getName().equals(property.getName())) {
					advancedControllableProperties.remove(controllableProperty);
					break;
				}
			}
			if (StringUtils.isNotNullOrEmpty(value)) {
				stats.put(property.getName(), value);
			} else {
				stats.put(property.getName(), NurevaConsoleConstant.EMPTY);
			}
			advancedControllableProperties.add(property);
		}
	}

	/***
	 * Create AdvancedControllableProperty slider instance
	 *
	 * @param stats extended statistics
	 * @param name name of the control
	 * @param initialValue initial value of the control
	 * @return AdvancedControllableProperty slider instance
	 */
	private AdvancedControllableProperty createSlider(Map<String, String> stats, String name, String labelStart, String labelEnd, Float rangeStart, Float rangeEnd, Float initialValue) {
		stats.put(name, initialValue.toString());
		AdvancedControllableProperty.Slider slider = new AdvancedControllableProperty.Slider();
		slider.setLabelStart(labelStart);
		slider.setLabelEnd(labelEnd);
		slider.setRangeStart(rangeStart);
		slider.setRangeEnd(rangeEnd);

		return new AdvancedControllableProperty(name, new Date(), slider, initialValue);
	}

	/**
	 * Create switch is control property for metric
	 *
	 * @param name the name of property
	 * @param status initial status (0|1)
	 * @return AdvancedControllableProperty switch instance
	 */
	private AdvancedControllableProperty createSwitch(String name, int status, String labelOff, String labelOn) {
		AdvancedControllableProperty.Switch toggle = new AdvancedControllableProperty.Switch();
		toggle.setLabelOff(labelOff);
		toggle.setLabelOn(labelOn);

		AdvancedControllableProperty advancedControllableProperty = new AdvancedControllableProperty();
		advancedControllableProperty.setName(name);
		advancedControllableProperty.setValue(status);
		advancedControllableProperty.setType(toggle);
		advancedControllableProperty.setTimestamp(new Date());

		return advancedControllableProperty;
	}
}
