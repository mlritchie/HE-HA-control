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
* Copyright 2021 tomw
* Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
* in compliance with the License. You may obtain a copy of the License at: http://www.apache.org/licenses/LICENSE-2.0
* Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
* on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
* for the specific language governing permissions and limitations under the License.
*
* Version Control:
* 0.1.22     2021-02-24 tomw               Optional configuration app to selectively filter out Home Assistant devices
* 0.1.23     2021-02-25 Dan Ogorchock      Switched logic from Exclude to Include to make more intuitive.  Sorted Device List.
* 0.1.32     2021-09-27 kaimyn             Add option to use HTTPS support in configuration app
* 0.1.45     2022-06-06 tomw               Added confirmation step before completing select/de-select all
* 0.1.46     2022-07-04 tomw               Advanced configuration - manual add/remove of devices; option to disable filtering; unused child cleanup
* 0.1.52     2023-02-02 tomw               UI improvements for app usability
* 0.1.53     2023-02-19 tomw               Allow multiple instances of HADB app to be installed
* 0.1.58     2023-08-02 Yves Mercier       Add support for number domain
* 0.1.62     2023-08-02 Yves Mercier       Add support for input_number domain
* 0.1.63     2024-01-11 tomw               Remove entityList state
* 2.0        2024-01-20 Yves Mercier       Introduce entity subscription model
* 2.3        2024-03-26 Yves Mercier       Add support for buttons
* 2.5        2024-05-08 Yves Mercier       Add support for valves
* 2.6        2024-05-31 Yves Mercier       Add support for humidifiers
* 2.7        2024-08-13 Yves Mercier       Add support for events, remove HA states response from debug log
* 2.10       2024-11-12 Yves Mercier       Add support for text and vacuums
* 2.11       2024-11-30 Yves Mercier       Add limited support for media_player entity
* 2.12       2024-12-15 Yves Mercier       Add support for select entity
* 2.15       2024-01-17 Yves Mercier       Fix "Toggle all On/Off" included as an entity
* XXXX		 2025-06-06 ritchierich        Refactored code to combine attributes into a single device
*/
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

definition(
    name: "Home Assistant Device Bridge",
    namespace: "tomw",
    author: "tomw",
    description: "",
    category: "Convenience",
    importUrl: "https://raw.githubusercontent.com/ymerj/HE-HA-control/main/haDeviceBridgeConfiguration.groovy",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "")

preferences {
    page(name: "mainPage")
    page(name: "discoveryPage")
    page(name: "advOptionsPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {
        section("<b>Home Assistant Device Bridge:</b>") {
            input name: "ip", type: "text", title: "Home Assistant IP Address", description: "HomeAssistant IP Address", required: true, width: 4
            input name: "port", type: "text", title: "Home Assistant Port", description: "HomeAssistant Port Number", required: true, defaultValue: "8123", width: 4
            input name: "token", type: "text", title: "Home Assistant Long-Lived Access Token", description: "HomeAssistant Access Token", required: true
            input name: "secure", type: "bool", title: "Require secure connection", defaultValue: false, width: 4
            input name: "ignoreSSLIssues", type: "bool", title: "Ignore SSL Issues", defaultValue: false, width: 4
            input name: "logEnable", type: "bool", title: "Enable debug logging?", defaultValue: false, required: true
        }
        section("<b>Configuration options:</b>") {
            href(page: "discoveryPage", title: "<b>Discover and select devices</b>", description: "Query Home Assistant for all currently configured devices.  Then select which entities to Import to Hubitat.", params: [runDiscovery : true])
            def childOfAppLabel = (settings.childOfApp == null || settings.childOfApp == false) ? "<b>HADB Device</b> or This app?": "HADB Device or <b>This app</b>?"
            paragraph "<u>Child Options</u>: By default, each HA entity will create a device that is a child of the HA Device Bridge device. When viewing the device list, each child device will show up under the HA Device Bridge device. If you prefer to avoid this hierarchy, choose 'This app' and devices will be children of this application instead."
            input name: "childOfApp", type: "bool", title: "${childOfAppLabel}", defaultValue: false, submitOnChange: true
            paragraph "<u>Entity Options</u>: By default, each HA entity selected will create a separate HE device. Toggle the Combine setting to create a single HE device.\n<b>Please note</b>: You will need to select the primary entity in the table below to combine the attributes to and also change the associated HE device driver to one that supports all the selected entities. If the primary entity HE device driver doesn't support all the entities, a child device will be created for that attribute."
            input name: "combineDevices", type: "bool", title: "Combine HA entities associated to the same device?", defaultValue: false, submitOnChange: true
            if (settings.combineDevices == true) {
            	paragraph buildEntityMapping()
            }
        }
        section("<b>App Name</b>") {
            label title: "Optionally assign a custom name for this app", required: false
        }
    }
}

def linkToMain() {
    section {
        href(page: "mainPage", title: "<b>Return to previous page</b>", description: "")
    }
}

def discoveryPage(params) {
    dynamicPage(name: "discoveryPage", title: "", install: true, uninstall: true)
    {
        if(wasButtonPushed("cleanupUnused"))
        {
            cullGrandchildren()
            clearButtonPushed()
        }
        if(params?.runDiscovery)
        {
            state.entityList = [:]
            def domain
            // query HA to get entity_id list
            def resp = httpGetExec(genParamsMain("states"))
            // logDebug("states response = ${resp?.data}")
            
            if(resp?.data)
            {
                resp.data.each
                {
                    domain = it.entity_id?.tokenize(".")?.getAt(0)
                    if(["fan", "switch", "light", "binary_sensor", "sensor", "device_tracker", "cover", "lock", "climate", "input_boolean", "number", "input_number", "button", "input_button", "valve", "humidifier", "event", "text", "input_text", "vacuum", "media_player", "input_select", "select"].contains(domain))
                    {
                        state.entityList.put(it.entity_id, "${it.attributes?.friendly_name} (${it.entity_id})")
                    }
                }

                state.entityList = state.entityList.sort { it.value }
            }
        }
        section
        {
            input name: "includeList", type: "enum", title: "Select any devices to <b>include</b> from Home Assistant Device Bridge", options: state.entityList, required: false, multiple: true, offerAll: true
        }
        section("Administration option")
        {
            input(name: "cleanupUnused", type: "button", title: "Remove all child devices that are not currently selected (use carefully!)")
        }
        linkToMain()
    }
}

def cullGrandchildren() {
    // remove all child devices that aren't currently on either filtering list
    
    if (settings.childOfApp == false) {
        def ch = getHADBDevice()
        ch?.getChildDevices()?.each() {
        	def entity = it.getDeviceNetworkId()?.tokenize("-")?.getAt(1)        
        	if (!settings.includeList?.contains(entity)) { ch.removeChild(entity) }
        }
    } else {
        getChildDevices()?.each() {
        	def entity = it.getDeviceNetworkId()?.tokenize("-")?.getAt(1)        
        	if (!settings.includeList?.contains(entity) && entity != state.HADBID) {
            	deleteChildDevice(it.getDeviceNetworkId())
        	}
    	}
    }
}

def logDebug(msg) {
    if(enableLogging) {
        log.debug "${msg}"
    }
}

def installed() {
    def deviceID = "${app.id}-${UUID.randomUUID().toString()}"
    def ch = addChildDevice("ymerj", "HomeAssistant Hub Parent", deviceID, [name: "Home Assistant Device Bridge", label: "Home Assistant Device Bridge (${ip})", isComponent: false])
    atomicState.HADBID = deviceID
    
    initialize()
}

def updated() {
    if (!state.HADBID) {
        def ch = getChildDevices()?.getAt(0)
        atomicState.HADBID = ch.getDeviceNetworkId()
    }
    
    initialize()
}

def initialize() {
    def ch = getHADBDevice()
    
    if(ch) {
        // propoagate our settings to the child
        ch.updateSetting("ip", ip)
        ch.updateSetting("port", port)
        ch.updateSetting("token", token)
        ch.updateSetting("secure", secure)
        def filterListForChild = includeList?.join(",")
        filterListForChild -= "Toggle All On/Off"
        ch.updateDataValue("filterList", filterListForChild)
        ch.updated()
    }
    if (settings.combineDevices) getDevices()
}

/*def getChild() {
    if (state.HADBID) return getChildDevice(state.HADBID)
}*/

def uninstalled() {
    deleteAllChildDevices()
}

def deleteAllChildDevices() {
    log.info("Uninstalling all Child Devices")
    getChildDevices().each {
          deleteChildDevice(it.deviceNetworkId)
    }
}

def setButtonPushed(btn) {
    state.button = [btn: btn]
}

def wasButtonPushed(btn)
{
    return state.button?.btn == btn
}

def clearButtonPushed()
{
    state.remove("button")
}

def genParamsMain(suffix, body = null)
{
    def params =
        [
            uri: getBaseURI() + suffix,
            headers:
            [
                'Authorization': "Bearer ${token}",
                'Content-Type': "application/json"
            ],
            ignoreSSLIssues: ignoreSSLIssues
        ]
    
    if(body)
    {
        params['body'] = body
    }
 
    return params
}

