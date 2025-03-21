/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.annotation.Nullable;
import android.content.Context;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;

import com.android.ons.ONSProfileActivator.Result;
import com.android.ons.ONSProfileDownloader.DownloadRetryResultCode;

import java.util.List;

/**
 * ONSStatsInfo is the container class for ONS statistic information.
 */
public record ONSStatsInfo(Result provisioningResult, DownloadRetryResultCode downloadResult,
                           int primarySimSubId, int oppSimCarrierId, int retryCount,
                           int detailedErrCode, boolean isWifiConnected) {
    public static final int INVALID_VALUE = -1;

    public boolean isProvisioningResultUpdated() {
        return provisioningResult != null;
    }

    private ONSStatsInfo(Builder builder) {
        this(builder.mProvisioningResult, builder.mDownloadResult, builder.mPrimarySimSubId,
                builder.mOppSimCarrierId, builder.mRetryCount, builder.mDetailedErrCode,
                builder.mIsWifiConnected);
    }

    /**
     * Builder for {@link ONSStatsInfo}
     */
    public static final class Builder {
        @Nullable
        private Result mProvisioningResult;
        @Nullable
        private DownloadRetryResultCode mDownloadResult;
        private int mPrimarySimSubId;
        private int mOppSimCarrierId;
        private int mRetryCount;
        private int mDetailedErrCode;
        private boolean mIsWifiConnected;

        /**
         * Create a new Builder initialized data from the given ONSStatsInfo
         * @param other ONSStatsInfo for initial data
         */
        public Builder(ONSStatsInfo other) {
            mProvisioningResult = other.provisioningResult;
            mDownloadResult = other.downloadResult;
            mPrimarySimSubId = other.primarySimSubId;
            mOppSimCarrierId = other.oppSimCarrierId;
            mRetryCount = other.retryCount;
            mDetailedErrCode = other.detailedErrCode;
            mIsWifiConnected = other.isWifiConnected;
        }

        /**
         * Create a new Builder with default values.
         */
        public Builder() {
            mProvisioningResult = null;
            mDownloadResult = null;
            mPrimarySimSubId = INVALID_VALUE;
            mOppSimCarrierId = INVALID_VALUE;
            mRetryCount = INVALID_VALUE;
            mDetailedErrCode = INVALID_VALUE;
            mIsWifiConnected = false;
        }

        /**
         * Set the provisioning result.
         */
        public Builder setProvisioningResult(Context context, @Nullable Result result) {
            mProvisioningResult = result;
            mDownloadResult = null;
            // For provisioning errors, Result enum ordinal is set as detailed error code.
            mDetailedErrCode = result.ordinal();
            // add subscription id for carrier if it doesn't support CBRS.
            if (result == Result.ERR_CARRIER_DOESNT_SUPPORT_CBRS) {
                SubscriptionManager sm = context.getSystemService(SubscriptionManager.class);
                if (sm != null) {
                    List<SubscriptionInfo> subInfos = sm.getActiveSubscriptionInfoList();
                    mPrimarySimSubId =
                            (subInfos != null && !subInfos.isEmpty())
                                    ? subInfos.getFirst().getSubscriptionId()
                                    : INVALID_VALUE;
                }
            }
            return this;
        }

        /**
         * Set the download result
         */
        public Builder setDownloadResult(@Nullable DownloadRetryResultCode result) {
            mProvisioningResult = null;
            mDownloadResult = result;
            return this;
        }

        /**
         * Set the primary SIM subscription Id.
         */
        public Builder setPrimarySimSubId(int primarySimSubId) {
            mPrimarySimSubId = primarySimSubId;
            return this;
        }

        /**
         * Set the opportunistic SIM carrier Id.n
         */
        public Builder setOppSimCarrierId(int oppSimCarrierId) {
            mOppSimCarrierId = oppSimCarrierId;
            return this;
        }

        /**
         * Set the retry count.
         */
        public Builder setRetryCount(int retryCount) {
            mRetryCount = retryCount;
            return this;
        }

        /**
         * Set the detailed error code
         */
        public Builder setDetailedErrCode(int detailedErrCode) {
            mDetailedErrCode = detailedErrCode;
            return this;
        }

        /**
         * Set if WI-FI is connected.
         */
        public Builder setWifiConnected(boolean wifiConnected) {
            mIsWifiConnected = wifiConnected;
            return this;
        }

        /**
         * Build out a ONSStatsInfo object.
         */
        public ONSStatsInfo build() {
            return new ONSStatsInfo(this);
        }
    }
}
