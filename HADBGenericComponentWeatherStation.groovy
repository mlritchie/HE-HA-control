/*

Copyright 2025

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

*/

import groovy.transform.Field

@Field static Map luxOpts = [
	defaultValue: 10414
	,defaultText: "On 10 Lux change"
	,options:[
		3010:"On 1 Lux change",10414:"On 10 Lux change",20043:"On 100 Lux change",23032:"On 200 Lux change",26998:"On 500 Lux change"
		,(-3):"log10 precision 3",(-2):"log10 precision 2",(-1):"log10 precision 1",(0):"log10 precision 0"
		,65535:"Disable"
	]
]

@Field static Map temperatureOpts = [
	defaultValue: "-1",
	defaultText: "On 0.5 change",
	options:[
		0.5:"On 0.5 change",
        1:"On 1.0 change",
		5:"On 5.0 change",
		0:"Disable"
	]
]

metadata {
    definition(name: "HADB Generic Component Weather Station", namespace: "community", author: "community") {
        capability "IlluminanceMeasurement"
        capability "TemperatureMeasurement"
        capability "RelativeHumidityMeasurement"
        capability "Refresh"
        capability "Health Check"
    }
    preferences {
        input name: "luxThreshold", type:"enum", title: "Lux Reporting (default:${temperatureOpts.defaultText})", options:luxOpts.options , defaultValue:luxOpts.defaultValue
        input name: "tempThreshold", type:"enum", title: "Temperature Reporting (default:${temperatureOpts.defaultText})", options:temperatureOpts.options , defaultValue:temperatureOpts.defaultValue
        input name: "humidityThreshold", type:"enum", title: "Humidity Reporting (default:${temperatureOpts.defaultText})", options:temperatureOpts.options , defaultValue:temperatureOpts.defaultValue
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
    }
    attribute "healthStatus", "enum", ["offline", "online"]
    attribute "feelsLike", "string"
}

void updated() {
    log.info "Updated..."
    log.warn "description logging is: ${txtEnable == true}"
}

void installed() {
    log.info "Installed..."
    device.updateSetting("txtEnable",[type:"bool",value:true])
    refresh()
}

void parse(String description) { log.warn "parse(String description) not implemented" }

void parse(List<Map> description) {
    description.each {
        if (it.name in ["illuminance", "temperature", "humidity", "feelsLike", "healthStatus"]) {
            def processEvent = true
            if (it.name in ["illuminance"]) {
                Integer value = Math.max((Math.pow(10,(it.value.toInteger()/10000)) - 1).toInteger(),1)
                if (it.value.toInteger() == 0) value = 0
                
                def previousValue = device.currentValue("illuminance") ?: 0
                if (previousValue == value) {
                    processEvent = false
                } else {
                    Integer luxThreshold = (luxThreshold?:luxOpts.defaultValue).toInteger()
                    if (luxThreshold <= 0) { //log options
                        luxThreshold = Math.abs(luxThreshold)
                        if (Math.log10(value).round(luxThreshold) == Math.log10(previousValue).round(luxThreshold)) processEvent = false

                    }
                }
                
                /*
                void sendIlluminanceEvent(rawValue) {
                	Integer value = getLuxValue(rawValue)
                	if (rawValue.toInteger() == 0) value = 0
                	Integer pv = device.currentValue("illuminance") ?: 0
                    Integer luxThreshold = (luxThreshold?:luxOpts.defaultValue).toInteger()
                	if (luxThreshold <= 0) { //log options
                		luxThreshold = Math.abs(luxThreshold)
                		if (Math.log10(value).round(luxThreshold) == Math.log10(pv).round(luxThreshold)) return
                	}
                	String descriptionText = "${device.displayName} illuminance is ${value} Lux"
                	if (txtEnable) log.info descriptionText
                	if (pv == value) return
                    sendEvent(name: "illuminance",value: value,descriptionText: descriptionText,unit: "Lux")
                }
                Integer getLuxValue(rawValue) {
					return Math.max((Math.pow(10,(rawValue/10000)) - 1).toInteger(),1)
                }
                */
                
            } else if (it.name in ["temperature", "feelsLike"]) {
                def value = it.value.toDouble().round(1)
                def previousValue = device.currentValue(it.name) ? device.currentValue(it.name).toDouble() : 0.0
                
                if (value == previousValue) {
                    processEvent = false
                } else if (settings.tempThreshold != 0) {
                    def tempThreshold = settings.tempThreshold?:temperatureOpts.defaultValue
                    
                    if (logEnable) log.debug "temp: value: ${value}, previousValue: ${previousValue}, ${Math.abs(value-previousValue)} > ${tempThreshold}} = ${Math.abs(value-previousValue) > tempThreshold.toDouble()}"
                    if (Math.abs(value-previousValue) <= tempThreshold.toDouble()) {
                        processEvent = false
                    }
                    
                    it.value = value
                }
            } else if (it.name in ["humidity"]) {
                def value = Math.floor(it.value.toDouble())
                def previousValue = device.currentValue("humidity") ?: 0
                if (value == previousValue) {
                    processEvent = false
                } else if (settings.humidityThreshold != 0) {
                    def humidityThreshold = settings.humidityThreshold?:temperatureOpts.defaultValue
                    
                    if (logEnable) log.debug "humidity: value: ${value}, previousValue: ${previousValue}, ${Math.abs(value-previousValue)} > ${humidityThreshold}} = ${Math.abs(value-previousValue) > humidityThreshold.toDouble()}"
                    if (Math.abs(value-previousValue) <= humidityThreshold.toDouble()) {
                        processEvent = false
                    }
                    
                    it.value = value
                }
            }

            if (processEvent) {
                if (txtEnable && it.descriptionText) {
                    log.info it.descriptionText
                }
                sendEvent(it)
            }
        } else {
            log.trace "unparsed: ${it}"
        }
    }
}

void refresh() {
    parent?.componentRefresh(this.device)
}

void ping() {
    refresh()
}