def getBaseURI()
{
    if(secure) return "https://${ip}:${port}/api/"
    return "http://${ip}:${port}/api/"
}

def httpGetExec(params, throwToCaller = false)
{
    logDebug("httpGetExec(${params})")
    
    try
    {
        def result
        httpGet(params)
        { resp ->
            if (resp)
            {
                //logDebug("resp.data = ${resp.data}")
                result = resp
            }
        }
        return result
    }
    catch (Exception e)
    {
        logDebug("httpGetExec() failed: ${e.message}")
        //logDebug("status = ${e.getResponse().getStatus().toInteger()}")
        if(throwToCaller)
        {
            throw(e)
        }
    }
}

//=========================================================================================================================
def parse(String description) {
    if (logEnable) log.debug("parse(): description = ${description}")
    def response = null;
    try {
        response = new groovy.json.JsonSlurper().parseText(description)
	    if (response.type != "event") return
	    def newState = response?.event?.variables?.trigger?.to_state
	    if (newState?.state?.toLowerCase() == "unknown") return
        def origin = "physical"
        if (newState?.context?.user_id) origin = "digital"
        def newVals = []
        def entity = response?.event?.variables?.trigger?.entity_id        
        def domain = entity?.tokenize(".")?.getAt(0)
        def device_class = newState?.attributes?.device_class
        def friendly = newState?.attributes?.friendly_name
        newVals << newState?.state
        def mapping = null       
        if (logEnable) log.debug("parse: domain: ${domain}, device_class: ${device_class}, entity: ${entity}, newVals: ${newVals}, friendly: ${friendly}")
        switch (domain)
            {
            case "fan":
                def speed = newState?.attributes?.speed?.toLowerCase()
                choices =  ["off","low","medium-low","medium","medium-high","high","auto"]
                if (speed)
                    {
                    if (!(choices.contains(speed)))
                        {
                        if (logEnable) log.info("Invalid fan speed received - ${speed}")
                        speed = null
                        }
                    }
                def percentage = newState?.attributes?.percentage
                if (percentage)
                    {
                    switch (percentage.toInteger())
                        {
                        case 0: speed = "off"; break
                        case 1..30: speed = "low"; break
                        case 31..50: speed = "medium-low"; break
                        case 51..70: speed = "medium"; break
                        case 71..90: speed = "medium-high"; break
                        case 91..100: speed = "high"; break
                        default: if (logEnable) log.info("Invalid fan percentage received - ${percentage}")
                        }
                    }
                newVals += speed
                newVals += percentage
                mapping = translateDevices(domain, newVals, friendly, origin)
                if (!percentage) mapping.event.remove(2)
                if (!speed) mapping.event.remove(1)
                if (mapping) updateChildDevice(mapping, entity, friendly)
                break
            
            case "cover":
                def pos = newState?.attributes?.current_position?.toInteger()
                newVals += pos
                def tilt = newState?.attributes?.current_tilt_position?.toInteger()
                newVals += tilt
                switch (device_class)
                   {
                   case {it in ["blind","shutter","window"]}: device_class = "blind"; break
                   case {it in ["curtain","shade"]}: device_class = "shade"; break
                   case "garage": break
                   default: device_class = "door"
                   }
                mapping = translateCovers(device_class, newVals, friendly, origin)
                if (mapping) updateChildDevice(mapping, entity, friendly)
                break
            
            case "event":
                def eventType = newState?.attributes?.event_type
                def eventList = []
                eventList = newState?.attributes?.event_types
                def button = eventList.indexOf(eventType) + 1
                def nb = eventList.size()
                eventList = newState?.attributes?.event_types?.indexed(1)
                newVals += [button, eventType, eventList, nb]
                mapping = translateDevices(domain, newVals, friendly, origin)
                if (mapping) updateChildDevice(mapping, entity, friendly)
                break
            
            case "input_text":
            case "text":
            case "lock":
            case "device_tracker":
            case "valve":
            case "switch":
            case "input_boolean":
                mapping = translateDevices(domain, newVals, friendly, origin)
                if (mapping) updateChildDevice(mapping, entity, friendly)
                break
            
            case "light":
                def level = newState?.attributes?.brightness
                if (level) level = Math.round((level.toInteger() * 100 / 255))
                newVals += level
                def hue = newState?.attributes?.hs_color?.getAt(0)
                if (hue) hue = Math.round(hue.toInteger() * 100 / 360)
                def sat = newState?.attributes?.hs_color?.getAt(1)
                if (sat) sat = Math.round(sat.toInteger())
                def ct = newState?.attributes?.color_temp
                if (ct) ct = Math.round(1000000/ct)
                effectsList = JsonOutput.toJson(newState?.attributes?.effect_list)
                def effectName = newState?.attributes?.effect
                def lightType = []
                lightType = newState?.attributes?.supported_color_modes
                if ((lightType.intersect(["hs", "rgb"])) && (lightType.contains("color_temp"))) lightType += "rgbw"
                if (effectsList) lightType += "rgbwe"
                switch (lightType) {
                    case {it.intersect(["rgbwe"])}:
                        device_class = "rgbwe"
                        newVals += ["RGB", hue, sat, ct, effectsList, effectName]
                        break
                    case {it.intersect(["rgbww", "rgbw"])}:
                        device_class = "rgbw"
                        newVals += ["RGB", hue, sat, ct]
                        break
                    case {it.intersect(["hs", "rgb"])}:
                        device_class = "rgb"
                        newVals += ["RGB", hue, sat]
                        break
                    case {it.intersect(["color_temp"])}:
                        device_class = "ct"
                        newVals += ["white", ct]
                        break
                    default:
                        device_class = "dimmer"
                    }
                mapping = translateLight(device_class, newVals, friendly, origin)
                if (newVals[0] == "off") mapping.event = [mapping.event[0]] // remove updates not provided with the HA 'off' event json data
                if (mapping) updateChildDevice(mapping, entity, friendly)
                break
            
            case "binary_sensor":
                def attributes = newState?.attributes
                def unit = attributes?.unit_of_measurement
                newVals += unit
                if ((!device_class) && (unit in ["Bq/m³","pCi/L"])) {
                    
                }
                
                mapping = translateBinarySensors(device_class, newVals, friendly, origin)
                if (mapping) updateChildDevice(mapping, entity, friendly)
                break
            
            case "input_number":
            case "number":
                def minimum = newState?.attributes?.min
                def maximum = newState?.attributes?.max
                def step = newState?.attributes?.step
                def unit = newState?.attributes?.unit_of_measurement
                newVals += [unit, minimum, maximum, step]
                mapping = translateDevices(domain, newVals, friendly, origin)
                if (mapping) updateChildDevice(mapping, entity, friendly)
                break
            
            case "sensor":
                def attributes = newState?.attributes
                def unit = attributes?.unit_of_measurement
                newVals += unit
                if ((!device_class) && (unit in ["Bq/m³","pCi/L"])) {
                    device_class = "radon" // if there is no device_class, we need to infer from the units
                } else if ((!device_class) && (attributes.containsKey("distance"))) {
                    device_class = "occupancy"
                    def distance = attributes.distance
                    newVals = [newVals[0]] + distance
                } else if (device_class == "temperature" && entity.contains("feels_like")) {
                    device_class = "feelsLike"
                }
                newVals += attributes
                mapping = translateSensors(device_class, newVals, friendly, origin)
                if (mapping) updateChildDevice(mapping, entity, friendly)
                break
            
            case "climate":
                def thermostat_mode = newState?.state
                def current_temperature = newState?.attributes?.current_temperature
                def maxHumidity = newState?.attributes?.max_humidity
                def minHumidity = newState?.attributes?.min_humidity
                def currentHumidity = newState?.attributes?.current_humidity
                def targetHumidity = newState?.attributes?.humidity
                def hvac_action = newState?.attributes?.hvac_action
                def fan_mode = newState?.attributes?.fan_mode
                def target_temperature = newState?.attributes?.temperature
                def target_temp_high = newState?.attributes?.target_temp_high
                def target_temp_low = newState?.attributes?.target_temp_low
                def supportedPmodes = []
                supportedPmodes = newState?.attributes?.preset_modes?.indexed(1)
                def currentPreset = newState?.attributes?.preset_mode
                def hvac_modes = newState?.attributes?.hvac_modes
                if (!hvac_modes) hvac_modes = ["heat"]	    
                def supportedTmodes = JsonOutput.toJson(hvac_modes)
                def fan_modes = newState?.attributes?.fan_modes
                if (!fan_modes) fan_modes = ["on"]
                def supportedFmodes = JsonOutput.toJson(fan_modes)
                newVals = [thermostat_mode, current_temperature, hvac_action, fan_mode, target_temperature, target_temp_high, target_temp_low, supportedTmodes, supportedFmodes, supportedPmodes, currentPreset, maxHumidity, minHumidity, currentHumidity, targetHumidity]
                mapping = translateDevices(domain, newVals, friendly, origin)
                if (!currentHumidity) mapping.event = mapping.event[0..10] // some thermostats don't provide humidity control
                if (mapping) updateChildDevice(mapping, entity, friendly)
                break
            
            case "button":
            case "input_button":
                newVals = [1]
                mapping = translateDevices(domain, newVals, friendly, origin)
                if (mapping) updateChildDevice(mapping, entity, friendly)
                break
            
            case "humidifier":
                humidifierMode = newState?.attributes?.mode
                def supportedModes = []
                supportedModes = newState?.attributes?.available_modes?.indexed(1)
                def maxHumidity = newState?.attributes?.max_humidity
                def minHumidity = newState?.attributes?.min_humidity
                def currentHumidity = newState?.attributes?.current_humidity
                def targetHumidity = newState?.attributes?.humidity
                newVals += [humidifierMode, supportedModes, maxHumidity, minHumidity, currentHumidity, targetHumidity]
                mapping = translateDevices(domain, newVals, friendly, origin)
                if (!targetHumidity) mapping.event.remove(6)
                if (!currentHumidity) mapping.event.remove(5)
                if (mapping) updateChildDevice(mapping, entity, friendly)
                break
            
            case "vacuum":
                def speed = newState?.attributes?.fan_speed
                def fanSpeedList = []
                fanSpeedList = newState?.attributes?.fan_speed_list
                newVals += [speed, fanSpeedList]
                mapping = translateDevices(domain, newVals, friendly, origin)
                if (mapping) updateChildDevice(mapping, entity, friendly)
                break
            
            case "select":
            case "input_select":
                def options = []
                options = newState?.attributes?.options?.indexed(1)
                newVals += options
                mapping = translateDevices(domain, newVals, friendly, origin)
                if (mapping) updateChildDevice(mapping, entity, friendly)
                break
                
            case "media_player":
                def status = newVals[0] in ["playing", "paused"] ? newVals[0] : "stopped"
                def volume = newState?.attributes?.volume_level
                if (volume) volume = Math.round((volume * 100).toInteger())
                def mute = newState?.attributes?.is_volume_muted
                def mediaType = newState?.attributes?.media_content_type
                def duration = newState?.attributes?.media_duration
                def position = newState?.attributes?.media_position
                def trackData = newState?.attributes?.media_content_id
                def title = newState?.attributes?.media_title ?: '---'
                def trackDescription = "Title: " + title
                def artist = newState?.attributes?.media_artist ?: '---'
                def album = newState?.attributes?.media_album_name ?: '---'                
                def playlist = newState?.attributes?.media_playlist ?: '---'
                def channel = newState?.attributes?.media_channel ?: '---'
                def episode = newState?.attributes?.media_episode ?: '---'
                def season = newState?.attributes?.media_season ?: '---'
                def seriesTitle = newState?.attributes?.media_series_title ?: '---'
                switch (mediaType)
                    {
                    case "music": trackDescription += ", Artist: " + artist + ", Album: " + album + ", Playlist: " + playlist; break
                    case "episode": trackDescription += ", Serie: " + seriesTitle + ", Season: " + season + ", Episode: " + episode; break
                    case "channel": trackDescription = "Channel: " + channel; break
                    }
                def mediaInputSource = newState?.attributes?.source
                def sourceList = newState?.attributes?.source_list?.indexed(1)
                def supportedInputs = JsonOutput.toJson(newState?.attributes?.source_list)
                newVals += [status, mute, volume, mediaType, duration, position, trackData, trackDescription, mediaInputSource, supportedInputs, sourceList]
                mapping = translateDevices(domain, newVals, friendly, origin)
                if (!sourceList) mapping.event = mapping.event[0..9]
                if (mapping) updateChildDevice(mapping, entity, friendly)
                break
            
            default:
                if (logEnable) log.info("No mapping exists for domain: ${domain}, device_class: ${device_class}.  Please contact devs to have this added.")
            }
        return
    }  
    catch(e) {
        log.error("Parsing error: ${e}")
        return
    }
}

