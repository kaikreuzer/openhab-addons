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

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.smartthingscloud.internal.client.SmartThingsClient;
import org.openhab.binding.smartthingscloud.internal.client.dto.SmartThingsDevice;
import org.openhab.core.config.discovery.AbstractDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.config.discovery.DiscoveryService;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Discovery service for SmartThings devices.
 * 
 * @author Kai Kreuzer - Initial contribution
 */
@NonNullByDefault
@Component(service = { DiscoveryService.class,
        SmartThingsDiscoveryService.class }, immediate = true, configurationPid = "discovery.smartthings")
public class SmartThingsDiscoveryService extends AbstractDiscoveryService {

    private final Logger logger = LoggerFactory.getLogger(SmartThingsDiscoveryService.class);
    private final java.util.Set<SmartThingsAccountBridgeHandler> bridgeHandlers = java.util.concurrent.ConcurrentHashMap
            .newKeySet();

    @Activate
    public SmartThingsDiscoveryService() {
        super(Set.of(SmartThingsCloudBindingConstants.THING_TYPE_WINDFREE_AC), 30, true);
    }

    public void registerBridgeHandler(SmartThingsAccountBridgeHandler handler) {
        bridgeHandlers.add(handler);
    }

    public void unregisterBridgeHandler(SmartThingsAccountBridgeHandler handler) {
        bridgeHandlers.remove(handler);
    }

    @Override
    protected void startScan() {
        logger.debug("Starting SmartThings device scan...");
        for (SmartThingsAccountBridgeHandler bridgeHandler : bridgeHandlers) {
            SmartThingsClient client = bridgeHandler.getClient();
            if (client != null) {
                scanAccount(client, bridgeHandler.getThing().getUID());
            }
        }
    }

    private void scanAccount(SmartThingsClient client, ThingUID bridgeUID) {
        client.getDevices().thenAccept(devices -> {
            for (SmartThingsDevice device : devices) {
                ThingTypeUID thingType = determineThingType(device);
                if (thingType != null) {
                    ThingUID thingUID = new ThingUID(thingType, device.deviceId);
                    DiscoveryResult result = DiscoveryResultBuilder.create(thingUID)
                            .withLabel(device.label != null ? device.label : device.name)
                            .withProperty("deviceId", device.deviceId).withBridge(bridgeUID).build();
                    thingDiscovered(result);
                }
            }
        }).exceptionally(e -> {
            if (e.getCause() instanceof java.io.IOException) {
                logger.debug("Connection error discovering devices for bridge {}: {}", bridgeUID, e.getMessage());
            } else {
                logger.error("Error discovering devices for bridge {}", bridgeUID, e);
            }
            return null;
        });
    }

    private @Nullable ThingTypeUID determineThingType(SmartThingsDevice device) {
        // If it has 'airConditionerMode' capability, assume it's an AC.
        if (device.components != null) {
            boolean hasAcMode = device.components.stream().flatMap(c -> c.capabilities.stream()).anyMatch(
                    cap -> "airConditionerMode".equals(cap.id) || "custom.airConditionerOptionalMode".equals(cap.id));

            if (hasAcMode) {
                return SmartThingsCloudBindingConstants.THING_TYPE_WINDFREE_AC;
            }
        }
        return null;
    }
}
