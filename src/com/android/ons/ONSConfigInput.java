/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.ons;

import static android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID;

import android.telephony.AvailableNetworkInfo;

import com.android.internal.telephony.IUpdateAvailableNetworksCallback;

import java.util.ArrayList;

/**
 * ONSConfigInput is data class that passes configuration parameters to
 * {@link OpportunisticNetworkService}.
 */
public record ONSConfigInput(ArrayList<AvailableNetworkInfo> availableNetworkInfos,
                             IUpdateAvailableNetworksCallback availableNetworkCallback,
                             int preferredDataSub, int primarySub) {
    /**
     * Construct ONSConfigInput with INVALID_SUBSCRIPTION_ID as default value for both {@code
     * preferredDataSub} and {@code primarySub}
     *
     * @param availableNetworkInfos An ArrayList of AvailableNetworkInfo
     * @param callback              IUpdateAvailableNetworksCallback to receive callback on update
     */
    public ONSConfigInput(ArrayList<AvailableNetworkInfo> availableNetworkInfos,
            IUpdateAvailableNetworksCallback callback) {
        this(availableNetworkInfos, callback, INVALID_SUBSCRIPTION_ID,
                INVALID_SUBSCRIPTION_ID);
    }
}