def translateBinarySensors(device_class, newVals, friendly, origin)
{
    def mapping =
        [
            door: [type: "Generic Component Contact Sensor",            event: [[name: "contact", value: newVals[0] == "on" ? "open":"closed", descriptionText: "${friendly} is ${newVals[0] == 'on' ? 'open':'closed'}"]]],
            garage_door: [type: "Generic Component Contact Sensor",     event: [[name: "contact", value: newVals[0] == "on" ? "open":"closed", descriptionText:"${friendly} is ${newVals[0] == 'on' ? 'open':'closed'}"]]],
            lock: [type: "Generic Component Contact Sensor",            event: [[name: "contact", value: newVals[0] == "on" ? "open":"closed", descriptionText:"${friendly} is ${newVals[0] == 'on' ? 'unlocked':'locked'}"]]],
            moisture: [type: "Generic Component Water Sensor",          event: [[name: "water", value: newVals[0] == "on" ? "wet":"dry", descriptionText:"${friendly} is ${newVals[0] == 'on' ? 'wet':'dry'}"]]],
            motion: [type: "Generic Component Motion Sensor",           event: [[name: "motion", value: newVals[0] == "on" ? "active":"inactive", descriptionText:"${friendly} is ${newVals[0] == 'on' ? 'active':'inactive'}"]]],
            occupancy: [type: "Generic Component Motion Sensor",        event: [[name: "motion", value: newVals[0] == "on" ? "active":"inactive", descriptionText:"${friendly} is ${newVals[0] == 'on' ? 'active':'inactive'}"]]],
            moving: [type: "Generic Component Acceleration Sensor",     event: [[name: "acceleration", value: newVals[0] == "on" ? "active":"inactive", descriptionText:"${friendly} is ${newVals[0] == 'on' ? 'active':'inactive'}"]]],
            opening: [type: "Generic Component Contact Sensor",         event: [[name: "contact", value: newVals[0] == "on" ? "open":"closed", descriptionText:"${friendly} is ${newVals[0] == 'on' ? 'open':'closed'}"]]],
            presence: [type: "Generic Component Presence Sensor",       event: [[name: "presence", value: newVals[0] == "on" ? "present":"not present", descriptionText:"${friendly} is ${newVals[0] == 'on' ? 'present':'not present'}"]], namespace: "community"],
            smoke: [type: "Generic Component Smoke Detector",           event: [[name: "smoke", value: newVals[0] == "on" ? "detected":"clear", descriptionText:"${friendly} is ${newVals[0] == 'on' ? 'detected':'clear'}"]]],
            vibration: [type: "Generic Component Acceleration Sensor",  event: [[name: "acceleration", value: newVals[0] == "on" ? "active":"inactive", descriptionText:"${friendly} is ${newVals[0] == 'on' ? 'active':'inactive'}"]]],
            unknown: [type: "Generic Component Unknown Sensor",         event: [[name: "unknown", value: newVals[0], descriptionText:"${friendly} is ${newVals[0]}"]], namespace: "community"],
            window: [type: "Generic Component Contact Sensor",          event: [[name: "contact", value: newVals[0] == "on" ? "open":"closed", descriptionText:"${friendly} is ${newVals[0] == 'on' ? 'open':'closed'}"]]],
            gas: [type: "HADB Generic Component Gas Detector",          event: [[name: "naturalGas", value: newVals[0] == "on" ? "detected":"clear", descriptionText:"${friendly} is ${newVals[0] == 'on' ? 'detected':'clear'}"]], namespace: "community"],
        ]
    if (!mapping[device_class]) device_class = "unknown"
    return mapping[device_class]
}

