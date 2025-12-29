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

import static org.openhab.binding.smartthingscloud.internal.SmartThingsCloudBindingConstants.*;

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.service.component.annotations.Component;

/**
 * The {@link SmartThingsCloudHandlerFactory} is responsible for creating things
 * and thing handlers.
 *
 * @author Kai Kreuzer - Initial contribution
 */
@NonNullByDefault
@Component(configurationPid = "binding.smartthingscloud", service = ThingHandlerFactory.class)
public class SmartThingsCloudHandlerFactory extends BaseThingHandlerFactory {

    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Set.of(THING_TYPE_ACCOUNT,
            THING_TYPE_WINDFREE_AC);

    private final SmartThingsDiscoveryService discoveryService;
    private final org.openhab.core.auth.client.oauth2.OAuthFactory oauthFactory;

    @org.osgi.service.component.annotations.Activate
    public SmartThingsCloudHandlerFactory(
            @org.osgi.service.component.annotations.Reference SmartThingsDiscoveryService discoveryService,
            @org.osgi.service.component.annotations.Reference org.openhab.core.auth.client.oauth2.OAuthFactory oauthFactory) {
        this.discoveryService = discoveryService;
        this.oauthFactory = oauthFactory;
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (THING_TYPE_ACCOUNT.equals(thingTypeUID)) {
            return new SmartThingsAccountBridgeHandler((org.openhab.core.thing.Bridge) thing, discoveryService,
                    oauthFactory);
        } else if (THING_TYPE_WINDFREE_AC.equals(thingTypeUID)) {
            return new WindFreeAcHandler(thing);
        }

        return null;
    }
}
