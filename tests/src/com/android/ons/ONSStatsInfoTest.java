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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.InstrumentationRegistry;

import com.android.ons.ONSProfileActivator.Result;
import com.android.ons.ONSProfileDownloader.DownloadRetryResultCode;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ONSStatsInfoTest {
    private Context mContext;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getTargetContext();
    }

    @Test
    public void testProvisioningResult() {
        ONSStatsInfo info;
        info = new ONSStatsInfo.Builder()
                .setProvisioningResult(mContext, Result.ERR_AUTO_PROVISIONING_DISABLED)
                .build();
        assertEquals(Result.ERR_AUTO_PROVISIONING_DISABLED, info.provisioningResult());
        assertNull(info.downloadResult());
        assertTrue(info.isProvisioningResultUpdated());
    }

    @Test
    public void testDownloadResult() {
        ONSStatsInfo info;
        info = new ONSStatsInfo.Builder()
                .setDownloadResult(DownloadRetryResultCode.ERR_MEMORY_FULL)
                .build();
        assertEquals(DownloadRetryResultCode.ERR_MEMORY_FULL, info.downloadResult());
        assertNull(info.provisioningResult());
        assertFalse(info.isProvisioningResultUpdated());
    }

    @Test
    public void testPrimarySimSubId() {
        ONSStatsInfo info;
        info = new ONSStatsInfo.Builder().setPrimarySimSubId(1).build();
        assertEquals(1, info.primarySimSubId());
    }

    @Test
    public void testOppSimCarrierId() {
        ONSStatsInfo info;
        info = new ONSStatsInfo.Builder().setOppSimCarrierId(1221).build();
        assertEquals(1221, info.oppSimCarrierId());
    }

    @Test
    public void testRetryCount() {
        ONSStatsInfo info;
        info = new ONSStatsInfo.Builder().setRetryCount(3).build();
        assertEquals(3, info.retryCount());
    }

    @Test
    public void testDetailedErrCode() {
        ONSStatsInfo info;
        info = new ONSStatsInfo.Builder().setDetailedErrCode(1000).build();
        assertEquals(1000, info.detailedErrCode());
    }

    @Test
    public void testIsWifiConnected() {
        ONSStatsInfo info;
        info = new ONSStatsInfo.Builder().setWifiConnected(true).build();
        assertTrue(info.isWifiConnected());
    }

    @Test
    public void testIsProvisioningResultUpdated() {
        ONSStatsInfo info;
        info = new ONSStatsInfo.Builder()
                .setProvisioningResult(mContext, Result.ERR_ESIM_NOT_SUPPORTED)
                .build();
        assertTrue(info.isProvisioningResultUpdated());

        info = new ONSStatsInfo.Builder(info)
                .setDownloadResult(DownloadRetryResultCode.ERR_MEMORY_FULL)
                .build();
        assertFalse(info.isProvisioningResultUpdated());
    }
}