def translateSensors(device_class, newVals, friendly, origin)
{
    def mapping =
        [
            humidity: [type: "Generic Component Humidity Sensor",             event: [[name: "humidity", value: newVals[0], unit: newVals[1] ?: "%", descriptionText:"${friendly} humidity is ${newVals[0]} ${newVals[1] ?: '%'}"]]],
            moisture: [type: "Generic Component Humidity Sensor",             event: [[name: "humidity", value: newVals[0], unit: newVals[1] ?: "%", descriptionText:"${friendly} humidity is ${newVals[0]} ${newVals[1] ?: '%'}"]]],
            illuminance: [type: "HADB Generic Component Illuminance Sensor",  event: [[name: "illuminance", value: newVals[0], unit: newVals[1] ?: "lx", descriptionText:"${friendly} illuminance is ${newVals[0]} ${newVals[1] ?: 'lx'}"]], namespace: "community"],
            battery: [type: "Generic Component Battery",                      event: [[name: "battery", value: newVals[0], unit: newVals[1] ?: "%", descriptionText:"${friendly} battery is ${newVals[0]} ${newVals[1] ?: '%'}"]], namespace: "community"],
            power: [type: "Generic Component Power Meter",                    event: [[name: "power", value: newVals[0], unit: newVals[1] ?: "W", descriptionText:"${friendly} power is ${newVals[0]} ${newVals[1] ?: 'W'}"]]],
            pressure: [type: "Generic Component Pressure Sensor",             event: [[name: "pressure", value: newVals[0], unit: newVals[1] ?: "", descriptionText:"${friendly} pressure is ${newVals[0]} ${newVals[1] ?: ''}"]], namespace: "community"],
            carbon_dioxide: [type: "Generic Component Carbon Dioxide Sensor", event: [[name: "carbonDioxide", value: newVals[0], unit: newVals[1] ?: "ppm", descriptionText:"${friendly} carbon_dioxide is ${newVals[0]} ${newVals[1] ?: 'ppm'}"]], namespace: "community"],
            volatile_organic_compounds_parts: [type: "Generic Component Volatile Organic Compounds Sensor",
                                                                              event: [[name: "voc", value: newVals[0], unit: newVals[1] ?: "ppb", descriptionText:"${friendly} volatile_organic_compounds_parts is ${newVals[0]} ${newVals[1] ?: 'ppb'}"]], namespace: "community"],
            volatile_organic_compounds: [type: "Generic Component Volatile Organic Compounds Sensor",
                                                                              event: [[name: "voc", value: newVals[0], unit: newVals[1] ?: "µg/m³", descriptionText:"${friendly} volatile_organic_compounds is ${newVals[0]} ${newVals[1] ?: 'µg/m³'}"]], namespace: "community"],
            radon: [type: "Generic Component Radon Sensor",                   event: [[name: "radon", value: newVals[0], unit: newVals[1], descriptionText:"${friendly} radon is ${newVals[0]} ${newVals[1]}"]], namespace: "community"],
            temperature: [type: "Generic Component Temperature Sensor",       event: [[name: "temperature", value: newVals[0], unit: newVals[1] ?: "°", descriptionText:"${friendly} temperature is ${newVals[0]} ${newVals[1] ?: '°'}"]]],
            feelsLike: [type: "Generic Component Temperature Sensor",       event: [[name: "feelsLike", value: newVals[0], unit: newVals[1] ?: "°", descriptionText:"${friendly} temperature is ${newVals[0]} ${newVals[1] ?: '°'}"]]],
            voltage: [type: "Generic Component Voltage Sensor",               event: [[name: "voltage", value: newVals[0], unit: newVals[1] ?: "V", descriptionText:"${friendly} voltage is ${newVals[0]} ${newVals[1] ?: 'V'}"]]],
            energy: [type: "Generic Component Energy Meter",                  event: [[name: "energy", value: newVals[0], unit: newVals[1] ?: "kWh", descriptionText:"${friendly} energy is ${newVals[0]} ${newVals[1] ?: 'kWh'}"]]],
            unknown: [type: "Generic Component Unknown Sensor",               event: [[name: "unknown", value: newVals[0], unit: newVals[1] ?: "", descriptionText:"${friendly} value is ${newVals[0]} ${newVals[1] ?: ''}"],[name: "attributes", value: newVals[2]]], namespace: "community"],
            occupancy: [type: "HADB Generic Component Occupancy Sensor",      event: [[name: "room", value: newVals[0], descriptionText:"${friendly} room is ${newVals[0]} "],[name: "distance", value: newVals[1], unit: "m", descriptionText:"${friendly} distance is ${newVals[1]} m"],[name: "attributes", value: newVals[2]]], namespace: "community"],                
            timestamp: [type: "Generic Component TimeStamp Sensor",           event: [[name: "timestamp", value: newVals[0], descriptionText:"${friendly} time is ${newVals[0]}"]], namespace: "community"],
            pm25: [type: "Generic Component pm25 Sensor",                     event: [[name: "pm25", value: newVals[0], unit: newVals[1] ?: "µg/m³", descriptionText:"${friendly} pm2.5 is ${newVals[0]} ${newVals[1] ?: 'µg/m³'}"]], namespace: "community"],
        ]
    if (!mapping[device_class]) device_class = "unknown"
    return mapping[device_class]
}

def translateCovers(device_class, newVals, friendly, origin)
{
    def mapping =
        [
            shade: [type: "Generic Component Window Shade",             event: [[name: "windowShade", value: newVals[0] ?: "unknown", type: origin, descriptionText:"${friendly} was turned ${newVals[0]} [${origin}]"],[name: "position", value: (null != newVals?.getAt(1)) ? newVals[1] : "unknown", type: origin, descriptionText:"${friendly} was turned ${newVals[1]} [${origin}]"]], namespace: "community"],
            garage: [type: "Generic Component Garage Door Control",     event: [[name: "door", value: newVals[0] ?: "unknown", type: origin, descriptionText:"${friendly} was turned ${newVals[0]} [${origin}]"]], namespace: "community"],
            door: [type: "Generic Component Door Control",              event: [[name: "door", value: newVals[0] ?: "unknown", type: origin, descriptionText:"${friendly} was turned ${newVals[0]} [${origin}]"]], namespace: "community"],
            blind: [type: "Generic Component Window Blind",             event: [[name: "windowBlind", value: newVals[0] ?: "unknown", type: origin, descriptionText:"${friendly} was turned ${newVals[0]} [${origin}]"],[name: "windowShade", value: newVals[0] ?: "unknown", type: origin, descriptionText:"${friendly} was turned ${newVals[0]} [${origin}]"],[name: "position", value: newVals[1] ?: "unknown", type: origin, descriptionText:"${friendly} position was set to ${newVals[1] ?: "unknown"} [${origin}]"],[name: "tilt", value: newVals[2] ?: "unknown", type: origin, descriptionText:"${friendly} tilt was set to ${newVals[2] ?: "unknown"} [${origin}]"]], namespace: "community"],
        ]
    return mapping[device_class]
}

