/*
* Home Assistant to Hubitat Integration
*
* Description:
* Allow control of HA devices.
*
* Required Information:
* Home Asisstant IP and Port number
* Home Assistant long term Access Token
*
* Features List:
*
* Licensing:
* Copyright 2021 Yves Mercier.
* Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
* in compliance with the License. You may obtain a copy of the License at: http://www.apache.org/licenses/LICENSE-2.0
* Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
* on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
* for the specific language governing permissions and limitations under the License.
*
* Version Control:
* 0.1.0  2021-02-05 Yves Mercier       Orinal version
* 0.1.1  2021-02-06 Dan Ogorchock      Added basic support for simple "Light" devices from Home Assistant using Hubitat Generic Component Dimmer driver
* 0.1.2  2021-02-06 tomw               Added handling for some binary_sensor subtypes based on device_class
* 0.1.3  2021-02-06 Dan Ogorchock      Bug Fixes 
* 0.1.4  2021-02-06 Yves Mercier       Added version number and import URL
* 0.1.5  2021-02-06 Dan Ogorchock      Added support for Temperature and Humidity Sensors
* 0.1.6  2021-02-06 Dan Ogorchock      Corrected open/closed for HA door events
* 0.1.7  2021-02-07 Dan Ogorchock      Corrected open/closed for HA window, garage_door, and opening per @smarthomeprimer
* 0.1.8  2021-02-07 Dan Ogorchock      Removed temperature and humidity workaround for missing device_class on some HA sensors.  
*                                      This can be corrected on the HA side via the Customize entity feature to add the missing device_class.
* 0.1.9  2021-02-07 tomw               More generic handling for "sensor" device_classes.  Added voltage device_class to "sensor".
* 0.1.10 2021-02-07 Dan Ogorchock      Refactored the translation from HA to HE to simplify the overall design
* 0.1.11 2021-02-07 Dan Ogorchock      Completed refactoring of Dimmer Switch support
* 0.1.12 2021-02-08 Dan Ogorchock      Fixed typo in log.info statement
* 0.1.13 2021-02-08 tomw               Added "community" namespace support for component drivers.  Added Pressure and Illuminance.
* 0.1.14 2021-02-10 Dan Ogorchock      Added support for Fan devices (used Lutron Fan Controller as test device.)
* 0.1.15 2021-02-13 tomw               Adjust websocket status handling to reconnect on both close and error conditions.
* 0.1.16 2021-02-14 tomw               Revert 0.1.15
* 0.1.17 2021-02-14 Dan Ogorchock      Improved webSocket reconnect logic
* 0.1.18 2021-02-14 tomw               Avoid reconnect loop on initialize
* 0.1.19 2021-02-16 Yves Mercier       Added Refresh handler
* 0.1.20 2021-02-16 Yves mercier       Refactored webSocketStatus
* 0.1.21 2021-02-22 Yves mercier       Reinstated CloseConnection command. Added connection status on device page.
* 0.1.22 2021-02-24 tomw               Changes to support optional device filtering.  For use with haDeviceBridgeConfiguration.groovy.
* 0.1.23 2021-02-25 Dan Ogorchock      Switched from Exclude List to Include List
* 0.1.24 2021-03-07 Yves Mercier       Added device label in event description
* 0.1.25 2021-03-18 Dan Ogorchock      Updated for recent Hass Fan handling changes (use percentages to set speeds instead of deprecated speed names)
* 0.1.26 2021-04-02 DongHwan Suh       Added partial support for Color temperature, RGB, RGBW lights
*                                      (Manually updating the device type to the corresponding one is required in Hubitat. Only statuses of level and switch are shown in Hubitat.)
* 0.1.27 2021-04-11 Yves Mercier       Added option for secure connection
* 0.1.28 2021-04-14 Dan Ogorchock      Improved Fan Device handling
* 0.1.29 2021-04-17 Dan Ogorchock      Added support for Smoke Detector Binary Sensor
* 0.1.30 2021-08-10 tomw               Added support for device_tracker as Presence Sensor
* 0.1.31 2021-09-23 tomw               Added support for Power sensor
* 0.1.33 2021-09-28 tomw               Added support for cover as Garage Door Opener
* 0.1.34 2021-11-24 Yves Mercier       Added event type: digital or physical (in that case, from Hubitat or from Home Assistant).	
* 0.1.35 2021-12-01 draperw            Added support for locks
* 0.1.36 2021-12-14 Yves Mercier       Improved event type
* 0.1.37 2021-12-26 gabriel_kpk        Added support for Climate domain
* 0.1.38 2021-12-29                    Improved Climate support, Code cleanup, Minor decription fixes
* 0.1.39 2022-01-19 BrenenP            Added support for additional sensors
* 0.1.40 2022-02-23 tomw               Added support for Energy sensor
* 0.1.41 2022-03-08 Yves Mercier       Validate Fan speed
* 0.1.42 2022-04-02 tomw               Added support for input_boolean
* 0.1.43 2022-05-10 tomw               Added support for Curtain device_class
* 0.1.44 2022-05-15 tomw               Added support for Shade device_class
* 0.1.46 2022-07-04 tomw               Advanced configuration - manual add/remove of devices; option to disable filtering; unused child cleanup
* 0.1.47 2022-11-03 mboisson           Added support for Carbon Dioxide, Radon, and Volatile Organic Compounds sensors
* 0.1.48 2022-11-14 Yves Mercier       Added minimal RGB light support (no CT)
* 0.1.49 2022-11-16 mboisson           Sensor units and support for "unknown" sensor types
* 0.1.50 2022-12-06 Yves Mercier       Improved support for lights and added option to ignore unavailable state
* 0.1.51 2023-01-30 Yves Mercier       Added support for "unknown" binary sensor and timestamp sensor
* 0.1.53 2023-02-19 Yves Mercier       Fix a typo and refine support for lights (CT)
* 0.1.54 2023-03-02 Yves Mercier       Added support for light effects
* 0.1.55 2023-05-27 Yves Mercier       Added support for pm2.5
* 0.1.56 2023-06-12 Yves Mercier       Modified various sensor units handling
* 0.1.57 2023-07-18 Yves Mercier       By default map unsuported sensors to unknown
* 0.1.58 2023-07-27 Yves Mercier       Add support for number entity
* 0.1.59 2023-08-13 Yves Mercier       Remove unsupported states and change how health status is reported.
* 0.1.60 2013-12-31 mboisson           Added support for air quality parts
* 0.1.61 2024-01-02 Yves Mercier       Add alternate RGBW implementation + add handling of unknown state.
* 0.1.62 2024-01-10 Yves Mercier       Add input_number support
* 2.0	 2024-01-20 Yves Mercier       Introduce entity subscription model
* 2.1	 2024-01-30 Yves Mercier       Improve climate support
* 2.2    2024-02-01 Yves Mercier       Add support for door types, blind types and moisture
* 2.3    2024-03-26 Yves Mercier       Add call service command and support for buttons
* 2.4    2024-04-27 Yves Mercier       Add humidity to climate entity
* 2.5    2024-05-24 Yves Mercier       Add support for valve entity and add supported fan modes for climate entity
* 2.6    2024-06-11 Yves Mercier       Add support for humidifier entity
* 2.7    2024-08-15 Yves Mercier       Add support for events, change fan error handling, remap fan percentage to accomodate for missing named speed, forgo thermostat mode translation, add thermostat presets, use device ID instead of device name for service call.
* 2.8    2024-09-03 Yves Mercier       Fix custom call sevice to allow colons in data, fix thermostat set_preset calls.
* 2.9    2024-10-29 Yves Mercier       Add windowsShade attribute to blinds, add attributes to unknown sensor, add support for espresense.
* 2.10   2024-11-24 Yves Mercier       Add support for text and vacuum entities. Add extra blind commands.
* 2.11   2024-11-30 Yves Mercier       Add limited support for media_player entity.
* 2.12   2024-12-15 Yves Mercier       Add support for select entity. Clean code. Add item selection by name. Fix button event.
* 2.13   2024-12-25 Yves Mercier       Fix fan setSpeed.
* 2.14   2025-01-10 Yves Mercier       Add humidity support to climate entity.
* 2.15   2025-02-27 Yves Mercier       Separate indexed source list from supported inputs, remove index from lightEffects, refactored event entity to reflect breaking changes
* 2.16   2025-03-13 ritchierich        Add support for gas detector.
*                   Yves Mercier       Compensate for restrictions imposed by ezdashboard in mediaPlayer and locks, simplify handling of "off" thermostat mode
* XXXX   2025-06-06 ritchierich        Refactored code to combine attributes into a single device
*/

