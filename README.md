# Nureva Console Integration - Capabilities & Configuration

This document covers Nureva Console Aggregator Capabilities and Configuration.

Symphony integrates with Nureva Console to provide comprehensive monitoring and control of Nureva conference audio devices, including microphones and intelligent speakers.

## Main use cases for Nureva Console Integration
- **Monitor** Nureva device health, online status, and firmware warnings across the organization
- **Track** individual device details - firmware version, device status, hardware components, and console room assignment
- **Control** audio settings such as speaker bass/treble levels, active zone configuration, microphone gain, and voice amplification
- **Inventory** Nureva conference audio devices (HDL200, HDL300, HDL310, HDL410, CV30) in check

## Prerequisites for Nureva Console Device Connection Setup
Nureva Console Aggregator communicates with the Nureva Console API on behalf of an authenticated account using a Client ID and Client Secret.

To get started:
- Log in to Nureva Console and navigate to your account/API settings
- Obtain the Client ID and Client Secret for your organization
- Use the Client ID as the Username and the Client Secret as the Password during Symphony device configuration

## Nureva Console Device Connection Setup and Provisioning

Note: The connection configuration below describes a successful Nureva Console integration setup. These should not be confused with the adapter configuration properties. They are not to be inferred as troubleshooting checks and should not be used when diagnosing specific errors unless a troubleshooting entry (provided in the Troubleshooting section) explicitly references them.

Once the Client ID and Client Secret are obtained, use them as the Username and Password for the Symphony device configuration.

Devices and models supported by the Nureva Console integration are connected conference audio devices from Nureva: HDL200, HDL300, HDL310, HDL410, and CV30.

The aggregated Nureva devices will be available as aggregated devices under the Nureva Console aggregator.

Devices that have the Nureva Console Aggregator device set as Monitoring Proxy are devices monitored by the Nureva Console Aggregator.
Create the Nureva Console device with Monitoring Service -> Advanced Monitoring, the HTTPs management protocol must be selected:

| Field | Value |
|---|---|
| Device Type | Infrastructure |
| Category | Management |
| Manufacturer | Nureva |
| Model | Console |
| Monitoring Service | Advanced Monitoring |
| Monitoring Source | Direct |
| Management Address | The hostname of Nureva Console API cloud |
| Protocol | HTTPs |
| Username | Client ID |
| Password | Client Secret |
| Port Number | 443 |

When the device is configured, saved, and set active, the Nureva Console Aggregator will start communicating with the Nureva Console API to retrieve data about registered Nureva devices based on the provided configuration.

By default, the unprovisioned devices will appear on Aggregated Devices -> Unprovisioned Devices tab.

To import a Nureva device for monitoring by the Nureva Console Aggregator:
1. Open Aggregated Devices
2. Click the (+) icon on the unprovisioned device
3. Fill in the empty values: Type (AV Devices), Category (Speaker), Manufacturer (Nureva), Model (e.g. HDL200)
4. Click the "Import" button, then click "OK" to confirm import

Once successfully imported, the device will display an up-arrow icon indicating it is actively monitored by the Nureva Console Aggregator.

Devices and available device data can be tuned by adapter configuration properties:

| Property | Description | Value |
|---|---|---|
| pingMode | Ping mode specifies the type of protocol used to test network connectivity and latency by sending a ping signal to a device. Values: TCP, ICMP | ICMP by default |

## Available Monitored Data for Nureva Console Aggregator

Nureva Console Aggregator monitored data consists of 2 parts: Aggregator extended properties and Device extended properties.

Aggregator properties include service information - NumberOfDevices, NumberOfConsoleRooms, FirmwareWarnings.

Aggregated Devices (Nureva audio devices) provide the following monitoring capabilities:

| Property Type | Description | Properties |
|---|---|---|
| Device identity | Identification and room assignment | ConsoleRoomName, deviceId, deviceName, MachineName |
| Device status | Online/offline and firmware state | deviceOnline, DeviceStatus, FirmwareCurrentVersion, FirmwareUpdateAvailable, FirmwareUpdateVersion |
| Hardware group | Physical component details (per hardware component) | Model, PhysicalId, PortNumber |
| Settings group (monitored) | Audio and zone configuration readings | ActiveZoneControl, ActiveZoneLength(ft), ActiveZoneType, ActiveZoneWidth(ft), AuxiliaryOutputState, DisplayClock, DisplayTheme, DisplayTimeFormat, MicrophoneGain, MicrophonePickupState, ReductionEchoLevel, ReductionNoiseLevel, SleepMode, SpeakerBassLevel, SpeakerBassLevelCurrentValue, SpeakerTrebleLevel, SpeakerTrebleLevelCurrentValue, VoiceAmplification, VoiceAmplificationAuxIn, VoiceAmplificationLevel |
| Settings group (controllable) | Audio settings that can be adjusted | ActiveZoneControl, ActiveZoneType, SpeakerBassLevel, SpeakerTrebleLevel |

Note: Monitoring and Control Capabilities may depend on the device model.
Note: The FirmwareUpdateVersion property will only be displayed when the value of FirmwareUpdateAvailable is true.
Note: When the ActiveZoneControl property is Off, the ActiveZoneType property will be changed to read-only.

### Property Values Reference

| Property | Possible Values |
|---|---|
| DeviceStatus | Online, Offline, Disconnected, CableUnplugged, Unavailable |
| VoiceAmplificationAuxIn | Mic Level, Line Level |
| VoiceAmplificationLevel | Low, Med, High |
| ActiveZoneWidth(ft) / ActiveZoneLength(ft) | 8 - 25 |
| AuxiliaryOutputState | Mic level, Line level, Speaker out, Mixed signal, Speaker reference |
| SpeakerTrebleLevel / SpeakerBassLevel | 0 - 100 |
| MicrophonePickupState | Mono, Stereo |
| MicrophoneGain | Low, Medium, High |
| DisplayTheme | Classic, Dot |
| DisplayTimeFormat | 12hr, 24hr |

## Troubleshooting for Nureva Console Integration

**Troubleshooting guidance**
- If an error occurs, focus only on troubleshooting steps that are provided in the section below.
- Do not include prerequisite/setup information.
- Do not add unrelated configuration details from other sections.
- If the document does not provide a direct error troubleshooting step, state that the document does not contain enough guidance for that specific issue.

**Login Error**
- Ensure the Symphony device Username field contains the correct Client ID and the Password field contains the correct Client Secret
- Verify the Management Address is set to the correct Nureva Console API cloud hostname

**API Error**
- Check the API error description
- Verify the Management Address is set to the correct Nureva Console API cloud hostname
- Ensure the Nureva Console account has the necessary permissions to access the organization's device data

**Link Error / Ping Timeout**
- Make sure your Cloud Connector can reach the Nureva Console API cloud host on port 443
- Check the pingMode setting in the Symphony Nureva Console Aggregator Device configuration
- Try switching between ICMP/TCP modes, as certain protocols may be unavailable or blocked by proxy settings

If none of the recommended steps help, please enter an SOS ticket at {https://avi-spl.atlassian.net/servicedesk/customer/portals}

## What AI Assistant can do with the Nureva Console Integration:
- Find Nureva Console Aggregated Devices (Nureva Console Aggregator as Monitoring Proxy)
- Verify Nureva Console Aggregator configuration
- Check firmware update status and warnings for Nureva devices
- Read and report on device audio settings (speaker levels, microphone settings, zone configuration)

## What AI Assistant cannot do with the Nureva Console Integration:
- Provision the devices