def translateDevices(domain, newVals, friendly, origin)
{
    def mapping =
        [
            button: [type: "Generic Component Pushable Button",         event: [[name: "pushed", value: newVals[0], type: origin, descriptionText:"${friendly} button ${newVals[0]} was pushed [${origin}]", isStateChange: true]], namespace: "community"],
            input_button: [type: "Generic Component Pushable Button",   event: [[name: "pushed", value: newVals[0], type: origin, descriptionText:"${friendly} button ${newVals[0]} was pushed [${origin}]", isStateChange: true]], namespace: "community"],
            fan: [type: "Generic Component Fan Control",                event: [[name: "switch", value: newVals[0], type: origin, descriptionText:"${friendly} was turned ${newVals[0]} [${origin}]"],[name: "speed", value: newVals[1], type: origin, descriptionText:"${friendly} speed was set to ${newVals[1]} [${origin}]"],[name: "level", value: newVals[2], type: origin, descriptionText:"${friendly} level was set to ${newVals[2]} [${origin}]"]]],
            switch: [type: "Generic Component Switch",                  event: [[name: "switch", value: newVals[0], type: origin, descriptionText:"${friendly} was turned ${newVals[0]} [${origin}]"]]],
            device_tracker: [type: "Generic Component Presence Sensor", event: [[name: "presence", value: newVals[0] == "home" ? "present":"not present", descriptionText:"${friendly} is updated"]], namespace: "community"],
            lock: [type: "HADB Generic Component Lock",                 event: [[name: "lock", value: newVals[0] in ["locked", "unlocked"] ? newVals[0] : "unknown", type: origin, descriptionText:"${friendly} status became ${newVals[0]} [${origin}]"],[name: "rawStatus", value: newVals[0], type: origin, descriptionText:"${friendly} rawStatus became ${newVals[0]} [${origin}]"]], namespace: "community"],
            climate: [type: "HADB Generic Component Thermostat",        event: [[name: "thermostatMode", value: newVals[0], descriptionText: "${friendly} is set to ${newVals[0]}"],[name: "temperature", value: newVals[1], descriptionText: "${friendly} current temperature is ${newVals[1]} degree"],[name: "thermostatOperatingState", value: newVals[2], descriptionText: "${friendly} mode is ${newVals[2]}"],[name: "thermostatFanMode", value: newVals[3] ?: "on", descriptionText: "${friendly} fan is set to ${newVals[3] ?: 'on'}"],[name: "thermostatSetpoint", value: newVals[4], descriptionText: "${friendly} temperature is set to ${newVals[4]} degree"],[name: "coolingSetpoint", value: newVals[5] ?: newVals[4], descriptionText: "${friendly} cooling temperature is set to ${newVals[5] ?: newVals[4]} degrees"],[name: "heatingSetpoint", value: newVals[6] ?: newVals[4], descriptionText: "${friendly} heating temperature is set to ${newVals[6] ?: newVals[4]} degrees"],[name: "supportedThermostatModes", value: newVals[7], descriptionText: "${friendly} supportedThermostatModes were set to ${newVals[7]}"],[name: "supportedThermostatFanModes", value: newVals[8], descriptionText: "${friendly} supportedThermostatFanModes were set to ${newVals[8]}"],[name: "supportedPresets", value: newVals[9] ?: "none", descriptionText: "${friendly} supportedPresets were set to ${newVals[9] ?: 'none'}"],[name: "currentPreset", value: newVals[10] ?: "none", descriptionText: "${friendly} currentPreset was set to ${newVals[10] ?: 'none'}"],[name: "maxHumidity", value: newVals[11] ?: 100, unit: "%", descriptionText:"${friendly} maximum humidity is ${newVals[11] ?: 100}%"],[name: "minHumidity", value: newVals[12] ?: 0, unit: "%", descriptionText:"${friendly} minimum humidity is ${newVals[12] ?: 0}%"],[name: "humidity", value: newVals[13], unit: "%", descriptionText:"${friendly} current humidity is ${newVals[13]}%"],[name: "humiditySetpoint", value: newVals[14], unit: "%", descriptionText:"${friendly} humidity setpoint is set to ${newVals[14]}%"]], namespace: "community"],
            input_boolean: [type: "Generic Component Switch",           event: [[name: "switch", value: newVals[0], type: origin, descriptionText:"${friendly} was turned ${newVals[0]} [${origin}]"]]],
            humidifier: [type: "HADB Generic Component Humidifier",     event: [[name: "switch", value: newVals[0], type: origin, descriptionText:"${friendly} was turned ${newVals[0]} [${origin}]"],[name: "humidifierMode", value: newVals[1] ?: "none", descriptionText: "${friendly}'s humidifier mode is set to ${newVals[1] ?: 'none'}"],[name: "supportedModes", value: newVals[2] ?: "none", descriptionText: "${friendly} supportedModes were set to ${newVals[2] ?: 'none'}"],[name: "maxHumidity", value: newVals[3] ?: 100, unit: "%", descriptionText:"${friendly} max humidity is ${newVals[3] ?: 100}%"],[name: "minHumidity", value: newVals[4] ?: 0, unit: "%", descriptionText:"${friendly} min humidity is ${newVals[4] ?: 0}%"],[name: "humidity", value: newVals[5], unit: "%", descriptionText:"${friendly} current humidity is ${newVals[5]}%"],[name: "targetHumidity", value: newVals[6], unit: "%", descriptionText:"${friendly} target humidity is set to ${newVals[6]}%"]], namespace: "community"],
            valve: [type: "HADB Generic Component Valve",               event: [[name: "valve", value: newVals[0] == "closed" ? "closed":"open", type: origin, descriptionText:"${friendly} was turned ${newVals[0]} [${origin}]"]], namespace: "community"],
            event: [type: "HADB Generic Component Event",               event: [[name: "timestamp", value: newVals[0], descriptionText:"${friendly} event received at ${newVals[0]}"],[name: "pushed", value: newVals[1], descriptionText: "${friendly} button ${newVals[1]} was pushed", isStateChange: true],[name: "eventType", value: newVals[2], descriptionText:"${friendly} event type was ${newVals[2]}"],[name: "eventList", value: newVals[3], descriptionText:"${friendly} eventlList is ${newVals[3]}"],[name: "numberOfButtons", value: newVals[4], descriptionText:"${friendly} number of buttons is ${newVals[4]}"]], namespace: "community"],
            input_text: [type: "HADB Generic Component Text",           event: [[name: "variable", value: newVals[0], type: origin, descriptionText:"${friendly} was set to ${newVals[0]} [${origin}]"]], namespace: "community"],
            text: [type: "HADB Generic Component Text",                 event: [[name: "variable", value: newVals[0], type: origin, descriptionText:"${friendly} was set to ${newVals[0]} [${origin}]"]], namespace: "community"],
            input_number: [type: "Generic Component Number",            event: [[name: "number", value: newVals[0], unit: newVals[1] ?: "", type: origin, descriptionText:"${friendly} was set to ${newVals[0]} ${newVals[1] ?: ''} [${origin}]"],[name: "minimum", value: newVals[2], descriptionText:"${friendly} minimum value is ${newVals[2]}"],[name: "maximum", value: newVals[3], descriptionText:"${friendly} maximum value is ${newVals[3]}"],[name: "step", value: newVals[4], descriptionText:"${friendly} step is ${newVals[4]}"]], namespace: "community"],
            number: [type: "Generic Component Number",                  event: [[name: "number", value: newVals[0], unit: newVals[1] ?: "", type: origin, descriptionText:"${friendly} was set to ${newVals[0]} ${newVals[1] ?: ''} [${origin}]"],[name: "minimum", value: newVals[2], descriptionText:"${friendly} minimum value is ${newVals[2]}"],[name: "maximum", value: newVals[3], descriptionText:"${friendly} maximum value is ${newVals[3]}"],[name: "step", value: newVals[4], descriptionText:"${friendly} step is ${newVals[4]}"]], namespace: "community"],
            vacuum: [type: "HADB Generic Component Vacuum",             event: [[name: "vacuum", value: newVals[0], type: origin, descriptionText:"${friendly} is ${newVals[0]} [${origin}]"],[name: "speed", value: newVals[1], type: origin, descriptionText:"${friendly} speed was set to ${newVals[1]} [${origin}]"],[name: "fanSeedList", value: newVals[2], type: origin, descriptionText:"${friendly} speed list is ${newVals[2]} [${origin}]"]], namespace: "community"],
            media_player: [type: "HADB Generic Component Media Player", event: [[name: "switch", value: newVals[0] == "off" ? "off":"on", type: origin, descriptionText:"${friendly} was turned ${newVals[0] == 'off' ? 'off':'on'} [${origin}]"],[name: "status", value: newVals[1], type: origin, descriptionText:"${friendly} status was set to ${newVals[1]} [${origin}]"],[name: "rawStatus", value: newVals[0], type: origin, descriptionText:"${friendly} rawStatus became ${newVals[0]} [${origin}]"],[name: "mute", value: newVals[2] ? "muted":"unmuted", type: origin, descriptionText:"${friendly} volume was ${newVals[2] ? 'muted':'unmuted'} [${origin}]"],[name: "volume", value: newVals[3], type: origin, descriptionText:"${friendly} volume was set to ${newVals[3]} [${origin}]"],[name: "mediaType", value: newVals[4], type: origin, descriptionText:"${friendly} mediaType was set to ${newVals[4]} [${origin}]"],[name: "duration", value: newVals[5], type: origin, descriptionText:"${friendly} duration was set to ${newVals[5]} [${origin}]"],[name: "position", value: newVals[6], type: origin, descriptionText:"${friendly} position was set to ${newVals[6]} [${origin}]"],[name: "trackData", value: newVals[7], type: origin, descriptionText:"${friendly} track was set to ${newVals[7]} [${origin}]"],[name: "trackDescription", value: newVals[8], type: origin, descriptionText:"${friendly} trackDescription was set to ${newVals[8]} [${origin}]"],[name: "mediaInputSource", value: newVals[9], type: origin, descriptionText:"${friendly} mediaInputSource was set to ${newVals[9]} [${origin}]"],[name: "supportedInputs", value: newVals[10], type: origin, descriptionText:"${friendly} supportedInputs was set to ${newVals[10]} [${origin}]"],[name: "sourceList", value: newVals[11], type: origin, descriptionText:"${friendly} source list was set to ${newVals[11]} [${origin}]"]], namespace: "community"],
            select: [type: "HADB Generic Component Select",             event: [[name: "currentOption", value: newVals[0], type: origin, descriptionText:"${friendly} was set to ${newVals[0]} [${origin}]"],[name: "options", value: newVals[1], descriptionText: "${friendly} options were set to ${newVals[1]}"]], namespace: "community"],
            input_select: [type: "HADB Generic Component Select",       event: [[name: "currentOption", value: newVals[0], type: origin, descriptionText:"${friendly} was set to ${newVals[0]} [${origin}]"],[name: "options", value: newVals[1], descriptionText: "${friendly} options were set to ${newVals[1]}"]], namespace: "community"],
        ]
    return mapping[domain]
}

def translateLight(device_class, newVals, friendly, origin)
{
    def mapping =
        [
            rgbwe: [type: "Generic Component RGBW Light Effects",       event: [[name: "switch", value: newVals[0], type: origin, descriptionText:"${friendly} was turned ${newVals[0]} [${origin}]"],[name: "level", value: newVals[1], type: origin, descriptionText:"${friendly} level was set to ${newVals[1]}"],[name: "colorMode", value: newVals[2], descriptionText:"${friendly} color mode was set to ${newVals[2]}"],[name: "hue", value: newVals[3], descriptionText:"${friendly} hue was set to ${newVals[3]}"],[name: "saturation", value: newVals[4], descriptionText:"${friendly} saturation was set to ${newVals[4]}"],[name: "colorTemperature", value: newVals[5] ?: 'emulated', descriptionText:"${friendly} color temperature was set to ${newVals[5] ?: 'emulated'}°K"],[name: "lightEffects", value: newVals[6]],[name: "effectName", value: newVals[7] ?: "none", descriptionText:"${friendly} effect was set to ${newVals[7] ?: 'none'}"]]],
            rgbw: [type: "Generic Component RGBW",                      event: [[name: "switch", value: newVals[0], type: origin, descriptionText:"${friendly} was turned ${newVals[0]} [${origin}]"],[name: "level", value: newVals[1], type: origin, descriptionText:"${friendly} level was set to ${newVals[1]}"],[name: "colorMode", value: newVals[2], descriptionText:"${friendly} color mode was set to ${newVals[2]}"],[name: "hue", value: newVals[3], descriptionText:"${friendly} hue was set to ${newVals[3]}"],[name: "saturation", value: newVals[4], descriptionText:"${friendly} saturation was set to ${newVals[4]}"],[name: "colorTemperature", value: newVals[5], descriptionText:"${friendly} color temperature was set to ${newVals[5]}°K"]]],
            rgb: [type: "Generic Component RGB",                        event: [[name: "switch", value: newVals[0], type: origin, descriptionText:"${friendly} was turned ${newVals[0]} [${origin}]"],[name: "level", value: newVals[1], type: origin, descriptionText:"${friendly} level was set to ${newVals[1]}"],[name: "colorMode", value: newVals[2], descriptionText:"${friendly} color mode was set to ${newVals[2]}"],[name: "hue", value: newVals[3], descriptionText:"${friendly} hue was set to ${newVals[3]}"],[name: "saturation", value: newVals[4], descriptionText:"${friendly} saturation was set to ${newVals[4]}"]]],
            ct: [type: "Generic Component CT",                          event: [[name: "switch", value: newVals[0], type: origin, descriptionText:"${friendly} was turned ${newVals[0]} [${origin}]"],[name: "level", value: newVals[1], type: origin, descriptionText:"${friendly} level was set to ${newVals[1]}"],[name: "colorName", value: newVals[2], descriptionText:"${friendly} color name was set to ${newVals[2]}"],[name: "colorTemperature", value: newVals[3], descriptionText:"${friendly} color temperature was set to ${newVals[3]}°K"]]],
            dimmer: [type: "Generic Component Dimmer",                  event: [[name: "switch", value: newVals[0], type: origin, descriptionText:"${friendly} was turned ${newVals[0]} [${origin}]"],[name: "level", value: newVals[1], type: origin, descriptionText:"${friendly} level was set to ${newVals[1]} [${origin}]"]]],
        ]
    return mapping[device_class]
}
   
