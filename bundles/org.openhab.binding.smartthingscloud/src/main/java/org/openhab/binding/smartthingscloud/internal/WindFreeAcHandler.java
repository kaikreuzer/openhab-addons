/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.smartthingscloud.internal;

import java.util.concurrent.ScheduledFuture;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.smartthingscloud.internal.client.SmartThingsClient;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;

/**
 * Thing handler for Samsung WindFree AC.
 *
 * @author Kai Kreuzer - Initial contribution
 */
@NonNullByDefault
public class WindFreeAcHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(WindFreeAcHandler.class);
    private @Nullable SmartThingsClient client;
    private @Nullable String deviceId;
    private int refreshInterval = 60;
    private @Nullable ScheduledFuture<?> refreshJob;

    public WindFreeAcHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        String deviceId = (String) getThing().getConfiguration().get("deviceId");
        if (deviceId == null || deviceId.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Device ID is missing");
            return;
        }
        this.deviceId = deviceId;

        Object refreshIntervalObj = getThing().getConfiguration().get("refreshInterval");
        int interval = 60;
        if (refreshIntervalObj instanceof Number) {
            interval = ((Number) refreshIntervalObj).intValue();
        }
        if (interval < 10) {
            interval = 10;
        }
        this.refreshInterval = interval;

        Bridge bridge = getBridge();
        if (bridge == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED, "Bridge is missing");
            return;
        }

        ThingHandler bridgeHandler = bridge.getHandler();
        if (bridgeHandler instanceof SmartThingsAccountBridgeHandler) {
            this.client = ((SmartThingsAccountBridgeHandler) bridgeHandler).getClient();
        }

        if (this.client == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE, "Client is not ready");
            return;
        }

        startPolling();
    }

    @Override
    public void dispose() {
        stopPolling();
        super.dispose();
    }

    private void startPolling() {
        stopPolling();
        refreshJob = scheduler.scheduleWithFixedDelay(this::refreshState, 0, refreshInterval,
                java.util.concurrent.TimeUnit.SECONDS);
    }

    private void stopPolling() {
        java.util.concurrent.ScheduledFuture<?> job = refreshJob;
        if (job != null && !job.isCancelled()) {
            job.cancel(false);
            refreshJob = null;
        }
    }

    private void refreshState() {
        final SmartThingsClient cl = client;
        final String devId = deviceId;

        if (cl != null && devId != null) {
            cl.getDeviceStatus(devId).thenAccept(status -> {
                updateStatus(ThingStatus.ONLINE);
                updateChannels(status);
            }).exceptionally(e -> {
                Throwable cause = e.getCause();
                String msg = cause != null ? cause.getMessage() : e.getMessage();
                if ("Unauthorized".equals(msg)) {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                            "Invalid or expired Personal Access Token");
                } else {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, msg);
                }
                return null;
            });
        }
    }

    private void updateChannels(JsonObject status) {
        // The status object is guaranteed to be non-null here due to the check in
        // refreshState
        // and the nature of the SmartThings API response.
        // If 'components' or 'main' are missing, getAsJsonObject will return null,
        // which is handled by subsequent checks (e.g., mainComponent.has("switch")).
        JsonObject mainComponent = status.getAsJsonObject("components").getAsJsonObject("main");
        if (mainComponent == null) {
            return;
        }

        // Power (Switch)
        if (mainComponent.has("switch") && mainComponent.getAsJsonObject("switch").has("switch")) {
            String switchState = mainComponent.getAsJsonObject("switch").getAsJsonObject("switch").get("value")
                    .getAsString();
            updateState(SmartThingsCloudBindingConstants.CHANNEL_POWER,
                    "on".equalsIgnoreCase(switchState) ? OnOffType.ON : OnOffType.OFF);
        }

        // Temperature (Number)
        if (mainComponent.has("temperatureMeasurement")
                && mainComponent.getAsJsonObject("temperatureMeasurement").has("temperature")) {
            double temp = mainComponent.getAsJsonObject("temperatureMeasurement").getAsJsonObject("temperature")
                    .get("value").getAsDouble();
            updateState(SmartThingsCloudBindingConstants.CHANNEL_TEMPERATURE, new QuantityType<>(temp + " °C"));
        }

        // Setpoint (Number)
        if (mainComponent.has("thermostatCoolingSetpoint")
                && mainComponent.getAsJsonObject("thermostatCoolingSetpoint").has("coolingSetpoint")) {
            // Logic to determine if we are in cooling or heating mode to pick the right
            // setpoint
            // For simplicity, let's grab coolingSetpoint if in cool mode, or just grab one.
            // Usually WindFree ACs have coolingSetpoint.
            double setpoint = mainComponent.getAsJsonObject("thermostatCoolingSetpoint")
                    .getAsJsonObject("coolingSetpoint").get("value").getAsDouble();
            updateState(SmartThingsCloudBindingConstants.CHANNEL_SETPOINT, new QuantityType<>(setpoint + " °C"));
        }

        // Mode (String)
        if (mainComponent.has("airConditionerMode")
                && mainComponent.getAsJsonObject("airConditionerMode").has("airConditionerMode")) {
            String mode = mainComponent.getAsJsonObject("airConditionerMode").getAsJsonObject("airConditionerMode")
                    .get("value").getAsString();
            updateState(SmartThingsCloudBindingConstants.CHANNEL_MODE, StringType.valueOf(mode));
        }
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        if (bridgeStatusInfo.getStatus() == ThingStatus.ONLINE) {
            initialize();
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
            stopPolling();
            client = null;
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        final SmartThingsClient cl = client;
        final String devId = deviceId;

        if (cl == null || devId == null) {
            logger.warn("Cannot handle command, client or deviceId is null");
            return;
        }

        String capability = "";
        String cmd = "";
        Object[] args = new Object[0];

        switch (channelUID.getId()) {
            case SmartThingsCloudBindingConstants.CHANNEL_POWER:
                capability = "switch";
                if (command instanceof OnOffType) {
                    cmd = command == OnOffType.ON ? "on" : "off";
                }
                break;
            case SmartThingsCloudBindingConstants.CHANNEL_SETPOINT:
                capability = "thermostatCoolingSetpoint"; // Or thermostatHeatingSetpoint
                cmd = "setCoolingSetpoint";
                if (command instanceof QuantityType) {
                    args = new Object[] { ((QuantityType<?>) command).toBigDecimal().intValue() };
                } else if (command instanceof DecimalType) {
                    args = new Object[] { ((DecimalType) command).intValue() };
                }
                break;
            case SmartThingsCloudBindingConstants.CHANNEL_MODE:
                capability = "airConditionerMode";
                cmd = "setAirConditionerMode";
                if (command instanceof StringType) {
                    args = new Object[] { command.toString() };
                }
                break;
        }

        if (!capability.isEmpty() && !cmd.isEmpty()) {
            cl.executeCommand(devId, capability, cmd, args);
        }
    }
}
