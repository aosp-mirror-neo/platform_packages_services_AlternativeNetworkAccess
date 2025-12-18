  /*
 * Copyright (C) 2019 The Android Open Source Project
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
 * limitations under the License
 */
package com.android.ons;

import static com.android.internal.telephony.util.TelephonyUtils.TELEPHONY_FEATURE_ENFORCEMENT_VENDOR_API_LEVEL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.compat.testing.PlatformCompatChangeRule;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Looper;
import android.os.RemoteException;
import android.os.UserManager;
import android.platform.test.flag.junit.SetFlagsRule;
import android.telephony.AvailableNetworkInfo;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.test.runner.AndroidJUnit4;

import com.android.internal.telephony.IOns;
import com.android.internal.telephony.ISetOpportunisticDataCallback;
import com.android.internal.telephony.IUpdateAvailableNetworksCallback;

import libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class OpportunisticNetworkServiceTest extends ONSBaseTest {
    // SetFlagsRule will be used for Telephony feature flag
    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();
    @Rule
    public TestRule compatChangeRule = new PlatformCompatChangeRule();

    private static final String TAG = "ONSTest";
    private String pkgForDebug;
    private String pkgForFeature;
    private int mResult;
    private IOns iOpportunisticNetworkService;
    private Looper mLooper;
    private OpportunisticNetworkService mOpportunisticNetworkService;

    private final IUpdateAvailableNetworksCallback mUpdateAvailableNetworksCallback =
            new IUpdateAvailableNetworksCallback.Stub() {
                @Override
                public void onComplete(int result) {
                    Log.d(TAG, "mUpdateAvailableNetworksCallback, mResult end:" + result);
                    mResult = result;
                    setReady(true);
                }
            };

    private final ISetOpportunisticDataCallback mSetOpptDataCallback =
            new ISetOpportunisticDataCallback.Stub() {
                @Override
                public void onComplete(int result) {
                    Log.d(TAG, "mSetOpptDataCallback, mResult end:" + result);
                    mResult = result;
                    setReady(true);
                }
            };

    private static final String CARRIER_APP_CONFIG_NAME = "carrierApp";
    private static final String SYSTEM_APP_CONFIG_NAME = "systemApp";

    @Mock
    private HashMap<String, ONSConfigInput> mockONSConfigInputHashMap;
    @Mock
    private ONSProfileSelector mockProfileSelector;
    @Mock
    private CarrierConfigManager mCarrierConfigManager;
    @Mock
    private ONSProfileActivator mONSProfileActivator;
    @Mock
    private UserManager mUserManager;
    @Mock
    private Context mMockContext;
    @Mock
    private PackageManager mPackageManager;

    @Before
    public void setUp() throws Exception {
        super.setUp("ONSTest");
        initONS();
    }

    private void initONS() {
        pkgForDebug = mContext != null ? mContext.getOpPackageName() : "<unknown>";
        pkgForFeature = mContext != null ? mContext.getAttributionTag() : null;
        Intent intent = new Intent(mContext, OpportunisticNetworkService.class);
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                mOpportunisticNetworkService = new OpportunisticNetworkService();
                mOpportunisticNetworkService.initialize(mContext);
                mOpportunisticNetworkService.mSubscriptionManager = mSubscriptionManager;
                mOpportunisticNetworkService.mPackageManager = mPackageManager;
                for (int retry = 2; retry > 0; retry--) {

                    iOpportunisticNetworkService = (IOns) mOpportunisticNetworkService.onBind(null);

                    if (iOpportunisticNetworkService != null) {
                        break;
                    }

                    try {
                        Thread.sleep(100);
                    } catch (Exception e) {
                        Log.e(TAG, e.toString());
                    }
                }
                mLooper = Looper.myLooper();
                setReady(true);
                Looper.loop();
            }
        }).start();
        waitUntilReady(300);

        // In order not to affect the existing implementation, define a telephony feature
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_DATA))
                .thenReturn(true);
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS))
                .thenReturn(true);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        if (mOpportunisticNetworkService != null) {
            mOpportunisticNetworkService.onDestroy();
        }
        if (mLooper != null) {
            mLooper.quit();
            mLooper.getThread().join();
        }
    }

    private void runUntilReady(Runnable task) {
        mResult = -1;
        setReady(false);
        task.run();
        waitUntilReady();
        assertTrue(mReady);
    }

    @Test
    public void testCheckEnable() {
        boolean isEnable = true;
        try {
            iOpportunisticNetworkService.setEnable(false, pkgForDebug);
            isEnable = iOpportunisticNetworkService.isEnabled(pkgForDebug);
        } catch (RemoteException ex) {
            Log.e(TAG, "RemoteException", ex);
        }
        assertEquals(false, isEnable);
    }

    @Test
    public void testHandleSimStateChange() {
        ArrayList<String> mccMncs = new ArrayList<>();
        mccMncs.add("310210");
        AvailableNetworkInfo availableNetworkInfo = new AvailableNetworkInfo(1, 1, mccMncs,
                new ArrayList<Integer>());
        List<AvailableNetworkInfo> availableNetworkInfos =
                new ArrayList<AvailableNetworkInfo>();
        availableNetworkInfos.add(availableNetworkInfo);
        ONSConfigInput onsConfigInput =
                new ONSConfigInput(
                        availableNetworkInfos,
                        mUpdateAvailableNetworksCallback,
                        availableNetworkInfos.get(0).getSubId(),
                        1);
        List<SubscriptionInfo> subscriptionInfos = new ArrayList<SubscriptionInfo>();

        // Case 1: There is no Carrier app using ONS.
        doReturn(null).when(mockONSConfigInputHashMap).get(CARRIER_APP_CONFIG_NAME);
        mOpportunisticNetworkService.mIsEnabled = true;
        mOpportunisticNetworkService.mONSConfigInputHashMap = mockONSConfigInputHashMap;
        mOpportunisticNetworkService.handleSimStateChange();
        verify(mockONSConfigInputHashMap, never()).get(SYSTEM_APP_CONFIG_NAME);

        // Case 2: There is a Carrier app using ONS and no System app input.
        doReturn(subscriptionInfos).when(mSubscriptionManager).getActiveSubscriptionInfoList(false);
        doReturn(onsConfigInput).when(mockONSConfigInputHashMap).get(CARRIER_APP_CONFIG_NAME);
        doReturn(null).when(mockONSConfigInputHashMap).get(SYSTEM_APP_CONFIG_NAME);
        mOpportunisticNetworkService.mIsEnabled = true;
        mOpportunisticNetworkService.mONSConfigInputHashMap = mockONSConfigInputHashMap;
        mOpportunisticNetworkService.handleSimStateChange();
        verify(mockONSConfigInputHashMap, times(1)).get(SYSTEM_APP_CONFIG_NAME);
    }

    @Test
    public void testSystemPreferredDataWhileCarrierAppIsActive() {
        ArrayList<String> mccMncs = new ArrayList<>();
        mccMncs.add("310210");
        AvailableNetworkInfo availableNetworkInfo = new AvailableNetworkInfo(1, 1, mccMncs,
                new ArrayList<Integer>());
        List<AvailableNetworkInfo> availableNetworkInfos =
                new ArrayList<AvailableNetworkInfo>();
        availableNetworkInfos.add(availableNetworkInfo);
        ONSConfigInput onsConfigInput =
                new ONSConfigInput(
                        availableNetworkInfos,
                        mUpdateAvailableNetworksCallback,
                        availableNetworkInfos.get(0).getSubId(),
                        1);
        List<SubscriptionInfo> subscriptionInfos = new ArrayList<SubscriptionInfo>();

        doReturn(subscriptionInfos).when(mSubscriptionManager).getActiveSubscriptionInfoList(false);
        doReturn(onsConfigInput).when(mockONSConfigInputHashMap).get(CARRIER_APP_CONFIG_NAME);
        mOpportunisticNetworkService.mIsEnabled = true;
        mOpportunisticNetworkService.mONSConfigInputHashMap = mockONSConfigInputHashMap;

        runUntilReady(() -> {
            try {
                IOns onsBinder = (IOns) mOpportunisticNetworkService.onBind(null);
                onsBinder.setPreferredDataSubscriptionId(
                        SubscriptionManager.DEFAULT_SUBSCRIPTION_ID, false, mSetOpptDataCallback,
                        pkgForDebug);
            } catch (RemoteException ex) {
                Log.e(TAG, "RemoteException", ex);
            }
        });
        assertEquals(TelephonyManager.SET_OPPORTUNISTIC_SUB_VALIDATION_FAILED, mResult);
    }

    @Test
    public void testSetPreferredDataSubscriptionId() {
        runUntilReady(
                () -> {
                    try {
                        assertNotNull(iOpportunisticNetworkService);
                        iOpportunisticNetworkService.setPreferredDataSubscriptionId(
                                5, false, mSetOpptDataCallback, pkgForDebug);
                    } catch (RemoteException ex) {
                        Log.e(TAG, "RemoteException", ex);
                    }
                });
        assertEquals(
                TelephonyManager.SET_OPPORTUNISTIC_SUB_NO_OPPORTUNISTIC_SUB_AVAILABLE, mResult);
    }

    @Test
    public void testGetPreferredDataSubscriptionId() {
        assertNotNull(iOpportunisticNetworkService);
        mResult = -1;
        try {
            mResult = iOpportunisticNetworkService.getPreferredDataSubscriptionId(pkgForDebug,
                    pkgForFeature);
            Log.d(TAG, "testGetPreferredDataSubscriptionId: " + mResult);
        } catch (RemoteException ex) {
            Log.e(TAG, "RemoteException", ex);
        }
    }

    @Test
    public void testUpdateAvailableNetworksWithInvalidArguments() {
        ArrayList<String> mccMncs = new ArrayList<>();
        mccMncs.add("310210");
        AvailableNetworkInfo availableNetworkInfo = new AvailableNetworkInfo(1, 1, mccMncs,
                new ArrayList<Integer>());
        List<AvailableNetworkInfo> availableNetworkInfos = new ArrayList<>();
        availableNetworkInfos.add(availableNetworkInfo);

        runUntilReady(
                () -> {
                    try {
                        iOpportunisticNetworkService.updateAvailableNetworks(
                                availableNetworkInfos,
                                mUpdateAvailableNetworksCallback,
                                pkgForDebug);
                    } catch (RemoteException ex) {
                        Log.e(TAG, "RemoteException", ex);
                    }
                });
        assertEquals(
                TelephonyManager.UPDATE_AVAILABLE_NETWORKS_NO_OPPORTUNISTIC_SUB_AVAILABLE, mResult);
    }

    @Test
    public void testUpdateAvailableNetworksWithSuccess() {
        List<AvailableNetworkInfo> availableNetworkInfos = new ArrayList<>();
        runUntilReady(
                () -> {
                    try {
                        iOpportunisticNetworkService.setEnable(false, pkgForDebug);
                        iOpportunisticNetworkService.updateAvailableNetworks(
                                availableNetworkInfos,
                                mUpdateAvailableNetworksCallback,
                                pkgForDebug);
                    } catch (RemoteException ex) {
                        Log.e(TAG, "RemoteException", ex);
                    }
                });
        assertEquals(TelephonyManager.UPDATE_AVAILABLE_NETWORKS_SUCCESS, mResult);
    }

    @Test
    public void testPriorityRuleOfActivatingAvailableNetworks() {
        ArrayList<String> mccMncs = new ArrayList<>();
        mccMncs.add("310210");
        AvailableNetworkInfo availableNetworkInfo = new AvailableNetworkInfo(1, 1, mccMncs,
                new ArrayList<Integer>());
        List<AvailableNetworkInfo> availableNetworkInfos =
                new ArrayList<AvailableNetworkInfo>();
        availableNetworkInfos.add(availableNetworkInfo);
        ONSConfigInput onsConfigInput =
                new ONSConfigInput(
                        availableNetworkInfos,
                        mUpdateAvailableNetworksCallback,
                        availableNetworkInfos.get(0).getSubId(),
                        1);
        doReturn(onsConfigInput).when(mockONSConfigInputHashMap).get(CARRIER_APP_CONFIG_NAME);
        doReturn(true).when(mockProfileSelector).hasOpportunisticSub(any());
        doReturn(false).when(mockProfileSelector).containStandaloneOppSubs(any());
        mOpportunisticNetworkService.mIsEnabled = true;
        mOpportunisticNetworkService.mONSConfigInputHashMap = mockONSConfigInputHashMap;
        mOpportunisticNetworkService.mProfileSelector = mockProfileSelector;

        // Assume carrier app has updated available networks at first.
        // Then system app updated available networks which is not standalone.
        mReady = false;
        try {
            iOpportunisticNetworkService.updateAvailableNetworks(
                    availableNetworkInfos, mUpdateAvailableNetworksCallback, pkgForDebug);
        } catch (RemoteException ex) {
            Log.e(TAG, "RemoteException", ex);
            fail("Unexpected RemoteException");
        }
        waitUntilReady();
        verify(mockProfileSelector, never()).startProfileSelection(any(), any());

        // System app updated available networks which contain standalone network.
        doReturn(true).when(mockProfileSelector).containStandaloneOppSubs(any());
        mReady = false;
        try {
            iOpportunisticNetworkService.updateAvailableNetworks(
                    availableNetworkInfos, mUpdateAvailableNetworksCallback, pkgForDebug);
        } catch (RemoteException ex) {
            Log.e(TAG, "RemoteException", ex);
        }
        waitUntilReady();
        verify(mockProfileSelector, times(1))
                .startProfileSelection(any(), eq(mUpdateAvailableNetworksCallback));

        // System app updated available networks which equal to null.
        // Case1: start carrier app request, if there is a carrier app request.
        availableNetworkInfos.clear();
        clearInvocations(mockProfileSelector);
        runUntilReady(
                () -> {
                    try {
                        iOpportunisticNetworkService.updateAvailableNetworks(
                                availableNetworkInfos,
                                mUpdateAvailableNetworksCallback,
                                pkgForDebug);
                    } catch (RemoteException ex) {
                        Log.e(TAG, "RemoteException", ex);
                    }
                });
        verify(mockProfileSelector, times(1)).startProfileSelection(any(), any());

        // System app updated available networks which equal to null.
        // Case2: stop profile selection, if there is no any carrier app request.
        doReturn(null).when(mockONSConfigInputHashMap).get(CARRIER_APP_CONFIG_NAME);
        mReady = false;
        try {
            iOpportunisticNetworkService.updateAvailableNetworks(
                    availableNetworkInfos, mUpdateAvailableNetworksCallback, pkgForDebug);
        } catch (RemoteException ex) {
            Log.e(TAG, "RemoteException", ex);
        }
        waitUntilReady();
        verify(mockProfileSelector, times(1)).stopProfileSelection(any());
    }

    @Test
    public void testCarrierConfigChangedUnlocked() {
        mOpportunisticNetworkService.mUserManager = mUserManager;
        mOpportunisticNetworkService.mONSProfileActivator = mONSProfileActivator;
        mOpportunisticNetworkService.mCarrierConfigManager = mCarrierConfigManager;
        mOpportunisticNetworkService.registerCarrierConfigChangListener();
        mOpportunisticNetworkService.mContext = mMockContext;

        CarrierConfigManager.CarrierConfigChangeListener CarrierConfigChangeListener;

        final ArgumentCaptor<CarrierConfigManager.CarrierConfigChangeListener> listenerCaptor =
                ArgumentCaptor.forClass(CarrierConfigManager.CarrierConfigChangeListener.class);

        verify(mCarrierConfigManager).registerCarrierConfigChangeListener(any(),
                listenerCaptor.capture());
        CarrierConfigChangeListener = listenerCaptor.getAllValues().get(0);

        // CarrierConfigChange in lock state
        when(mUserManager.isUserUnlocked()).thenReturn(false);
        CarrierConfigChangeListener.onCarrierConfigChanged(0, 0, 0, 0);
        verify(mONSProfileActivator, never()).handleCarrierConfigChange();

        // handle CarrierConfigChange when state is changed from lock to unlock
        final ArgumentCaptor<BroadcastReceiver> broadcastReceiverCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);

        verify(mMockContext).registerReceiver(broadcastReceiverCaptor.capture(), any());
        broadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(Intent.ACTION_USER_UNLOCKED));
        verify(mONSProfileActivator, times(1)).handleCarrierConfigChange();

        // CarrierConfigChange in Unlock state
        when(mUserManager.isUserUnlocked()).thenReturn(true);
        CarrierConfigChangeListener.onCarrierConfigChanged(0, 0, 0, 0);
        verify(mONSProfileActivator, times(2)).handleCarrierConfigChange();
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testTelephonyFeatureAndCompatChanges() throws Exception {
        ArrayList<String> mccMncs = new ArrayList<>();
        mccMncs.add("310210");
        AvailableNetworkInfo availableNetworkInfo = new AvailableNetworkInfo(1, 1, mccMncs,
                new ArrayList<Integer>());
        List<AvailableNetworkInfo> availableNetworkInfos = new ArrayList<>();
        availableNetworkInfos.add(availableNetworkInfo);

        // Enabled FeatureFlags and ENABLE_FEATURE_MAPPING, telephony features are defined
        try {
            // FEATURE_TELEPHONY_DATA
            iOpportunisticNetworkService.setPreferredDataSubscriptionId(
                    SubscriptionManager.DEFAULT_SUBSCRIPTION_ID, false, null, pkgForDebug);
            iOpportunisticNetworkService.getPreferredDataSubscriptionId(pkgForDebug, pkgForFeature);

            // FEATURE_TELEPHONY_RADIO_ACCESS
            iOpportunisticNetworkService.setEnable(false, pkgForDebug);
            iOpportunisticNetworkService.isEnabled(pkgForDebug);
            iOpportunisticNetworkService.updateAvailableNetworks(availableNetworkInfos, null,
                    pkgForDebug);
        } catch (Exception e) {
            fail("Not expect exception " + e.getMessage());
        }

        // Replace field to set vendor API level to the one where the exceptions are enabled.
        replaceInstance(OpportunisticNetworkService.class, "mVendorApiLevel",
                mOpportunisticNetworkService, TELEPHONY_FEATURE_ENFORCEMENT_VENDOR_API_LEVEL);

        // FEATURE_TELEPHONY_DATA is not defined, expect UnsupportedOperationException.
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_DATA))
                .thenReturn(false);

        assertThrows(UnsupportedOperationException.class,
                () -> iOpportunisticNetworkService.setPreferredDataSubscriptionId(
                        SubscriptionManager.DEFAULT_SUBSCRIPTION_ID, false, null, pkgForDebug));
        assertThrows(UnsupportedOperationException.class,
                () -> iOpportunisticNetworkService.getPreferredDataSubscriptionId(
                        pkgForDebug, pkgForFeature));

        // FEATURE_TELEPHONY_DATA is not defined, expect UnsupportedOperationException.
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS))
                .thenReturn(false);
        assertThrows(UnsupportedOperationException.class,
                () -> iOpportunisticNetworkService.setEnable(false, pkgForDebug));
        assertThrows(UnsupportedOperationException.class,
                () -> iOpportunisticNetworkService.isEnabled(pkgForDebug));
        assertThrows(UnsupportedOperationException.class,
                () -> iOpportunisticNetworkService.updateAvailableNetworks(
                        availableNetworkInfos, null, pkgForDebug));
    }

    @Test
    public void testUpdateAvailableNetworksForCarrierAppWithEmptyAvailableNetworksDisabled()
            throws Exception {
        doReturn(PackageManager.PERMISSION_DENIED).when(mMockContext)
                .checkCallingOrSelfPermission(android.Manifest.permission.MODIFY_PHONE_STATE);
        mOpportunisticNetworkService.mContext = mMockContext;
        List<AvailableNetworkInfo> availableNetworkInfos = new ArrayList<>();

        setAndAssertEnableState(false);
        runUntilReady(() -> {
            try {
                iOpportunisticNetworkService.updateAvailableNetworks(
                        availableNetworkInfos, mUpdateAvailableNetworksCallback, pkgForDebug);
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException", e);
            }
        });

        assertEquals(TelephonyManager.UPDATE_AVAILABLE_NETWORKS_SUCCESS, mResult);
    }

    // TODO(b/414649249): Enable test case when bug is fixed.
    @Test
    @Ignore
    public void testUpdateAvailableNetworksForCarrierAppWithEmptyAvailableNetworksEnabled()
            throws Exception {
        doReturn(PackageManager.PERMISSION_DENIED).when(mMockContext)
                .checkCallingOrSelfPermission(android.Manifest.permission.MODIFY_PHONE_STATE);
        mOpportunisticNetworkService.mContext = mMockContext;
        List<AvailableNetworkInfo> availableNetworkInfos = new ArrayList<>();

        setAndAssertEnableState(true);
        runUntilReady(() -> {
            try {
                iOpportunisticNetworkService.updateAvailableNetworks(
                        availableNetworkInfos, mUpdateAvailableNetworksCallback, pkgForDebug);
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException", e);
            }
        });

        // TODO(b/414649249): For reference until bug is fixed, this is the actual value returned.
        // assertEquals(
        //      TelephonyManager.UPDATE_AVAILABLE_NETWORKS_NO_OPPORTUNISTIC_SUB_AVAILABLE, mResult);
        assertEquals(TelephonyManager.UPDATE_AVAILABLE_NETWORKS_SUCCESS, mResult);
    }

    @Test
    public void testUpdateAvailableNetworksForCarrierAppWithTooManyNetworks() throws Exception {
        doReturn(PackageManager.PERMISSION_DENIED).when(mMockContext)
                .checkCallingOrSelfPermission(android.Manifest.permission.MODIFY_PHONE_STATE);
        mOpportunisticNetworkService.mContext = mMockContext;
        ArrayList<String> mccMncs = new ArrayList<>();
        mccMncs.add("310210");
        AvailableNetworkInfo availableNetworkInfo = new AvailableNetworkInfo(1, 1, mccMncs,
                new ArrayList<Integer>());
        List<AvailableNetworkInfo> availableNetworkInfos =
                new ArrayList<AvailableNetworkInfo>();
        availableNetworkInfos.add(availableNetworkInfo);
        availableNetworkInfos.add(availableNetworkInfo);

        setAndAssertEnableState(false);
        runUntilReady(() -> {
            try {
                iOpportunisticNetworkService.updateAvailableNetworks(
                        availableNetworkInfos, mUpdateAvailableNetworksCallback, pkgForDebug);
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException", e);
            }
        });

        assertEquals(
                TelephonyManager.UPDATE_AVAILABLE_NETWORKS_MULTIPLE_NETWORKS_NOT_SUPPORTED,
                mResult);
    }

    @Test
    public void testUpdateAvailableNetworksForCarrierAppWithoutOppSubs() throws Exception {
        doReturn(PackageManager.PERMISSION_DENIED).when(mMockContext)
                .checkCallingOrSelfPermission(android.Manifest.permission.MODIFY_PHONE_STATE);
        mOpportunisticNetworkService.mContext = mMockContext;
        ArrayList<String> mccMncs = new ArrayList<>();
        mccMncs.add("310210");
        AvailableNetworkInfo availableNetworkInfo = new AvailableNetworkInfo(1, 1, mccMncs,
                new ArrayList<Integer>());
        List<AvailableNetworkInfo> availableNetworkInfos =
                new ArrayList<AvailableNetworkInfo>();
        availableNetworkInfos.add(availableNetworkInfo);

        setAndAssertEnableState(false);
        runUntilReady(() -> {
            try {
                iOpportunisticNetworkService.updateAvailableNetworks(
                        availableNetworkInfos, mUpdateAvailableNetworksCallback, pkgForDebug);
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException", e);
            }
        });

        assertEquals(
                TelephonyManager.UPDATE_AVAILABLE_NETWORKS_NO_OPPORTUNISTIC_SUB_AVAILABLE, mResult);
    }

    @Test
    public void testUpdateAvailableNetworksForCarrierAppWithOppSubsNoActiveSubs() throws Exception {
        doReturn(PackageManager.PERMISSION_DENIED).when(mMockContext)
                .checkCallingOrSelfPermission(android.Manifest.permission.MODIFY_PHONE_STATE);
        doReturn(true).when(mockProfileSelector).hasOpportunisticSub(any());
        mOpportunisticNetworkService.mProfileSelector = mockProfileSelector;
        mOpportunisticNetworkService.mContext = mMockContext;
        ArrayList<String> mccMncs = new ArrayList<>();
        mccMncs.add("310210");
        AvailableNetworkInfo availableNetworkInfo = new AvailableNetworkInfo(1, 1, mccMncs,
                new ArrayList<Integer>());
        List<AvailableNetworkInfo> availableNetworkInfos =
                new ArrayList<AvailableNetworkInfo>();
        availableNetworkInfos.add(availableNetworkInfo);

        setAndAssertEnableState(false);
        runUntilReady(() -> {
            try {
                iOpportunisticNetworkService.updateAvailableNetworks(
                        availableNetworkInfos, mUpdateAvailableNetworksCallback, pkgForDebug);
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException", e);
            }
        });

        assertEquals(
                TelephonyManager.UPDATE_AVAILABLE_NETWORKS_NO_CARRIER_PRIVILEGE, mResult);
    }

    @Test
    public void testUpdateAvailableNetworksForCarrierAppWithOppSubsONSDisabled() throws Exception {
        doReturn(PackageManager.PERMISSION_DENIED).when(mMockContext)
                .checkCallingOrSelfPermission(android.Manifest.permission.MODIFY_PHONE_STATE);
        doReturn(true).when(mockProfileSelector).hasOpportunisticSub(any());
        doReturn(true).when(mSubscriptionManager).isActiveSubId(1);
        mOpportunisticNetworkService.mProfileSelector = mockProfileSelector;
        mOpportunisticNetworkService.mContext = mMockContext;
        ArrayList<String> mccMncs = new ArrayList<>();
        mccMncs.add("310210");
        AvailableNetworkInfo availableNetworkInfo = new AvailableNetworkInfo(1, 1, mccMncs,
                new ArrayList<Integer>());
        List<AvailableNetworkInfo> availableNetworkInfos =
                new ArrayList<AvailableNetworkInfo>();
        availableNetworkInfos.add(availableNetworkInfo);

        setAndAssertEnableState(false);
        runUntilReady(() -> {
            try {
                iOpportunisticNetworkService.updateAvailableNetworks(
                        availableNetworkInfos, mUpdateAvailableNetworksCallback, pkgForDebug);
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException", e);
            }
        });

        assertEquals(
                TelephonyManager.UPDATE_AVAILABLE_NETWORKS_SERVICE_IS_DISABLED, mResult);
    }

    @Test
    public void testUpdateAvailableNetworksForCarrierAppWithOppSubsONSEnabled() throws Exception {
        doReturn(PackageManager.PERMISSION_DENIED).when(mMockContext)
                .checkCallingOrSelfPermission(android.Manifest.permission.MODIFY_PHONE_STATE);
        doReturn(true).when(mockProfileSelector).hasOpportunisticSub(any());
        doReturn(true).when(mSubscriptionManager).isActiveSubId(1);
        mOpportunisticNetworkService.mProfileSelector = mockProfileSelector;
        mOpportunisticNetworkService.mContext = mMockContext;
        ArrayList<String> mccMncs = new ArrayList<>();
        mccMncs.add("310210");
        AvailableNetworkInfo availableNetworkInfo = new AvailableNetworkInfo(1, 1, mccMncs,
                new ArrayList<Integer>());
        List<AvailableNetworkInfo> availableNetworkInfos =
                new ArrayList<AvailableNetworkInfo>();
        availableNetworkInfos.add(availableNetworkInfo);

        setAndAssertEnableState(true);
        mReady = false;
        try {
            iOpportunisticNetworkService.updateAvailableNetworks(
                    availableNetworkInfos, mUpdateAvailableNetworksCallback, pkgForDebug);
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException", e);
        }
        waitUntilReady();
        verify(mockProfileSelector)
                .startProfileSelection(availableNetworkInfos, mUpdateAvailableNetworksCallback);
    }

    private void replaceInstance(final Class c, final String instanceName, final Object obj,
                                 final Object newValue) throws Exception {
        Field field = c.getDeclaredField(instanceName);
        field.setAccessible(true);
        field.set(obj, newValue);
    }

    private void setAndAssertEnableState(boolean enable) throws Exception {
        iOpportunisticNetworkService.setEnable(enable, pkgForDebug);
        assertEquals(enable, iOpportunisticNetworkService.isEnabled(pkgForDebug));
    }
}