def updateChildDevice(mapping, entity, friendly) {
    def ch = createChild(mapping, entity, friendly, mapping.namespace)
    if (!ch) {
        log.warn("Child type: ${mapping.type} not created for entity: ${entity}")
        return
    } else {
        def healthStatus = (mapping.event[0].value == "unavailable") ? "offline" : "online"
        if (healthStatus == "unavailable") {
            mapping.event = [[name: "healthStatus", value: "offline", descriptionText:"${friendly} is offline"]]
        } else if (ch.currentValue("healthStatus") != healthStatus) {
        	mapping.event += [name: "healthStatus", value: "online", descriptionText:"${friendly} is online"]
        }
        
        if (logEnable) log.debug "updateChildDevice - ${ch} parsed event: ${mapping.event}"
        ch.parse(mapping.event)
    }
}

def createChild(mapping, entity, friendly, namespace = null) {
    def ch
    
    if (settings.combineDevices == true) {
        def HADeviceID = state.entityList[entity]
        if (HADeviceID && state.deviceList.containsKey(HADeviceID)) {
            ch = getChild(state.deviceList[HADeviceID].deviceID)
            if (ch) {
                for (int i = 0; i < mapping.event.size(); i++) {
                    def event = mapping.event[i]
                    if (ch.hasAttribute(event.name)) {
                        continue
                    } else {
                        ch = null
                        break
                    }
                }
            }
        }
    }
    
    if (!ch) {
        if (settings.childOfApp != true) {
            ch = getChild(entity)
            if (!ch) {
                def hadb = getHADBDevice()
                ch = hadb.createChild(mapping.type, entity, friendly, namespace)
            }
        } else {
            def deviceID = "${app.id}-${entity}"
            ch = getChild(entity)
            if (!ch) ch = addChildDevice(namespace ?: "hubitat", mapping.type, deviceID, [name: "${entity}", label: "${friendly}", isComponent: false])
        }
    }
                    
    return ch
}

def getHADBDevice() {
    return getChildDevice("${app.id}-${state.HADBID}")
}

def getChild(entity){
    def ch
    def deviceID
    if (settings.childOfApp != true) {
        def hadb = getHADBDevice()
        deviceID = hadb.getId()
        ch = hadb.getChildDevice("${deviceID}-${entity}")
    } else {
        deviceID = app.id
    	ch = getChildDevice("${deviceID}-${entity}")
    }
    
    return ch
}

def componentOn(ch) {
    if (logEnable) log.info("received on request from ${ch.label}")
    if (!ch.currentValue("level") || ch.hasCapability("LightEffects")) {
        data = [:]
    }
    else {
        data = [brightness_pct: "${ch.currentValue("level")}"]
    }
    executeCommand(ch, "turn_on", data)
}

def componentOff(ch) {
    if (logEnable) log.info("received off request from ${ch.label}")
    executeCommand(ch, "turn_off")
}

def componentSetLevel(ch, level, transition=1) {
    if (logEnable) log.info("received setLevel request from ${ch.label}")
    if (level > 100) level = 100
    if (level < 0) level = 0
    // if a Fan device, special handling:
    if (ch.currentValue("speed"))
        { 
        switch (level.toInteger())
            {
            case 0: componentSetSpeed(ch, "off"); break
            case 1..30: componentSetSpeed(ch, "low"); break
            case 31..50: componentSetSpeed(ch, "medium-low"); break
            case 51..70: componentSetSpeed(ch, "medium"); break
            case 71..90: componentSetSpeed(ch, "medium-high"); break
            case 91..100: componentSetSpeed(ch, "high"); break
            default: if (logEnable) log.info("No case defined for Fan setLevel(${level})")
            }
        } 
    else
        {        
        data = [brightness_pct: "${level}", transition: "${transition}"]
        executeCommand(ch, "turn_on", data)
        }
    }

def componentSetColor(ch, color, transition=1) {
    if (logEnable) log.info("received setColor request from ${ch.label}")
    convertedHue = Math.round(color.hue * 360/100)
    data = [brightness_pct: "${color.level}", hs_color: ["${convertedHue}", "${color.saturation}"], transition: "${transition}"]
    executeCommand(ch, "turn_on", data)
}

def componentSetColorTemperature(ch, colortemperature, level, transition=1) {
    if (logEnable) log.info("received setColorTemperature request from ${ch.label}")
    if (!level) level = ch.currentValue("level")
    if (!transition) transition = 1
    data = [brightness_pct: "${level}", color_temp_kelvin: "${colortemperature}", transition: "${transition}"]
    executeCommand(ch, "turn_on", data)
}

def componentSetHue(ch, hue, transition=1) {
    if (logEnable) log.info("received setHue request from ${ch.label}")
    convertedHue = Math.round(hue * 360/100)
    data = [brightness_pct: "${ch.currentValue("level")}", hs_color: ["${convertedHue}", "${ch.currentValue("saturation")}"], transition: "${transition}"]
    executeCommand(ch, "turn_on", data)
}

def componentSetSaturation(ch, saturation, transition=1) {
    if (logEnable) log.info("received setSaturation request from ${ch.label}")
    convertedHue = Math.round(ch.currentValue("hue") * 360/100)
    data = [brightness_pct: "${ch.currentValue("level")}", hs_color: ["${convertedHue}", "${saturation}"], transition: "${transition}"]
    executeCommand(ch, "turn_on", data)
}

def componentSetEffect(ch, effectNumber) {
    if (logEnable) log.info("received setEffect request from ${ch.label}")
    effects = new groovy.json.JsonSlurper().parseText(ch.currentValue("lightEffects"))
    data = [effect: effects[effectNumber.toInteger()]]
    executeCommand(ch, "turn_on", data)
}

def componentSetNextEffect(ch) { log.warn("setNextEffect not implemented") }
def componentSetPreviousEffect(ch) { log.warn("setPreviousEffect not implemented") }

def componentSetSpeed(ch, speed) {
    if (logEnable) log.info("received setSpeed request from ${ch.label}, with speed = ${speed}")
    if (speed == "off") { executeCommand(ch, "turn_off"); return }
    switch (speed)
        {
        case "on": data = [:]; break
        case "low": data = [percentage: "20"]; break
        case "medium-low": data = [percentage: "40"]; break
        case "auto":
        case "medium": data = [percentage: "60"]; break
        case "medium-high": data = [percentage: "80"]; break
        case "high": data = [percentage: "100"]; break
        default: data = [:]
        }
    executeCommand(ch, "turn_on", data)
}

def componentCycleSpeed(ch) {
    def newSpeed = ""
    switch (ch.currentValue("speed"))
        {
        case "off": speed = "low";  break
        case "low": speed = "medium-low"; break
        case "medium-low": speed = "medium"; break
        case "medium": speed = "medium-high"; break
        case "medium-high": speed = "high"; break
        case "high": speed = "off"; break
        }
    componentSetSpeed(ch, speed)
}

void componentClose(ch) {
    if (logEnable) log.info("received close request from ${ch.label}")
    service = ch.hasCapability("Valve") ? "close_valve":"close_cover"
    executeCommand(ch, service)
}

void componentOpen(ch) {
    if (logEnable) log.info("received open request from ${ch.label}")
    service = ch.hasCapability("Valve") ? "open_valve":"open_cover"
    executeCommand(ch, service)
}

void componentSetPosition(ch, pos) {
    if (logEnable) log.info("received set position request from ${ch.label}")
    executeCommand(ch, "set_cover_position", [position: pos])
}

void componentCloseTilt(ch) {
    if (logEnable) log.info("received close tilt request from ${ch.label}")
    executeCommand(ch, "close_cover_tilt")
}

void componentOpenTilt(ch) {
    if (logEnable) log.info("received open tilt request from ${ch.label}")
    executeCommand(ch, "open_cover_tilt")
}

