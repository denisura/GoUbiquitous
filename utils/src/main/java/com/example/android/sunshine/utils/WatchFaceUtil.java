/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.utils;

import android.net.Uri;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

public final class WatchFaceUtil {
    private static final String TAG = "WatchFaceUtil";

    public static final String KEY_WEATHER_ID = "KEY_WEATHER_ID";
    public static final String KEY_HIGH_TEMP = "KEY_HIGH_TEMP";
    public static final String KEY_LOW_TEMP = "KEY_LOW_TEMP";

    public static final String PATH_WEATHER = "/weather";

    public interface FetchConfigDataMapCallback {
        void onConfigDataMapFetched(DataMap config);
    }

    /**
     * Asynchronously fetches the current config {@link DataMap}
     * and passes it to the given callback.
     * <p>
     * If the current config {@link DataItem} doesn't exist, it isn't created and the callback
     * receives an empty DataMap.
     */
    public static void fetchConfigDataMap(final GoogleApiClient client,
                                          final FetchConfigDataMapCallback callback) {

        Log.d(TAG, "fetchConfigDataMap");
        Wearable.NodeApi.getLocalNode(client).setResultCallback(
                new ResultCallback<NodeApi.GetLocalNodeResult>() {
                    @Override
                    public void onResult(NodeApi.GetLocalNodeResult getLocalNodeResult) {
                        String localNode = getLocalNodeResult.getNode().getId();
                        Uri uri = new Uri.Builder()
                                .scheme(PutDataRequest.WEAR_URI_SCHEME)
                                .path(WatchFaceUtil.PATH_WEATHER)
                                .authority(localNode)
                                .build();
                        Wearable.DataApi.getDataItem(client, uri)
                                .setResultCallback(new DataItemResultCallback(callback));
                    }
                }
        );
    }

    public static void fetchConfigDataMapFromConnectedNode(final GoogleApiClient client, final FetchConfigDataMapCallback callback) {
        Wearable.NodeApi.getConnectedNodes(client).setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
            @Override
            public void onResult(NodeApi.GetConnectedNodesResult nodes) {

                Log.d(TAG, "fetchConfigDataMapFromConnectedNode:nodes:size: " + nodes.getNodes().size());

                if (nodes.getNodes().size() == 0) {
                    //wearable device is disconnected
                    //get data from local node

                    fetchConfigDataMap(client, callback);
                    return;
                }
                Node connectedNode = null;
                for (Node node : nodes.getNodes()) {
                    connectedNode = node;
                }
                if (connectedNode == null) {
                    return;
                }
                Uri uri = new Uri.Builder()
                        .scheme(PutDataRequest.WEAR_URI_SCHEME)
                        .path(WatchFaceUtil.PATH_WEATHER)
                        .authority(connectedNode.getId())
                        .build();
                Wearable.DataApi.getDataItem(client, uri)
                        .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                            @Override
                            public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
                                if (dataItemResult.getStatus().isSuccess()) {
                                    if (dataItemResult.getDataItem() != null) {
                                        DataItem configDataItem = dataItemResult.getDataItem();
                                        DataMapItem dataMapItem = DataMapItem.fromDataItem(configDataItem);
                                        DataMap config = dataMapItem.getDataMap();
                                        Log.d(TAG, "fetchConfigDataMapFromConnectedNode:config: " + config);

                                        putWeatherDataItem(client, config);
                                        callback.onConfigDataMapFetched(config);
                                    }
                                }
                            }
                        });
            }
        });
    }

    public static void setWeatherInfo(GoogleApiClient client, int weatherId, double highTemp, double lowTemp) {
        if (!client.isConnected()) {
            client.connect();
        }

        Log.d(TAG, "setWeatherInfo /" + weatherId + "/" + highTemp + "/" + lowTemp);

        DataMap weatherData = new DataMap();
        weatherData.putInt(WatchFaceUtil.KEY_WEATHER_ID, weatherId);
        weatherData.putDouble(WatchFaceUtil.KEY_HIGH_TEMP, highTemp);
        weatherData.putDouble(WatchFaceUtil.KEY_LOW_TEMP, lowTemp);

        putWeatherDataItem(client, weatherData);
    }

    /**
     * Overwrites the current config {@link DataItem}'s {@link DataMap} with {@code newConfig}.
     * If the config DataItem doesn't exist, it's created.
     */
    public static void putWeatherDataItem(GoogleApiClient googleApiClient, DataMap dataMap) {
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(PATH_WEATHER);
        putDataMapRequest.setUrgent();
        DataMap configToPut = putDataMapRequest.getDataMap();
        configToPut.putAll(dataMap);
        Wearable.DataApi.putDataItem(googleApiClient, putDataMapRequest.asPutDataRequest())
                .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                    @Override
                    public void onResult(DataApi.DataItemResult dataItemResult) {
                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                            Log.d(TAG, "putDataItem result status: " + dataItemResult.getStatus());
                        }
                    }
                });
    }

    private static class DataItemResultCallback implements ResultCallback<DataApi.DataItemResult> {

        private final FetchConfigDataMapCallback mCallback;

        public DataItemResultCallback(FetchConfigDataMapCallback callback) {
            mCallback = callback;
        }

        @Override
        public void onResult(DataApi.DataItemResult dataItemResult) {

            Log.d(TAG, "DataApi.DataItemResult");
            if (dataItemResult.getStatus().isSuccess()) {
                if (dataItemResult.getDataItem() != null) {
                    DataItem configDataItem = dataItemResult.getDataItem();
                    DataMapItem dataMapItem = DataMapItem.fromDataItem(configDataItem);
                    DataMap config = dataMapItem.getDataMap();
                    Log.d(TAG, "DataApi.DataItemResult:success " + config.toString());
                    mCallback.onConfigDataMapFetched(config);
                } else {
                    mCallback.onConfigDataMapFetched(new DataMap());
                }
            }
        }
    }

    private WatchFaceUtil() {
    }
}
