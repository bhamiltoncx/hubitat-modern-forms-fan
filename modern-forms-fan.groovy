// Modern Forms Fan and Light Driver
//
// Creates a child dimmer device to simplify dashboards and automation
// rules which cannot handle combined fan and light devices.
//
// Polls device state every 30 seconds by default (customizable).
//
// Requires `ipAddress` be set to the IP address of the device.
// Make sure to give your device a static DHCP address, as it is not discoverable over the network.

// Copyright 2021 Ben Hamilton
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

metadata {
    definition(name: "Modern Forms Fan and Light", namespace: "bhamiltoncx", author: "Ben Hamilton", importUrl: "https://github.com/bhamiltoncx/hubitat-modern-forms-fan/blob/main/modern-forms-fan.groovy") {
        capability "Initialize"
        capability "FanControl"
        capability "Switch"
        capability "Refresh"
        attribute "direction", "enum", ["forward", "reverse"]
        command "reboot"
        command "reverseDirection"
        command "setSpeed", [[name: "speed", type: "ENUM", description: "Fan speed to set", constraints: getFanLevel.collect {k,v -> k}]]
    }

    preferences {
        input "ipAddress", "text", title: "Fan IP Address", required: true
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
        input name: "pollIntervalSecs", type: "number", title: "Poll Interval (Seconds)", defaultValue: 30
    }
}

// Custom fan speed constraints
import groovy.transform.Field
@Field Map getFanLevel = [
    "low": 1,
    "medium-low": 2,
    "medium": 4,
    "medium-high": 5,
    "high": 6,
    "on" : 6,
    "off": 0,
]

// Base capability methods

void installed() {
    if (logEnable) log.debug("installed()")
    setupDevice()
}

void updated() {
    if (logEnable) log.debug("updated()")
    setupDevice()
}

// Capability "Initialize" methods

void initialize() {
    if (logEnable) log.debug("initialize()")
    setupDevice()
}

// Capability "Refresh" methods

void refresh() {
    if (logEnable) log.debug("refresh()")
    fetchDeviceState()
}

// Capability "Switch" methods

void on() {
    if (logEnable) log.debug("on()")
    sendCommandToDevice([fanOn: true]) { resp ->
        if (logEnable) log.debug("Got response: ${resp.data}")
        sendEventsForNewState(resp.data)
    }
}

void off() {
    if (logEnable) log.debug("off()")
    sendCommandToDevice([fanOn: false]) { resp ->
        if (logEnable) log.debug("Got response: ${resp.data}")
        sendEventsForNewState(resp.data)
    }
}

// Capability "FanControl" methods

void setSpeed(speed) {
    if (logEnable) log.debug("setSpeed(${speed})")
    if (speed == "on") {
        on()
        return
    }
    if (speed == "off") {
        off()
        return
    }
    int speedValue = fanSpeedEnumToValue(speed)
    sendCommandToDevice(["fanSpeed": speedValue]) { resp ->
        if (logEnable) log.debug("Got response: ${resp.data}")
        sendEventsForNewState(resp.data)
    }
}

void cycleSpeed() {
    if (logEnable) log.debug("cycleSpeed()")
    String currentSpeed = device.currentValue("speed")
    if (!currentSpeed) {
        log.error("Could not cycle speed (device has no current speed)")
        return
    }
    int newSpeed = 0
    switch (currentSpeed) {
        case "low":
            newSpeed = 2
            break
        case "medium-low":
            newSpeed = 3
            break
        case "medium":
            newSpeed = 5
            break
        case "medium-high":
            newSpeed = 6
            break
        case "high":
            newSpeed = 1
            break
    }
    sendCommandToDevice(["fanSpeed": newSpeed]) { resp ->
        if (logEnable) log.debug("Got response: ${resp.data}")
        sendEventsForNewState(resp.data)
    }
}

// Custom commands

void reboot() {
    if (logEnable) log.debug("reboot()")
    sendCommandToDeviceWithParams(
        uri: deviceUrl(),
        body: ["reboot": true],
        // This command is expected to time out with no response.
        // Passing 0 for timeout seems to use the default (10),
        // and passing a float value like 0.1 fails with NumberFormatException,
        // so pass 1 here for the smallest timeout.
        timeout: 1,
    ) { resp ->
        if (logEnable) log.debug("Got unexpected response (reboot should time out)")
    }
}

void reverseDirection() {
    if (logEnable) log.debug("reverseDirection()")
    String currentDirection = device.currentValue("direction")
    if (!currentDirection) {
        log.error("Could not reverse direction (device has no current direction)")
        return
    }
    String newDirection = currentDirection == "forward" ? "reverse" : "forward"
    sendCommandToDevice(["fanDirection": newDirection]) { resp ->
        if (logEnable) log.debug("Got response: ${resp.data}")
        // Changing direction doesn't seem to include the current state, so fetch
        fetchDeviceState()
    }
}

// Methods invoked by "Generic Component Dimmer" child device

void componentRefresh(childDevice) {
    if (logEnable) log.debug "componentRefresh(${childDevice.displayName})"
    fetchDeviceState()
}

void componentOn(childDevice){
    if (logEnable) log.debug "componentOn(${childDevice.displayName})"
    sendCommandToDevice(["lightOn": true]) { resp ->
        if (logEnable) log.debug("Got response: ${resp.data}")
        sendEventsForNewState(resp.data)
    }
}