void componentSetTiltLevel(ch, tilt) {
    if (logEnable) log.info("received set tilt request from ${ch.label}")
    executeCommand(ch, "set_cover_tilt_position", [tilt_position: tilt])
}

void componentStartPositionChange(ch, dir) {
    if(["open", "close"].contains(dir)) {
        if (logEnable) log.info("received ${dir} request from ${ch.label}")
        executeCommand(ch, dir + "_cover")
    }
}

void componentStopPositionChange(ch) {
    if (logEnable) log.info("received stop request from ${ch.label}")
    executeCommand(ch, "stop_cover")
}

void componentStartTiltChange(ch, dir) {
    if(["open", "close"].contains(dir)) {
        if (logEnable) log.info("received ${dir} tilt request from ${ch.label}")
        executeCommand(ch, dir + "_cover_tilt")
    }
}

void componentStopTiltChange(ch) {
    if (logEnable) log.info("received stop tilt request from ${ch.label}")
    executeCommand(ch, "stop_cover_tilt")
}

void componentLock(ch) {
    if (logEnable) log.info("received lock request from ${ch.label}")
    executeCommand(ch, "lock")
}

void componentUnlock(ch) {
    if (logEnable) log.info("received unlock request from ${ch.label}")
    executeCommand(ch, "unlock")
}

def deleteCode(ch, codeposition) { log.warn("deleteCode not implemented") }
def getCodes(ch) { log.warn("getCodes not implemented") }
def setCode(ch, codeposition, pincode, name) { log.warn("setCode not implemented") }
def setCodeLength(ch, pincodelength) { log.warn("setCodeLength not implemented") }

def componentPush(ch, nb) {
    if (logEnable) log.info("received push button ${nb} request from ${ch.label}")
    
    //Check if combined device and there is a button entity
    def entity = (ch instanceof com.hubitat.app.DeviceWrapper) ? ch?.getDeviceNetworkId().split("-")[1] : ch
    def HADeviceID = state.entityList[entity]    
    if (HADeviceID && state.deviceList.containsKey(HADeviceID) && state.deviceList[HADeviceID].entities.size() > 1 && state.deviceList[HADeviceID].entities.toString().indexOf("button") > -1) {
        def entityList = state.deviceList[HADeviceID].entities
        for (int i = 0; i < entityList.size(); i++) {
            def entityItem = entityList[i]
            if (entityItem.indexOf("button") > -1) {
                ch = entityItem
                break
            }
        }
    }
    executeCommand(ch, "press")
}

def componentSetNumber(ch, newValue) {
    if (logEnable) log.info("received set number to ${newValue} request from ${ch.label}")
    newValue = Math.round(newValue / ch.currentValue("step")) * ch.currentValue("step")
    if (newValue < ch.currentValue("minimum")) newValue = ch.currentValue("minimum")
    if (newValue > ch.currentValue("maximum")) newValue = ch.currentValue("maximum")
    executeCommand(ch, "set_value", [value: newValue])
}

def componentSetVariable(ch, newValue) {
    if (logEnable) log.info("received set variable to ${newValue} request from ${ch.label}")
    executeCommand(ch, "set_value", [value: newValue])
}
        
def componentRefresh(ch) {
    if (logEnable) log.info("received refresh request from ${ch.label}")
    // special handling since domain is fixed 
    entity = ch.name
    messUpd = JsonOutput.toJson([id: state.id, type: "call_service", domain: "homeassistant", service: "update_entity", service_data: [entity_id: entity]])
    state.id = state.id + 1
    def hadbDevice = getHADBDevice()
    hadbDevice.executeCommand(messUpd)
}

def componentSetThermostatMode(ch, thermostatmode) {
    if (logEnable) log.info("received setThermostatMode request from ${ch.label}")
    executeCommand(ch, "set_hvac_mode", [hvac_mode: thermostatmode])
}

def componentSetCoolingSetpoint(ch, temperature) {
    if (logEnable) log.info("received setCoolingSetpoint request from ${ch.label}")
    if (ch.currentValue("thermostatMode") == "heat_cool") data = [target_temp_high: temperature, target_temp_low: ch.currentValue("heatingSetpoint")] else data = [temperature: temperature]
    executeCommand(ch, "set_temperature", data)
}

def componentSetHeatingSetpoint(ch, temperature) {
    if (logEnable) log.info("received setHeatingSetpoint request from ${ch.label}")
    if (ch.currentValue("thermostatMode") == "heat_cool") data = [target_temp_high: ch.currentValue("coolingSetpoint"), target_temp_low: temperature] else data = [temperature: temperature] 
    executeCommand(ch, "set_temperature", data)
}

def componentSetThermostatFanMode(ch, fanmode) {
    if (logEnable) log.info("received ${fanmode} request from ${ch.label}")
    executeCommand(ch, "set_fan_mode", [fan_mode: fanmode])
}

def componentSetPreset(ch, preset) { 
    if (logEnable) log.info("received set preset number request from ${ch.label}")
    if (preset.toString().isNumber())
        {
        def presetsList = ch.currentValue("supportedPresets")?.tokenize(',=[]')
        def max = presetsList.size() / 2
        max = max.toInteger()
        preset = preset.toInteger()
        preset = (preset < 1) ? 1 : ((preset > max) ? max : preset)   
        data = [preset_mode: presetsList[(preset * 2) - 1].trim().replaceAll("}","")]
        }
    else data = [preset_mode: preset]
    executeCommand(ch, "set_preset_mode", data)
}
	
def componentSetHumidifierMode(ch, mode) {
    if (logEnable) log.info("received set mode number request from ${ch.label}")
    if (mode.toString().isNumber())
        {
        def modesList = ch.currentValue("supportedModes")?.tokenize(',=[]')
        def max = modesList.size() / 2
        max = max.toInteger()
        mode = mode.toInteger()
        mode = (mode < 1) ? 1 : ((mode > max) ? max : mode)   
        data = [mode: modesList[(mode * 2) - 1].trim().replaceAll("}","")]
        }
    else data = [mode: mode]
    executeCommand(ch, "set_mode", data)
}

def componentSelectOption(ch, option) {
    if (logEnable) log.info("received select option number request from ${ch.label}")
    if (option.toString().isNumber())
        {
        def optionsList = ch.currentValue("options")?.tokenize(',=[]')
        def max = optionsList.size() / 2
        max = max.toInteger()
        option = option.toInteger()
        option = (option < 1) ? 1 : ((option > max) ? max : option)   
        data = [option: optionsList[(option * 2) - 1].trim().replaceAll("}","")]
        }
    else data = [option: option]
    executeCommand(ch, "select_option", data)
}

def componentSetHumidity(ch, target) {
    if (logEnable) log.info("received set humidity request from ${ch.label}")
    executeCommand(ch, "set_humidity", [humidity: target])
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
    if (logEnable) log.info("received clean spot request from ${ch.label}")
    //executeCommand(ch, "clean_spot", [:])
}

void componentLocate(ch) {
    if (logEnable) log.info("received locate request from ${ch.label}")
    executeCommand(ch, "locate")
}

void componentPause(ch) {
    if (logEnable) log.info("received pause request from ${ch.label}")
    executeCommand(ch, "pause")
}

void componentReturnToBase(ch) {
    if (logEnable) log.info("received return to base request from ${ch.label}")
    executeCommand(ch, "return_to_base")
}

void componentSetFanSpeed(ch, speed) {
    if (logEnable) log.info("received set fan speed request from ${ch.label}")
    executeCommand(ch, "set_fan_speed", [value: speed])
}

void componentStart(ch) {
    if (logEnable) log.info("received start request from ${ch.label}")
    executeCommand(ch, "start")
}

void componentStop(ch) {
    if (logEnable) log.info("received stop request from ${ch.label}")
    executeCommand(ch, "stop")
}

void componentMute(ch) {
    if (logEnable) log.info("received mute request from ${ch.label}")
    executeCommand(ch, "volume_mute", [is_volume_muted: "true"])
}

void componentUnmute(ch) {
    if (logEnable) log.info("received unmute request from ${ch.label}")
    executeCommand(ch, "volume_mute", [is_volume_muted: "false"])
}

void componentVolumeUp(ch) {
    if (logEnable) log.info("received volume up request from ${ch.label}")
    executeCommand(ch, "volume_up")
}

void componentVolumeDown(ch) {
    if (logEnable) log.info("received volume down request from ${ch.label}")
    executeCommand(ch, "volume_down")
}

void componentSetVolume(ch, volume) {
    if (logEnable) log.info("received set volume level request from ${ch.label}")
    volume = volume / 100
    executeCommand(ch, "volume_set", [volume_level: volume])
}

void componentSetInputSource(ch, source) {
    if (logEnable) log.info("received set input source from ${ch.label}")
    if (source.toString().isNumber())
        {
        def sourcesList = ch.currentValue("sourceList")?.tokenize(',={}')
        def max = sourcesList.size() / 2
        max = max.toInteger()
        source = source.toInteger()
        source = (source < 1) ? 1 : ((source > max) ? max : source)   
        data = [source: sourcesList[(source * 2) - 1].trim().replaceAll("}","")]
        }
    else data = [source: source]
    executeCommand(ch, "select_source", data)
}