import groovy.json.JsonSlurper
import groovy.json.JsonOutput

metadata {
    definition (name: "HomeAssistant Hub Parent", namespace: "ymerj", author: "Yves Mercier") {
        capability "Initialize"
        capability "Actuator"

        command "closeConnection"        
        command "callService", [[name:"entity", type:"STRING", description:"domain.entity"],[name:"service", type:"STRING"],[name:"data", type:"STRING", description:"key:value,key:value... etc"]]
	    
        attribute "Connection", "string"
    }

    preferences {
        input ("ip", "text", title: "IP", description: "HomeAssistant IP Address", required: true)
        input ("port", "text", title: "Port", description: "HomeAssistant Port Number", required: true, defaultValue: "8123")
        input ("token", "text", title: "Token", description: "HomeAssistant Long-Lived Access Token", required: true)
        input ("secure", "bool", title: "Require secure connection (https)", defaultValue: false)
        input ("logEnable", "bool", title: "Enable debug logging", defaultValue: true)
        input ("txtEnable", "bool", title: "Enable description text logging", defaultValue: true)
    }
}

def removeChild(entity){
    String thisId = device.id
    def ch = getChildDevice("${thisId}-${entity}")
    if (ch) {deleteChildDevice("${thisId}-${entity}")}
}

def logsOff(){
    log.warn("debug logging disabled...")
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def updated(){
    log.info("updated...")
    log.warn("debug logging is: ${logEnable == true}")
    log.warn("description logging is: ${txtEnable == true}")
    unschedule()
    if (logEnable) runIn(1800,logsOff)
    initialize()
}

def initialize() {
    log.info("initializing...")
    closeConnection()

    state.id = 2
    def connectionType = "ws"
    if (secure) connectionType = "wss"
    auth = '{"type":"auth","access_token":"' + "${token}" + '"}'
    def subscriptionsList = device.getDataValue("filterList")
    if(subscriptionsList == null) return
    evenements = '{"id":1,"type":"subscribe_trigger","trigger":{"platform":"state","entity_id":"' + subscriptionsList + '"}}'
    try {
        interfaces.webSocket.connect("${connectionType}://${ip}:${port}/api/websocket", ignoreSSLIssues: true)
        interfaces.webSocket.sendMessage("${auth}")
        interfaces.webSocket.sendMessage("${evenements}")
    } 
    catch(e) {
        log.error("initialize error: ${e.message}")
    }
}

def uninstalled() {
    log.info("uninstalled...")
    closeConnection()
    unschedule()
    deleteAllChildDevices()
}

def webSocketStatus(String status){
    if (logEnable) log.debug("webSocket ${status}")
    if ((status == "status: closing") && (state.wasExpectedClose)) {
        state.wasExpectedClose = false
        sendEvent(name: "Connection", value: "Closed")
        return
    } 
    else if(status == 'status: open') {
        log.info("websocket is open")
        // success! reset reconnect delay
        pauseExecution(1000)
        state.reconnectDelay = 1
        state.wasExpectedClose = false
        sendEvent(name: "Connection", value: "Open")
    } 
    else {
        log.warn("WebSocket error, reconnecting.")
        sendEvent(name: "Connection", value: "Reconnecting")
        reconnectWebSocket()
    }
}

def reconnectWebSocket() {
    // first delay is 2 seconds, doubles every time
    state.reconnectDelay = (state.reconnectDelay ?: 1) * 2
    // don't let delay get too crazy, max it out at 10 minutes
    if(state.reconnectDelay > 600) state.reconnectDelay = 600
    //If the Home Assistant Hub is offline, give it some time before trying to reconnect
    runIn(state.reconnectDelay, initialize)
}

def parse(String description) {
    parent.parse(description)
}

def closeConnection() {
    if (logEnable) log.debug("Closing connection...")   
    state.wasExpectedClose = true
    interfaces.webSocket.close()
}

def callService(entity, service, data = "") {
    def cvData = [:]
    cvData = data.tokenize(",").collectEntries{it.tokenize(":").with{[(it[0]):it[1..(it.size()-1)].join(":")]}}
    domain = entity?.tokenize(".")[0]
    messUpd = [id: state.id, type: "call_service", domain: domain, service: service, service_data : [entity_id: entity] + cvData]
    state.id = state.id + 1
    messUpdStr = JsonOutput.toJson(messUpd)
    executeCommand(messUpdStr)
}

def executeCommand(messUpdStr) {
    if (logEnable) log.debug("executeCommand = ${messUpdStr}")
    interfaces.webSocket.sendMessage(messUpdStr)    
}

def createChild(deviceType, entity, friendly, namespace = null) {
    def ch = getChildDevice("${device.id}-${entity}")
    if (!ch) ch = addChildDevice(namespace ?: "hubitat", deviceType, "${device.id}-${entity}", [name: "${entity}", label: "${friendly}", isComponent: false])
    return ch
}

def deleteAllChildDevices() {
    log.info("Uninstalling all Child Devices")
    getChildDevices().each {
          deleteChildDevice(it.deviceNetworkId)
    }
}

//================ Component Commands======================

def componentOn(ch) {
    parent.componentOn(ch)
}

def componentOff(ch) {
    parent.componentOff(ch)
}

def componentSetLevel(ch, level, transition=1) {
    parent.componentSetLevel(ch, level, transition)
}

def componentSetColor(ch, color, transition=1) {
    parent.componentSetColor(ch, color, transition)
}

def componentSetColorTemperature(ch, colortemperature, level, transition=1) {
    parent.componentSetColorTemperature(ch, colortemperature, level, transition)
}

def componentSetHue(ch, hue, transition=1) {
    parent.componentSetHue(ch, hue, transition)
}

def componentSetSaturation(ch, saturation, transition=1) {
    parent.componentSetSaturation(ch, saturation, transition)
}

def componentSetEffect(ch, effectNumber) {
    parent.componentSetEffect(ch, effectNumber)
}

def componentSetNextEffect(ch) { log.warn("setNextEffect not implemented") }
def componentSetPreviousEffect(ch) { log.warn("setPreviousEffect not implemented") }

def componentSetSpeed(ch, speed) {
    parent.componentSetSpeed(ch, speed)
}

def componentCycleSpeed(ch) {
    parent.componentCycleSpeed(ch)
}

void componentClose(ch) {
    parent.componentClose(ch)
}

void componentOpen(ch) {
    parent.componentOpen(ch)
}

void componentSetPosition(ch, pos) {
    parent.componentSetPosition(ch, pos)
}

void componentCloseTilt(ch) {
    parent.componentCloseTilt(ch)
}

void componentOpenTilt(ch) {
    parent.componentOpenTilt(ch)
}

void componentSetTiltLevel(ch, tilt) {
    parent.componentSetTiltLevel(ch, tilt)
}

void componentStartPositionChange(ch, dir) {
    parent.componentStartPositionChange(ch, dir)
}

void componentStopPositionChange(ch) {
    parent.componentStopPositionChange(ch)
}

void componentStartTiltChange(ch, dir) {
    parent.componentStartTiltChange(ch, dir)
}

void componentStopTiltChange(ch) {
    parent.componentStopTiltChange(ch)
}

void componentLock(ch) {
    parent.componentLock(ch)
}

void componentUnlock(ch) {
    parent.componentUnlock(ch)
}

def deleteCode(ch, codeposition) { log.warn("deleteCode not implemented") }
def getCodes(ch) { log.warn("getCodes not implemented") }
def setCode(ch, codeposition, pincode, name) { log.warn("setCode not implemented") }
def setCodeLength(ch, pincodelength) { log.warn("setCodeLength not implemented") }

def componentPush(ch, nb) {
    parent.componentPush(ch, nb)
}

def componentSetNumber(ch, newValue) {
    parent.componentSetNumber(ch, newValue)
}

def componentSetVariable(ch, newValue) {
    parent.componentSetVariable(ch, newValue)
}
        
def componentRefresh(ch) {
    parent.componentRefresh(ch)
}

def componentSetThermostatMode(ch, thermostatmode) {
    parent.componentSetThermostatMode(ch, thermostatmode)
}

def componentSetCoolingSetpoint(ch, temperature) {
    parent.componentSetCoolingSetpoint(ch, temperature)
}

def componentSetHeatingSetpoint(ch, temperature) {
    parent.componentSetHeatingSetpoint(ch, temperature)
}

def componentSetThermostatFanMode(ch, fanmode) {
    parent.componentSetThermostatFanMode(ch, fanmode)
}

def componentSetPreset(ch, preset) { 
    parent.componentSetPreset(ch, preset)
}
	
def componentSetHumidifierMode(ch, mode) {
    parent.componentSetHumidifierMode(ch, mode)
}

def componentSelectOption(ch, option) {
    parent.componentSelectOption(ch, option)
}

def componentSetHumidity(ch, target) {
    parent.componentSetHumidity(ch, target)
}

def componentAuto(ch) {
    componentSetThermostatMode(ch, "auto")
}

def componentCool(ch) {
    componentSetThermostatMode(ch, "cool")
}

def componentEmergencyHeat(ch) {
    componentSetThermostatMode(ch, "emergencyHeat")
}

def componentFanAuto(ch) {
    componentSetThermostatMode(ch, "auto")
}

def componentFanCirculate(ch) {
    componentSetThermostatFanMode(ch, "circulate")
}

def componentFanOn(ch) {
    componentSetThermostatFanMode(ch, "on")
}

def componentHeat(ch) {
    componentSetThermostatMode(ch, "heat")
}

def componentStartLevelChange(ch) {
    log.warn("Start level change not supported")
}

def componentStopLevelChange(ch) {
    log.warn("Stop level change not supported")
}

void componentCleanSpot(ch) {
    parent.componentCleanSpot(ch)
}

void componentLocate(ch) {
    parent.componentLocate(ch)
}

void componentPause(ch) {
    parent.componentPause(ch)
}

void componentReturnToBase(ch) {
    parent.componentReturnToBase(ch)
}

void componentSetFanSpeed(ch, speed) {
    parent.componentSetFanSpeed(ch, speed)
}

void componentStart(ch) {
    parent.componentStart(ch)
}

void componentStop(ch) {
    parent.componentStop(ch)
}

void componentMute(ch) {
    parent.componentMute(ch)
}

void componentUnmute(ch) {
    parent.componentUnmute(ch)
}

void componentVolumeUp(ch) {
    parent.componentVolumeUp(ch)
}

void componentVolumeDown(ch) {
    parent.componentVolumeDown(ch)
}

void componentSetVolume(ch, volume) {
    parent.componentSetVolume(ch, volume)
}

void componentSetInputSource(ch, source) {
    parent.componentSetInputSource(ch, source)
}

void componentPauseMedia(ch) {
    parent.componentPauseMedia(ch)
}

void componentPlay(ch) {
    parent.componentPlay(ch)
}

void componentStopMedia(ch) {
    parent.componentStopMedia(ch)
}

void componentPlayText(ch, text) {
}

void componentPlayTrack(ch, mediaType, trackUri) {
    parent.componentPlayTrack(ch, mediaType, trackUri)
}

void componentPreviousTrack(ch) {
    parent.componentPreviousTrack(ch)
}

void componentNextTrack(ch) {
    parent.componentNextTrack(ch)
}

void componentShuffle(ch, value) {
    parent.componentShuffle(ch, value)
}

void componentRepeat(ch, value) {
    parent.componentRepeat(ch, value)
}

void componentRestoreTrack(ch, trackUri) {
}

void componentResumeTrack(ch, trackUri) {
}

void componentSetTrack(ch, trackUri){
}
