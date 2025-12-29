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
package org.openhab.binding.smartthingscloud.internal.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Client for SmartThings API.
 * 
 * @author Kai Kreuzer - Initial contribution
 */
@NonNullByDefault
public class SmartThingsClient {

    private final Logger logger = LoggerFactory.getLogger(SmartThingsClient.class);
    private final java.util.function.Supplier<String> tokenSupplier;
    private final HttpClient httpClient;
    private final Gson gson;

    private static final String API_BASE_URL = "https://api.smartthings.com/v1";

    public SmartThingsClient(java.util.function.Supplier<String> tokenSupplier) {
        this.tokenSupplier = tokenSupplier;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.gson = new Gson();
    }

    public CompletableFuture<java.util.List<org.openhab.binding.smartthingscloud.internal.client.dto.SmartThingsDevice>> getDevices() {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(API_BASE_URL + "/devices"))
                .header("Authorization", "Bearer " + tokenSupplier.get()).GET().build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            if (response.statusCode() == 401) {
                throw new RuntimeException("Unauthorized");
            }
            return response.body();
        }).thenApply(responseBody -> {
            logger.trace("Devices response: {}", responseBody);
            org.openhab.binding.smartthingscloud.internal.client.dto.SmartThingsDeviceList list = gson.fromJson(
                    responseBody, org.openhab.binding.smartthingscloud.internal.client.dto.SmartThingsDeviceList.class);
            return list != null && list.items != null ? list.items : java.util.Collections.emptyList();
        });
    }

    public CompletableFuture<JsonObject> getDeviceStatus(String deviceId) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE_URL + "/devices/" + deviceId + "/status"))
                .header("Authorization", "Bearer " + tokenSupplier.get()).GET().build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            if (response.statusCode() == 401) {
                throw new RuntimeException("Unauthorized");
            }
            return response.body();
        }).thenApply(responseBody -> {
            logger.trace("Device status response: {}", responseBody);
            JsonObject result = gson.fromJson(responseBody, JsonObject.class);
            return result != null ? result : new JsonObject();
        });
    }

    public CompletableFuture<Boolean> executeCommand(String deviceId, String capability, String command,
            @Nullable Object[] args) {
        JsonObject commandBody = new JsonObject();
        commandBody.addProperty("capability", capability);
        commandBody.addProperty("command", command);
        if (args.length > 0) {
            JsonArray arguments = new JsonArray();
            for (Object arg : args) {
                if (arg instanceof String) {
                    arguments.add((String) arg);
                } else if (arg instanceof Number) {
                    arguments.add((Number) arg);
                } else if (arg instanceof Boolean) {
                    arguments.add((Boolean) arg);
                }
            }
            commandBody.add("arguments", arguments);
        } else {
            commandBody.add("arguments", new JsonArray());
        }

        JsonArray commands = new JsonArray();
        commands.add(commandBody);

        JsonObject root = new JsonObject();
        root.add("commands", commands);

        String jsonBody = gson.toJson(root);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE_URL + "/devices/" + deviceId + "/commands"))
                .header("Authorization", "Bearer " + tokenSupplier.get()).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody)).build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            if (response.statusCode() == 401) {
                throw new RuntimeException("Unauthorized");
            }
            return response.body();
        }).thenApply(responseBody -> {
            logger.debug("Command response: {}", responseBody);
            return true; // Simple success check for now
        });
    }
}