void componentPauseMedia(ch) {
    if (logEnable) log.info("received pause from ${ch.label}")
    executeCommand(ch, "media_pause")
}

void componentPlay(ch) {
    if (logEnable) log.info("received play from ${ch.label}")
    executeCommand(ch, "media_play")
}

void componentStopMedia(ch) {
    if (logEnable) log.info("received stop from ${ch.label}")
    executeCommand(ch, "media_stop")
}

void componentPlayText(ch, text) {
}

void componentPlayTrack(ch, mediaType, trackUri) {
    if (logEnable) log.info("received play track from ${ch.label}")
    executeCommand(ch, "play_media", [media_content_type: mediaType, media_content_id: trackUri])
}

void componentPreviousTrack(ch) {
    if (logEnable) log.info("received previous from ${ch.label}")
    executeCommand(ch, "media_previous_track")
}

void componentNextTrack(ch) {
    if (logEnable) log.info("received next from ${ch.label}")
    executeCommand(ch, "media_next_track")
}

void componentShuffle(ch, value) {
    if (logEnable) log.info("received shuffle from ${ch.label}")
    executeCommand(ch, "suffle_set", [suffle: value])
}

void componentRepeat(ch, value) {
    if (logEnable) log.info("received repeat from ${ch.label}")
    executeCommand(ch, "repeat_set", [repeat: value])
}

void componentRestoreTrack(ch, trackUri) {
}

void componentResumeTrack(ch, trackUri) {
}

void componentSetTrack(ch, trackUri){
}

def executeCommand(ch, service, data = [:]) {    
    //entity = ch?.getDeviceNetworkId().split("-")[1]
    entity = (ch instanceof com.hubitat.app.DeviceWrapper) ? ch?.getDeviceNetworkId().split("-")[1] : ch
    domain = entity?.tokenize(".")[0]
    messUpd = [id: state.id, type: "call_service", domain: domain, service: service, service_data : [entity_id: entity] + data]
    state.id = state.id + 1
    messUpdStr = JsonOutput.toJson(messUpd)
    
    def hadbDevice = getHADBDevice()
    hadbDevice.executeCommand(messUpdStr)
}

def getDevices() {
    def logMsg = ["getDevices"]
    def apiResponse
    
    def bodyString = "{\n  \"template\": \"{% set devices = states | map(attribute=\\\"entity_id\\\") | map(\\\"device_id\\\") | unique | reject(\\\"eq\\\",None) | list %}{%- set ns = namespace(devices = []) %}{%- for device in devices %}{%- set entities = device_entities(device) | list %}{%- if entities %}{%- set ns.devices = ns.devices +  [ {device: {\\\"name\\\": device_attr(device, \\\"name\\\"), \\\"entities\\\": entities}} ] %}{%- endif %}{%- endfor %}{{ ns.devices | tojson }}\\\"}\"\n}"
    
    def apiParams = [
        uri: "http://${settings.ip}:${settings.port}",
        path: "/api/template",
        contentType: "application/json",
        headers: [
            "Content-Type": "application/json; charset=utf-8",
            "Authorization": "Bearer ${settings.token}"
        ],
        body: bodyString
    ]

    try {
        httpPost(apiParams) {
            resp ->
            if (resp.status == 200) {
                def tempDeviceList = (state.deviceList) ?: [:]
                def deviceList = [:]
                def entityList = [:]
                
                def childDeviceList = []
                getChildDevices()?.each() {
        			def entity = it.getDeviceNetworkId()?.tokenize("-")?.getAt(1)        
                    childDeviceList.add(entity)
                }
                
                for (int p = 0; p < resp.data.size(); p++) {
            		//{ "a354a80b9ee8674193e6e9bc2997fdb4": { "entities": [ "button.presence_sensor_fp2_eeed_identify", "binary_sensor.master_bedroom_presence_sensor_1", "sensor.master_bedroom_presence_light_sensor_light_level" ], "name": "Presence-Sensor-FP2-EEED" } }
                    def device = resp.data[p]
                    def deviceKey = device.keySet()[0]
                    def deviceEntities = device[deviceKey].entities
                    def deviceDetail = [
                        entities : []
                    ]
                    for (int e = 0; e < deviceEntities.size(); e++) {
                        def entity = deviceEntities[e]
                        if (settings.includeList.toString().indexOf(entity) > -1 || childDeviceList.toString().indexOf(entity) > -1) {
                        	entityList[entity] = deviceKey
                            
                            deviceDetail.deviceID = (tempDeviceList[deviceKey]?.deviceID) ? tempDeviceList[deviceKey].deviceID : entity
                            deviceDetail.name = device[deviceKey].name

                            if (deviceDetail.entities.toString().indexOf(entity) == -1) {
                                deviceDetail.entities.add(entity)
                            }
                            deviceList[deviceKey] = deviceDetail
                        }
                    }
                }
                state.deviceList = deviceList
            	state.entityList = entityList
            }
        }
    } catch (e) {
    	log.error "updateDevice - error: ${e}"
    }
    
    log.debug "${logMsg}"
    return apiResponse
}

def buildEntityMapping() {    
    def deviceList = state.deviceList
    def deviceListKeys = deviceList?.keySet()

    String str = "<script src='https://code.iconify.design/iconify-icon/1.0.0/iconify-icon.min.js'></script>"
    str += "<style>.mdl-data-table tbody tr:hover{background-color:inherit} .tstat-col td,.tstat-col th { padding:8px 8px;text-align:center;font-size:12px} .tstat-col td {font-size:15px }" +
        "</style><div style='overflow-x:auto'><table class='mdl-data-table tstat-col' style=';border:2px solid black' id='entityMappings'>" +
        "<thead><tr style='border-bottom:2px solid black'>" +
        "<th style='display:none'>Device ID</th>" +
        "<th>Device Name</th>" +
        "<th>Entities</th>" +
        "<th>Primary Entity</th>" +
        "</tr></thead>"
    for (int d = 0; d < deviceListKeys?.size(); d++) {
        def deviceListKey = deviceListKeys[d]
        def device = deviceList[deviceListKey]
        String entityOptions = ""
        
        if (!device.deviceID && device.entities.size() > 1) entityOptions += "<option value='None'>Click to set</option>"
        for (int i = 0; i < device.entities.size(); i++) {
            def entity = device.entities[i]
            entityOptions += "<option value='" + entity + "' ${(entity == device.deviceID) ? "selected" : ""}>" + entity + "</option>"
        }
        
        str += "<tr style='color:black'>" +
            "<td style='display:none'><input id='deviceID$d' name='deviceID$d' type='text' value='${deviceListKey}'></td>" +
            "<td style='border-right:2px solid black'>${device.name}</td>" +
            "<td style='border-right:2px solid black'>${device.entities.join('\n')}</td>" +
            "<td><select id='entity$d' name='entity$d' value='${device.deviceID}' ${(device.entities.size() > 1) ? "" : "disabled"} onChange='captureEntityValue($d)'>" + entityOptions + "</select></td>" +
            "</tr>" +
            "<script>function captureEntityValue(val) {" +
            "var answer = {};answer.row = val;answer.deviceID = document.getElementById('deviceID' + val).value;answer.entity = document.getElementById('entity' + val).value;answer = JSON.stringify(answer);" +
            "var postBody = {'id': " + app.id + ",'name': 'mappingE' + answer + ''};" +
            "\$.post('/installedapp/btn', postBody,function (msg) {if (msg.status == 'success') {/*window.location.reload()*/}}, 'json');}</script>"
    }

    str += "</table>"
    return str
}

def appButtonHandler(btn) {
    //log.debug "btn: ${btn}"
    if (btn == "cleanupUnused") {
        // flag button pushed and let pages sort it out
        setButtonPushed(btn)
        return
    }
	
    if (btn.startsWith("mappingE")) {
        def rowData = btn.replace("mappingE", "")
        def slurper = new JsonSlurper()
        rowData = slurper.parseText(rowData)
        def deviceList = atomicState.deviceList
        deviceList[rowData.deviceID].deviceID = rowData.entity
        atomicState.deviceList = deviceList
        return
    }
}

def getDeviceDetails(deviceID) {
    def logMsg = ["getDeviceDetails - "]
    def answer = [:]
    def apiParams = [
        uri: "http://127.0.0.1:8080",
        path: "/device/fullJson/${deviceID}",
        headers: ["Content-Type": "text/json"]
    ]
    
    def updateAttributes = ["name", "label", "deviceTypeNamespace", "deviceTypeName", "deviceNetworkId"]
    
    try {
        httpGet(apiParams) {resp ->
            def apiResponse = resp.data
            def deviceDetails = apiResponse.device
            updateAttributes.each {attr ->
                def value = deviceDetails[attr]
                value = (value == null || value == '') ? "" : value
                //log.trace "${deviceID} ${attr}: ${value}"
                answer[attr] = value
            }
        }
    } catch (e) {
        log.error "getDeviceDetails - error: ${e}"
    }

    logMsg.push("answer: ${answer}")
    //log.debug "${logMsg}"
    return answer
}