void componentOff(childDevice){
    if (logEnable) log.debug "componentOff(${childDevice.displayName})"
    sendCommandToDevice(["lightOn": false]) { resp ->
        if (logEnable) log.debug("Got response: ${resp.data}")
        sendEventsForNewState(resp.data)
    }
}

void componentSetLevel(childDevice, level, transitionTime = null) {
    if (logEnable) log.debug "componentSetLevel(${childDevice.displayName}, ${level}, ${transitionTime})"
    sendCommandToDevice(["lightBrightness": level]) { resp ->
        if (logEnable) log.debug("Got response: ${resp.data}")
        sendEventsForNewState(resp.data)
    }
}

void componentStartLevelChange(childDevice, direction) {
    if (logEnable) log.debug "componentStartLevelChange(${childDevice.displayName}, ${direction})"
    // TODO
}

void componentStopLevelChange(childDevice) {
    if (logEnable) log.debug "componentStopLevelChange(${childDevice.displayName})"
    // TODO
}

// Private methods

void setupDevice() {
    List<String> fanSpeedList = ["low", "medium-low", "medium", "medium-high", "high", "off", "on"]
    groovy.json.JsonBuilder fanSpeedsJSON = new groovy.json.JsonBuilder(fanSpeedList)
    sendEvent(name: "supportedFanSpeeds", value: fanSpeedsJSON)
    fetchDeviceState()
    scheduleNextPoll()
}

String deviceUrl() {
    return "http://${ipAddress}/mf"
}

String fanSpeedValueToEnum(fanSpeed) {
    switch (fanSpeed) {
        case 1:
            return "low"
        case 2:
            return "medium-low"
        case 3:
            // Intentional fall-through to map 3 and 4 to "medium".
        case 4:
            return "medium"
        case 5:
            return "medium-high"
        case 6:
            return "high"
    }
    return null
}

int fanSpeedEnumToValue(fanSpeedEnum) {
    switch (fanSpeedEnum) {
        case "low":
            return 1
        case "medium-low":
            return 2
        case "medium":
            return 4
        case "medium-high":
            return 5
        case "high":
            return 6
    }
    log.error("Unknown fan speed enum: ${fanSpeedEnum}, falling back to default (4)")
    return 4
}

void scheduleNextPoll() {
    if (logEnable) log.debug("Scheduling next poll for $pollIntervalSecs")
    runIn(pollIntervalSecs, 'runPoll')
}

void runPoll() {
    if (logEnable) log.debug("Running poll")
    fetchDeviceState()
    scheduleNextPoll()
}

void sendCommandToDevice(jsonBodyMap, callback) {
    params = [
        uri: deviceUrl(),
        body: jsonBodyMap,
    ]
    sendCommandToDeviceWithParams(params, callback)
}

void sendCommandToDeviceWithParams(params, callback) {
    try {
        if (logEnable) log.debug("Sending request: ${params.body}")
        httpPostJson(params, callback)
    } catch (SocketTimeoutException e) {
        log.debug("Timed out sending command: ${e}")
    } catch (Exception e) {
        log.error("Error sending command: ${e}")
    }
}

void fetchDeviceState() {
    if (logEnable) log.debug("Fetching current fan state")
    sendCommandToDevice([queryDynamicShadowData: 1]) { resp ->
        if (logEnable) log.debug("Got response: ${resp.data}")
        sendEventsForNewState(resp.data)
    }
}

void sendEventsForNewState(newState) {
    String fanSpeedEnum = fanSpeedValueToEnum(newState.fanSpeed)
    if (fanSpeedEnum) {
        if (device.currentValue("speed") != fanSpeedEnum) {
            device.sendEvent(name: "speed", value: fanSpeedEnum, descriptionText: "${device.displayName} fan speed was set to ${fanSpeedEnum}")
        }
    } else {
        log.error("Could not parse fan speed: ${newState.fanSpeed}")
    }
    String newFanSwitchValue = newState.fanOn ? "on" : "off"
    if (device.currentValue("switch") != newFanSwitchValue) {
        device.sendEvent(name: "switch", value: newFanSwitchValue, descriptionText: "${device.displayName} fan was turned ${newFanSwitchValue}")
    }
    if (device.currentValue("direction") != newState.fanDirection) {
        device.sendEvent(name: "direction", value: newState.fanDirection, descriptionText: "${device.displayName} fan direction was changed to ${newState.fanDirection}")
    }

    def lightDevice = getChildLightDevice()
    String newLightSwitchValue = newState.lightOn ? "on" : "off"
    if (lightDevice.currentValue("switch") != newLightSwitchValue) {
        lightDevice.sendEvent(name: "switch", value: newLightSwitchValue, descriptionText: "${lightDevice.displayName} light was turned ${newLightSwitchValue}")
    }
    if (lightDevice.currentValue("level") != newState.lightBrightness) {
        lightDevice.sendEvent(name: "level", value: newState.lightBrightness, descriptionText: "${lightDevice.displayName} light level was changed to ${newState.lightBrightness}%", unit: "%")
    }
}

def getChildLightDevice() {
    String childDeviceId = "${device.deviceNetworkId}-light"
    def childDevice = getChildDevice(childDeviceId)
    if (childDevice) {
        return childDevice
    }
    if (logEnable) log.debug("Creating child dimmer device ${childDeviceId}")
    childDevice = addChildDevice("hubitat", "Generic Component Dimmer", childDeviceId, [name: "${device.displayName} - Light", isComponent: true])
    return childDevice
}
