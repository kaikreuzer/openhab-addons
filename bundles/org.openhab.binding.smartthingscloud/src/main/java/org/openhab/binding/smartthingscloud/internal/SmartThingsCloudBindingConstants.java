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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link SmartThingsCloudBindingConstants} class defines common constants,
 * which are used across the whole binding.
 *
 * @author Kai Kreuzer - Initial contribution
 */
@NonNullByDefault
public class SmartThingsCloudBindingConstants {

    public static final String BINDING_ID = "smartthingscloud";

    // Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_ACCOUNT = new ThingTypeUID(BINDING_ID, "account");
    public static final ThingTypeUID THING_TYPE_WINDFREE_AC = new ThingTypeUID(BINDING_ID, "windfree-ac");

    // Channels
    public static final String CHANNEL_POWER = "power";
    public static final String CHANNEL_TEMPERATURE = "temperature";
    public static final String CHANNEL_SETPOINT = "setpoint";
    public static final String CHANNEL_MODE = "mode";
}
