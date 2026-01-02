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
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.smartthingscloud.internal.client.SmartThingsClient;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpServer;

/**
 * Bridge handler for SmartThings Account.
 *
 * @author Kai Kreuzer - Initial contribution
 */
@NonNullByDefault
public class SmartThingsAccountBridgeHandler extends BaseBridgeHandler {

    private static final String AUTH_URL = "https://oauthin-regional.api.smartthings.com/oauth/authorize";
    private static final String TOKEN_URL = "https://oauthin-regional.api.smartthings.com/oauth/token";
    private static final String SCOPE = "controller:stCli";
    private static final String CLI_CLIENT_ID = "d18cf96e-c626-4433-bf51-ddbb10c5d1ed";
    private static final String REDIRECT_URI = "http://localhost:61973/finish";

    private final Logger logger = LoggerFactory.getLogger(SmartThingsAccountBridgeHandler.class);
    private final SmartThingsDiscoveryService discoveryService;
    private final org.openhab.core.auth.client.oauth2.OAuthFactory oauthFactory;
    private @Nullable SmartThingsClient client;
    private org.openhab.core.auth.client.oauth2.@Nullable OAuthClientService oauthClientService;
    private @Nullable HttpServer callbackServer;

    public SmartThingsAccountBridgeHandler(Bridge bridge, SmartThingsDiscoveryService discoveryService,
            org.openhab.core.auth.client.oauth2.OAuthFactory oauthFactory) {
        super(bridge);
        this.discoveryService = discoveryService;
        this.oauthFactory = oauthFactory;
    }

    @Override
    public void initialize() {
        initializeOAuth(CLI_CLIENT_ID);
        discoveryService.registerBridgeHandler(this);
    }

    private void initializeOAuth(String clientId) {
        oauthClientService = oauthFactory.createOAuthClientService(getThing().getUID().getAsString(), TOKEN_URL,
                AUTH_URL, clientId, null, SCOPE, true);

        try {
            org.openhab.core.auth.client.oauth2.AccessTokenResponse response = oauthClientService
                    .getAccessTokenResponse();
            if (response != null && response.getAccessToken() != null) {
                setupClient();
            } else {
                startCallbackListener();

                org.openhab.core.auth.client.oauth2.OAuthClientService srv = oauthClientService;
                String authUrl = srv != null
                        ? srv.getAuthorizationUrl(REDIRECT_URI, SCOPE, getThing().getUID().getId())
                                + "&client_type=USER_LEVEL"
                        : "";
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                        "Please authorize the binding by visiting: " + authUrl
                                + "\nThe authorization code will be captured automatically.");
            }
        } catch (Exception e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "OAuth failed: " + e.getMessage());
        }
    }

    private void startCallbackListener() {
        stopCallbackListener();
        try {
            HttpServer server = HttpServer.create(new java.net.InetSocketAddress(61973), 0);
            callbackServer = server;
            server.createContext("/finish", exchange -> {
                String query = exchange.getRequestURI().getQuery();
                if (query != null && query.contains("code=")) {
                    String code = query.split("code=")[1].split("&")[0];
                    logger.debug("Captured auth code: {}", code);

                    String response = "Authorization successful! You can now close this window.";
                    exchange.sendResponseHeaders(200, response.length());
                    java.io.OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes());
                    os.close();

                    // Finish OAuth flow
                    finishOAuth(code, getThing().getUID().getId());
                    stopCallbackListener();
                } else {
                    exchange.sendResponseHeaders(400, 0);
                    exchange.close();
                }
            });
            server.setExecutor(null);
            server.start();
            logger.info("Started OAuth callback listener on port 61973");
        } catch (java.io.IOException e) {
            logger.error("Failed to start OAuth callback listener", e);
        }
    }

    private void stopCallbackListener() {
        HttpServer server = callbackServer;
        if (server != null) {
            server.stop(0);
            callbackServer = null;
        }
    }

    private void finishOAuth(String code, String verifier) {
        org.openhab.core.auth.client.oauth2.OAuthClientService srv = oauthClientService;
        if (srv != null) {
            try {
                srv.addExtraAuthField("code_verifier", verifier);
                org.openhab.core.auth.client.oauth2.AccessTokenResponse response = srv
                        .getAccessTokenResponseByAuthorizationCode(code, REDIRECT_URI);
                if (response.getAccessToken() != null) {
                    setupClient();
                } else {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                            "Failed to exchange code for tokens");
                }
            } catch (Exception e) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                        "Token exchange failed: " + e.getMessage());
            }
        }
    }

    private void setupClient() {
        final org.openhab.core.auth.client.oauth2.OAuthClientService srv = oauthClientService;
        if (srv != null) {
            client = new SmartThingsClient(() -> {
                try {
                    org.openhab.core.auth.client.oauth2.AccessTokenResponse resp = srv.getAccessTokenResponse();
                    return resp != null ? resp.getAccessToken() : "";
                } catch (Exception e) {
                    return "";
                }
            });
            updateStatus(ThingStatus.ONLINE);
        }
    }

    @Override
    public void dispose() {
        stopCallbackListener();
        discoveryService.unregisterBridgeHandler(this);
        super.dispose();
    }

    public @Nullable SmartThingsClient getClient() {
        return client;
    }

    @Override
    public void handleCommand(org.openhab.core.thing.ChannelUID channelUID, org.openhab.core.types.Command command) {
        // No channels on the bridge
    }
}
