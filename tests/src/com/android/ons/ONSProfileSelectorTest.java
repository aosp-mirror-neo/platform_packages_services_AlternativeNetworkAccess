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
 * limitations under the License
 */
package com.android.ons;

import static android.telephony.AvailableNetworkInfo.PRIORITY_HIGH;
import static android.telephony.AvailableNetworkInfo.PRIORITY_MED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.ServiceManager;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.telephony.AvailableNetworkInfo;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityNr;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.UiccCardInfo;
import android.telephony.UiccPortInfo;
import android.telephony.euicc.EuiccManager;
import android.util.Log;

import androidx.test.runner.AndroidJUnit4;

import com.android.internal.telephony.ISetOpportunisticDataCallback;
import com.android.internal.telephony.ISub;
import com.android.internal.telephony.IUpdateAvailableNetworksCallback;
import com.android.internal.telephony.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@RunWith(AndroidJUnit4.class)
public class ONSProfileSelectorTest extends ONSBaseTest {

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private MyONSProfileSelector mONSProfileSelector;
    private int mResult;
    @Mock
    EuiccManager mMockEuiccManager;
    @Mock
    ONSNetworkScanCtlr mONSNetworkScanCtlr;
    @Mock
    TelephonyManager mSubscriptionBoundTelephonyManager;
    @Mock
    ISub mISubMock;
    @Mock
    IBinder mISubBinderMock;
    @Mock
    SubscriptionInfo mSubInfo;
    private static final String TAG = "ONSProfileSelectorTest";

    private final ONSProfileSelector.ONSProfileSelectionCallback mONSProfileSelectionCallback =
            new ONSProfileSelector.ONSProfileSelectionCallback() {
                public void onProfileSelectionDone() {
                    setReady(true);
                }
            };

    private final IUpdateAvailableNetworksCallback mUpdateAvailableNetworksCallback =
            new IUpdateAvailableNetworksCallback.Stub() {
                @Override
                public void onComplete(int result) {
                    Log.d(TAG, "mResult end:" + result);
                    mResult = result;
                    setReady(true);
                }
            };

    private final ISetOpportunisticDataCallback mSetOppDataCallback =
            new ISetOpportunisticDataCallback.Stub() {
                @Override
                public void onComplete(int result) {
                    Log.d(TAG, "mResult end:" + result);
                    mResult = result;
                    setReady(true);
                }
            };
    Answer<Void> mStartFastNetworkScanReceived = invocation -> {
        setReady(true);
        return null;
    };

    public class MyONSProfileSelector extends ONSProfileSelector {

        public SubscriptionManager.OnOpportunisticSubscriptionsChangedListener mProfileChngLstnrCpy;
        public BroadcastReceiver mProfileSelectorBroadcastReceiverCpy;
        public ONSNetworkScanCtlr.NetworkAvailableCallBack mNetworkAvailableCallBackCpy;

        public MyONSProfileSelector(Context c,
            MyONSProfileSelector.ONSProfileSelectionCallback aNSProfileSelectionCallback) {
            super(c, aNSProfileSelectionCallback);
        }

        public void triggerProfileUpdate() {
            mHandler.sendEmptyMessage(1);
        }

        public void updateOppSubs() {
            updateOpportunisticSubscriptions();
        }

        public int getCurrentPreferredData() {
            return mCurrentDataSubId;
        }

        public void setCurrentPreferredData(int subId) {
            mCurrentDataSubId = subId;
        }

        protected void init(Context c,
            MyONSProfileSelector.ONSProfileSelectionCallback aNSProfileSelectionCallback) {
            super.init(c, aNSProfileSelectionCallback);
            this.mSubscriptionManager = ONSProfileSelectorTest.this.mSubscriptionManager;
            this.mSubscriptionBoundTelephonyManager =
                ONSProfileSelectorTest.this.mSubscriptionBoundTelephonyManager;
            mProfileChngLstnrCpy = mProfileChangeListener;
            mProfileSelectorBroadcastReceiverCpy = null;
            mNetworkAvailableCallBackCpy = mNetworkAvailableCallBack;
            mNetworkScanCtlr = mONSNetworkScanCtlr;
        }
    }

    private void addISubService() throws Exception {
        Field field = ServiceManager.class.getDeclaredField("sCache");
        field.setAccessible(true);
        ((Map<String, IBinder>)field.get(null)).put("isub", mISubBinderMock);
        doReturn(mISubMock).when(mISubBinderMock).queryLocalInterface(any());
    }

    private void removeISubService() throws Exception {
        Field field = ServiceManager.class.getDeclaredField("sCache");
        field.setAccessible(true);
        ((Map<String, IBinder>)field.get(null)).remove("isub");
    }

    @Before
    public void setUp() throws Exception {
        super.setUp("ONSTest");
        MockitoAnnotations.initMocks(this);
        addISubService();
    }

    @After
    public void tearDown() throws Exception {
        removeISubService();
        super.tearDown();
    }

    // Set mock expectations before invoking.
    private void initONSProfileSelector() {
        runUntilReady(
                () -> {
                    new HandlerThread("ONSProfileSelectorTest") {
                        @Override
                        protected void onLooperPrepared() {
                            mONSProfileSelector =
                                    new MyONSProfileSelector(
                                            mContext, mONSProfileSelectionCallback);
                            setReady(true);
                        }
                    }.start();
                });
    }

    private void runUntilReady(Runnable task) {
        mResult = -1;
        setReady(false);
        task.run();
        waitUntilReady();
        assertTrue(mReady);
    }

    private void verifySetPreferredDataSubIdWithResult(int subId, boolean validation, int result) {
        ArgumentCaptor<Consumer<Integer>> callbackCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(mSubscriptionManager)
                .setPreferredDataSubscriptionId(
                        eq(subId), eq(validation), any(), callbackCaptor.capture());
        Consumer<Integer> callback = callbackCaptor.getAllValues().get(0);
        assertNotNull(callback);
        callback.accept(result);
    }

    @Test
    public void testStartProfileSelectionWithNoOpportunisticSub() {
        List<String> mccMncs = List.of("310210");
        ArrayList<AvailableNetworkInfo> availableNetworkInfos =
                new ArrayList<>(
                        List.of(
                                new AvailableNetworkInfo(
                                        1, PRIORITY_HIGH, mccMncs, new ArrayList<Integer>())));
        addUiccCardInfo(List.of(new UiccPortInfo("", 1, 1, false)));
        addOppSubscriptions(List.of());
        doReturn(true).when(mONSNetworkScanCtlr).startFastNetworkScan(any());
        initONSProfileSelector();

        runUntilReady(
                () -> {
                    mONSProfileSelector.mTelephonyManager = mMockTelephonyManager;
                    mONSProfileSelector.mEuiccManager = mMockEuiccManager;
                    mONSProfileSelector.updateOppSubs();
                    mONSProfileSelector.startProfileSelection(
                            availableNetworkInfos, mUpdateAvailableNetworksCallback);
                });

        assertEquals(
                TelephonyManager.UPDATE_AVAILABLE_NETWORKS_NO_OPPORTUNISTIC_SUB_AVAILABLE, mResult);
    }

    @Test
    public void testStartProfileSelectionSuccess() {
        int subId = 5;
        SubscriptionInfo subscriptionInfo1 =
                new SubscriptionInfo.Builder()
                        .setId(subId)
                        .setSimSlotIndex(1)
                        .setMcc("310")
                        .setMnc("210")
                        .setEmbedded(true)
                        .setOpportunistic(true)
                        .setCarrierId(1)
                        .setPortIndex(1)
                        .build();
        SubscriptionInfo subscriptionInfo2 =
                new SubscriptionInfo.Builder()
                        .setId(6)
                        .setSimSlotIndex(1)
                        .setMcc("310")
                        .setMnc("211")
                        .setEmbedded(true)
                        .setOpportunistic(false)
                        .setCarrierId(1)
                        .setPortIndex(0)
                        .build();
        addActiveSubscriptions(List.of(subscriptionInfo1, subscriptionInfo2));
        addOppSubscriptions(List.of(subscriptionInfo1));
        ArrayList<CellInfo> results = new ArrayList<>(List.of(createCellInfoLte(310, 210)));
        List<String> mccMncs = List.of("310210");
        ArrayList<AvailableNetworkInfo> availableNetworkInfos =
                new ArrayList<>(
                        List.of(
                                new AvailableNetworkInfo(
                                        subId, PRIORITY_HIGH, mccMncs, List.of())));
        doAnswer(mStartFastNetworkScanReceived)
                .when(mONSNetworkScanCtlr)
                .startFastNetworkScan(eq(availableNetworkInfos));
        initONSProfileSelector();

        runUntilReady(
                () -> {
                    mONSProfileSelector.updateOppSubs();
                    mONSProfileSelector.startProfileSelection(
                            availableNetworkInfos, mUpdateAvailableNetworksCallback);
                });
        runUntilReady(
                () -> {
                    mONSProfileSelector.mNetworkAvailableCallBackCpy.onNetworkAvailability(results);
                });

        assertEquals(TelephonyManager.UPDATE_AVAILABLE_NETWORKS_SUCCESS, mResult);
    }

    private CellInfo createCellInfoLte(int mcc, int mnc) {
        CellInfoLte cellInfo = new CellInfoLte();
        cellInfo.setCellIdentity(
                new CellIdentityLte(mcc, mnc, 1 /* ci */, 1 /* pci */, 1 /* tac */));
        return cellInfo;
    }

    private void addActiveSubscriptions(List<SubscriptionInfo> subInfos) {
        doReturn(subInfos).when(mSubscriptionManager).getActiveSubscriptionInfoList(anyBoolean());
        doReturn(subInfos).when(mSubscriptionManager).getCompleteActiveSubscriptionInfoList();
        subInfos.forEach(
                subInfo -> {
                    int subId = subInfo.getSubscriptionId();
                    int slotIndex = subInfo.getSimSlotIndex();
                    doReturn(true).when(mSubscriptionManager).isActiveSubId(subId);
                    doReturn(subInfo).when(mSubscriptionManager).getActiveSubscriptionInfo(subId);
                    doReturn(true)
                            .when(mSubscriptionBoundTelephonyManager)
                            .enableModemForSlot(eq(slotIndex), anyBoolean());
                });
    }

    private void addOppSubscriptions(List<SubscriptionInfo> oppSubList) {
        doReturn(oppSubList).when(mSubscriptionManager).getOpportunisticSubscriptions();
    }

    private void addUiccCardInfo(List<UiccPortInfo> portInfoList) {
        UiccCardInfo cardInfo = new UiccCardInfo(true, 1, "", 0, false, true, portInfoList);
        doReturn(List.of(cardInfo)).when(mMockTelephonyManager).getUiccCardsInfo();
        doReturn(mMockEuiccManager).when(mMockEuiccManager).createForCardId(1);

        portInfoList.forEach(
                portInfo ->
                        doReturn(true)
                                .when(mMockEuiccManager)
                                .isSimPortAvailable(portInfo.getPortIndex()));
    }

    @Test
    public void testStartProfileSelectionWithDifferentPrioritySubInfo() {
        SubscriptionInfo subscriptionInfo =
                new SubscriptionInfo.Builder()
                        .setId(5)
                        .setSimSlotIndex(1)
                        .setMcc("310")
                        .setMnc("210")
                        .setEmbedded(true)
                        .setOpportunistic(true)
                        .setCarrierId(1)
                        .setPortIndex(0)
                        .build();
        SubscriptionInfo subscriptionInfo_2 =
                new SubscriptionInfo.Builder()
                        .setId(8)
                        .setSimSlotIndex(1)
                        .setMcc("311")
                        .setMnc("480")
                        .setEmbedded(true)
                        .setOpportunistic(true)
                        .setCarrierId(1)
                        .setPortIndex(1)
                        .build();
        ArrayList<CellInfo> results =
                new ArrayList<>(List.of(createCellInfoLte(310, 210), createCellInfoLte(311, 480)));
        ArrayList<AvailableNetworkInfo> availableNetworkInfos =
                new ArrayList<>(
                        List.of(
                                new AvailableNetworkInfo(
                                        5, PRIORITY_MED, List.of("310210"), List.of()),
                                new AvailableNetworkInfo(
                                        8, PRIORITY_HIGH, List.of("311480"), List.of())));
        addActiveSubscriptions(List.of(subscriptionInfo, subscriptionInfo_2));
        addOppSubscriptions(List.of(subscriptionInfo, subscriptionInfo_2));
        doAnswer(mStartFastNetworkScanReceived)
                .when(mONSNetworkScanCtlr)
                .startFastNetworkScan(eq(availableNetworkInfos));
        initONSProfileSelector();

        runUntilReady(
                () -> {
                    mONSProfileSelector.updateOppSubs();
                    mONSProfileSelector.startProfileSelection(
                            availableNetworkInfos, mUpdateAvailableNetworksCallback);
                });
        int bestSubId = mONSProfileSelector.retrieveBestSubscription(results);
        assertEquals(8, bestSubId);
        runUntilReady(
                () -> {
                    mONSProfileSelector.mNetworkAvailableCallBackCpy.onNetworkAvailability(results);
                });

        assertEquals(TelephonyManager.UPDATE_AVAILABLE_NETWORKS_SUCCESS, mResult);
    }

    @Test
    public void testStartProfileSelectionWithActivePrimarySimOnESim() {
        ArrayList<AvailableNetworkInfo> availableNetworkInfos =
                new ArrayList<>(
                        List.of(
                                new AvailableNetworkInfo(
                                        5,
                                        PRIORITY_MED,
                                        List.of("310210"),
                                        new ArrayList<Integer>())));
        addActiveSubscriptions(
                List.of(
                        new SubscriptionInfo.Builder()
                                .setId(5)
                                .setSimSlotIndex(1)
                                .setMcc("310")
                                .setMnc("210")
                                .setEmbedded(true)
                                .setOpportunistic(false)
                                .setCarrierId(1839)
                                .setPortIndex(1)
                                .build(),
                        new SubscriptionInfo.Builder()
                                .setId(6)
                                .setSimSlotIndex(1)
                                .setMcc("310")
                                .setMnc("211")
                                .setEmbedded(true)
                                .setOpportunistic(true)
                                .setCarrierId(1839)
                                .setPortIndex(2)
                                .build()));
        addOppSubscriptions(List.of());
        addUiccCardInfo(
                List.of(new UiccPortInfo("1", 0, 0, false), new UiccPortInfo("2", 1, 1, true)));
        initONSProfileSelector();

        runUntilReady(
                () -> {
                    mONSProfileSelector.mTelephonyManager = mMockTelephonyManager;
                    mONSProfileSelector.mEuiccManager = mMockEuiccManager;
                    mONSProfileSelector.updateOppSubs();
                    mONSProfileSelector.startProfileSelection(
                            availableNetworkInfos, mUpdateAvailableNetworksCallback);
                });

        assertEquals(
                TelephonyManager.UPDATE_AVAILABLE_NETWORKS_NO_OPPORTUNISTIC_SUB_AVAILABLE, mResult);
    }

    @Test
    public void testSelectProfileForDataWithNoOpportunisticSub() {
        addOppSubscriptions(List.of());
        initONSProfileSelector();

        runUntilReady(
                () -> {
                    mONSProfileSelector.updateOppSubs();
                    mONSProfileSelector.selectProfileForData(1, false, mSetOppDataCallback);
                });

        assertEquals(
                TelephonyManager.SET_OPPORTUNISTIC_SUB_NO_OPPORTUNISTIC_SUB_AVAILABLE, mResult);
    }

    @Test
    public void testSelectProfileForDataWithInActiveSub() {
        addActiveSubscriptions(
                List.of(
                        new SubscriptionInfo.Builder()
                                .setId(5)
                                .setSimSlotIndex(1)
                                .setMcc("310")
                                .setMnc("210")
                                .build()));
        addOppSubscriptions(List.of());
        initONSProfileSelector();

        runUntilReady(
                () -> {
                    mONSProfileSelector.updateOppSubs();
                    mONSProfileSelector.selectProfileForData(5, false, mSetOppDataCallback);
                });

        assertEquals(
                TelephonyManager.SET_OPPORTUNISTIC_SUB_NO_OPPORTUNISTIC_SUB_AVAILABLE, mResult);
    }

    @Test
    public void testselectProfileForDataWithInvalidSubId() {
        SubscriptionInfo subscriptionInfo =
                new SubscriptionInfo.Builder()
                        .setId(5)
                        .setSimSlotIndex(1)
                        .setMcc("310")
                        .setMnc("210")
                        .build();
        addActiveSubscriptions(List.of(subscriptionInfo));
        addOppSubscriptions(List.of(subscriptionInfo));
        initONSProfileSelector();

        runUntilReady(
                () -> {
                    mONSProfileSelector.updateOppSubs();
                    mONSProfileSelector.selectProfileForData(
                            SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                            false,
                            mSetOppDataCallback);
                });

        assertEquals(
                TelephonyManager.SET_OPPORTUNISTIC_SUB_NO_OPPORTUNISTIC_SUB_AVAILABLE, mResult);
    }

    @Test
    public void testselectProfileForDataWithValidSub() {
        SubscriptionInfo subscriptionInfo =
                new SubscriptionInfo.Builder()
                        .setId(5)
                        .setSimSlotIndex(1)
                        .setMcc("310")
                        .setMnc("210")
                        .build();
        addActiveSubscriptions(List.of(subscriptionInfo));
        addOppSubscriptions(List.of(subscriptionInfo));
        initONSProfileSelector();
        int resultFromSubMan = 42;

        runUntilReady(
                () -> {
                    mONSProfileSelector.updateOppSubs();
                    mONSProfileSelector.selectProfileForData(5, false, mSetOppDataCallback);
                    verifySetPreferredDataSubIdWithResult(5, false, resultFromSubMan);
                });

        assertEquals(resultFromSubMan, mResult);
    }

    @Test
    public void testStartProfileSelectionSuccessWithSameArgumentsAgain() {
        SubscriptionInfo subscriptionInfo1 =
                new SubscriptionInfo.Builder()
                        .setId(5)
                        .setSimSlotIndex(1)
                        .setMcc("310")
                        .setMnc("210")
                        .setEmbedded(true)
                        .setOpportunistic(true)
                        .setCarrierId(1)
                        .setPortIndex(0)
                        .build();
        SubscriptionInfo subscriptionInfo2 =
                new SubscriptionInfo.Builder()
                        .setId(6)
                        .setSimSlotIndex(1)
                        .setMcc("310")
                        .setMnc("210")
                        .setEmbedded(true)
                        .setOpportunistic(false)
                        .setCarrierId(1)
                        .setPortIndex(0)
                        .build();
        addActiveSubscriptions(List.of(subscriptionInfo1, subscriptionInfo2));
        addOppSubscriptions(List.of(subscriptionInfo1));
        ArrayList<CellInfo> results = new ArrayList<>(List.of(createCellInfoLte(310, 210)));
        ArrayList<AvailableNetworkInfo> availableNetworkInfos =
                new ArrayList<>(
                        List.of(
                                new AvailableNetworkInfo(
                                        5, PRIORITY_HIGH, List.of("310210"), List.of())));
        doAnswer(mStartFastNetworkScanReceived)
                .when(mONSNetworkScanCtlr)
                .startFastNetworkScan(eq(availableNetworkInfos));
        initONSProfileSelector();

        runUntilReady(
                () -> {
                    mONSProfileSelector.updateOppSubs();
                    mONSProfileSelector.startProfileSelection(
                            availableNetworkInfos, mUpdateAvailableNetworksCallback);
                });
        runUntilReady(
                () -> {
                    mONSProfileSelector.mNetworkAvailableCallBackCpy.onNetworkAvailability(results);
                });

        assertEquals(TelephonyManager.UPDATE_AVAILABLE_NETWORKS_SUCCESS, mResult);

        runUntilReady(
                () -> {
                    mONSProfileSelector.updateOppSubs();
                    mONSProfileSelector.startProfileSelection(
                            availableNetworkInfos, mUpdateAvailableNetworksCallback);
                });
        runUntilReady(
                () -> {
                    mONSProfileSelector.mNetworkAvailableCallBackCpy.onNetworkAvailability(results);
                });

        assertEquals(TelephonyManager.UPDATE_AVAILABLE_NETWORKS_SUCCESS, mResult);
    }

    @Test
    public void testStopProfileSelectionWithPreferredDataOnSame() {
        SubscriptionInfo subscriptionInfo =
                new SubscriptionInfo.Builder()
                        .setId(5)
                        .setSimSlotIndex(1)
                        .setMcc("310")
                        .setMnc("210")
                        .setEmbedded(true)
                        .setOpportunistic(true)
                        .setCarrierId(0)
                        .build();
        addActiveSubscriptions(List.of(subscriptionInfo));
        addOppSubscriptions(List.of(subscriptionInfo));
        doReturn(5).when(mSubscriptionManager).getPreferredDataSubscriptionId();
        doReturn(true)
                .when(mSubscriptionBoundTelephonyManager)
                .enableModemForSlot(anyInt(), anyBoolean());
        initONSProfileSelector();

        runUntilReady(
                () -> {
                    mONSProfileSelector.updateOppSubs();
                    mONSProfileSelector.setCurrentPreferredData(5);
                    mONSProfileSelector.stopProfileSelection(mUpdateAvailableNetworksCallback);
                });

        assertEquals(
                SubscriptionManager.DEFAULT_SUBSCRIPTION_ID,
                mONSProfileSelector.getCurrentPreferredData());
    }

    @Test
    public void testStopProfileSelectionWithPreferredDataOnDifferent() {
        SubscriptionInfo subscriptionInfo =
                new SubscriptionInfo.Builder()
                        .setId(5)
                        .setSimSlotIndex(1)
                        .setMcc("310")
                        .setMnc("210")
                        .setEmbedded(true)
                        .setOpportunistic(true)
                        .setCarrierId(0)
                        .build();
        addActiveSubscriptions(List.of(subscriptionInfo));
        addOppSubscriptions(List.of(subscriptionInfo));
        doReturn(4).when(mSubscriptionManager).getPreferredDataSubscriptionId();
        initONSProfileSelector();

        mONSProfileSelector.updateOppSubs();
        mONSProfileSelector.setCurrentPreferredData(5);
        mONSProfileSelector.stopProfileSelection(null);

        assertEquals(5, mONSProfileSelector.getCurrentPreferredData());
    }

    /**
     * 2 - Primary active subscriptions and
     * 1 - Inactive opportunistic subscription
     */
    @Test
    public void testAvailablePortWhenTwoPrimarySIMsAreActive() {
        SubscriptionInfo oppSubInfo =
                new SubscriptionInfo.Builder()
                        .setId(4)
                        .setSimSlotIndex(-1)
                        .setMcc("110")
                        .setMnc("210")
                        .setEmbedded(true)
                        .setOpportunistic(true)
                        .setCarrierId(2839)
                        .setPortIndex(TelephonyManager.INVALID_PORT_INDEX)
                        .build();
        SubscriptionInfo primarySubInfo1 =
                new SubscriptionInfo.Builder()
                        .setId(5)
                        .setSimSlotIndex(0)
                        .setMcc("310")
                        .setMnc("210")
                        .setEmbedded(true)
                        .setOpportunistic(false)
                        .setCarrierId(1839)
                        .setPortIndex(0)
                        .build();
        SubscriptionInfo primarySubInfo2 =
                new SubscriptionInfo.Builder()
                        .setId(6)
                        .setSimSlotIndex(0)
                        .setMcc("310")
                        .setMnc("211")
                        .setEmbedded(true)
                        .setOpportunistic(false)
                        .setCarrierId(1839)
                        .setPortIndex(1)
                        .build();
        addActiveSubscriptions(List.of(primarySubInfo1, primarySubInfo2));
        addOppSubscriptions(List.of(oppSubInfo));
        addUiccCardInfo(
                List.of(new UiccPortInfo("", 0, 0, true), new UiccPortInfo("", 1, 0, true)));
        // Explicitly override the sim ports to be unavailable.
        doReturn(false).when(mMockEuiccManager).isSimPortAvailable(0);
        doReturn(false).when(mMockEuiccManager).isSimPortAvailable(1);
        initONSProfileSelector();

        mONSProfileSelector.mTelephonyManager = mMockTelephonyManager;
        mONSProfileSelector.mEuiccManager = mMockEuiccManager;
        int portIdx = mONSProfileSelector.getAvailableESIMPortIndex();

        assertEquals(TelephonyManager.INVALID_PORT_INDEX, portIdx);
    }

    /**
     * 1 - Primary active subscription and
     * 1 - Active opportunistic subscription
     */
    @Test
    public void testAvailablePortWhenOpportunisticEsimIsActive() {
        SubscriptionInfo oppSubInfo =
                new SubscriptionInfo.Builder()
                        .setId(5)
                        .setSimSlotIndex(1)
                        .setMcc("310")
                        .setMnc("210")
                        .setEmbedded(true)
                        .setOpportunistic(true)
                        .setCarrierId(1839)
                        .setPortIndex(0)
                        .build();
        SubscriptionInfo primarySubInfo =
                new SubscriptionInfo.Builder()
                        .setId(6)
                        .setSimSlotIndex(1)
                        .setMcc("310")
                        .setMnc("211")
                        .setEmbedded(true)
                        .setOpportunistic(false)
                        .setCarrierId(1839)
                        .setPortIndex(1)
                        .build();
        addActiveSubscriptions(List.of(oppSubInfo, primarySubInfo));
        addOppSubscriptions(List.of(oppSubInfo));
        initONSProfileSelector();

        int portIdx = mONSProfileSelector.getAvailableESIMPortIndex();

        assertEquals(0, portIdx);
    }

    /** 2 - Active opportunistic subscriptions. */
    @Test
    public void testAvailablePortWhenTwoOpportunisticEsimsAreActive() {
        SubscriptionInfo oppSubInfo1 =
                new SubscriptionInfo.Builder()
                        .setId(5)
                        .setSimSlotIndex(1)
                        .setMcc("310")
                        .setMnc("210")
                        .setEmbedded(true)
                        .setOpportunistic(true)
                        .setCarrierId(1839)
                        .setPortIndex(0)
                        .build();
        SubscriptionInfo oppSubInfo2 =
                new SubscriptionInfo.Builder()
                        .setId(6)
                        .setSimSlotIndex(1)
                        .setMcc("310")
                        .setMnc("211")
                        .setEmbedded(true)
                        .setOpportunistic(true)
                        .setCarrierId(1839)
                        .setPortIndex(1)
                        .build();
        addActiveSubscriptions(List.of(oppSubInfo1, oppSubInfo2));
        addOppSubscriptions(List.of(oppSubInfo1, oppSubInfo2));
        initONSProfileSelector();

        int portIdx = mONSProfileSelector.getAvailableESIMPortIndex();

        /* one of the opportunistic eSIM port should be selected */
        assertTrue(portIdx == 0 || portIdx == 1);
    }

    /**
     * 1 - Primary active subscription and
     * 1 - Active opportunistic subscription
     * 2 - Inactive opportunistic subscriptions
     */
    @Test
    public void testAvailablePortWhenOpportunisticEsimIsActiveAndInactiveSubscriptions() {
        SubscriptionInfo oppSubInfo1 =
                new SubscriptionInfo.Builder()
                        .setId(5)
                        .setSimSlotIndex(1)
                        .setMcc("310")
                        .setMnc("210")
                        .setEmbedded(true)
                        .setOpportunistic(true)
                        .setCarrierId(1839)
                        .setPortIndex(1)
                        .build();
        SubscriptionInfo oppSubInfo2 =
                new SubscriptionInfo.Builder()
                        .setId(7)
                        .setSimSlotIndex(1)
                        .setMcc("310")
                        .setMnc("210")
                        .setEmbedded(true)
                        .setOpportunistic(true)
                        .setCarrierId(1839)
                        .setPortIndex(TelephonyManager.INVALID_PORT_INDEX)
                        .build();
        SubscriptionInfo oppSubInfo3 =
                new SubscriptionInfo.Builder()
                        .setId(8)
                        .setSimSlotIndex(1)
                        .setMcc("310")
                        .setMnc("210")
                        .setEmbedded(true)
                        .setOpportunistic(true)
                        .setCarrierId(1839)
                        .setPortIndex(TelephonyManager.INVALID_PORT_INDEX)
                        .build();
        SubscriptionInfo primarySubInfo =
                new SubscriptionInfo.Builder()
                        .setId(6)
                        .setSimSlotIndex(1)
                        .setMcc("310")
                        .setMnc("211")
                        .setEmbedded(true)
                        .setOpportunistic(false)
                        .setCarrierId(1839)
                        .setPortIndex(0)
                        .build();
        addActiveSubscriptions(List.of(oppSubInfo1, primarySubInfo));
        addOppSubscriptions(List.of(oppSubInfo1, oppSubInfo2, oppSubInfo3));
        initONSProfileSelector();

        int portIdx = mONSProfileSelector.getAvailableESIMPortIndex();

        assertEquals(1, portIdx);
    }

    /**
     * 1 - Primary inactive subscription and
     * 1 - Inactive opportunistic subscription
     */
    @Test
    public void testAvailablePortWhenOnlyInactiveSubscriptions() {
        SubscriptionInfo oppSubInfo1 =
                new SubscriptionInfo.Builder()
                        .setId(5)
                        .setSimSlotIndex(1)
                        .setMcc("310")
                        .setMnc("210")
                        .setEmbedded(true)
                        .setOpportunistic(true)
                        .setCarrierId(1839)
                        .setPortIndex(TelephonyManager.INVALID_PORT_INDEX)
                        .build();
        SubscriptionInfo oppSubInfo2 =
                new SubscriptionInfo.Builder()
                        .setId(7)
                        .setSimSlotIndex(1)
                        .setMcc("310")
                        .setMnc("210")
                        .setEmbedded(true)
                        .setOpportunistic(true)
                        .setCarrierId(1839)
                        .setPortIndex(TelephonyManager.INVALID_PORT_INDEX)
                        .build();
        addActiveSubscriptions(List.of());
        addOppSubscriptions(List.of(oppSubInfo1, oppSubInfo2));
        addUiccCardInfo(
                List.of(new UiccPortInfo("", 0, 0, false), new UiccPortInfo("", 1, 0, false)));
        initONSProfileSelector();
        mONSProfileSelector.mTelephonyManager = mMockTelephonyManager;
        mONSProfileSelector.mEuiccManager = mMockEuiccManager;

        int portIdx = mONSProfileSelector.getAvailableESIMPortIndex();

        assertTrue(portIdx == 0 || portIdx == 1);
    }

    @Test
    public void testGetMncMccFromCellInfoNr() {
        initONSProfileSelector();

        CellIdentityNr cellIdentityNr =
                new CellIdentityNr(
                        0, 0, 0, new int[] {0}, "111", "222", 0, "", "", Collections.emptyList());

        CellInfoNr cellinfoNr = new CellInfoNr(0, true, 0, cellIdentityNr, null);

        assertEquals(mONSProfileSelector.getMcc(cellinfoNr), "111");
        assertEquals(mONSProfileSelector.getMnc(cellinfoNr), "222");
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_IS_PRIVATE_NETWORK_API)
    public void testStartProfileSelectionWithActiveOpportunisticNonEmbeddedSub() {
        SubscriptionInfo subscriptionInfo =
                new SubscriptionInfo.Builder()
                        .setId(5)
                        .setSimSlotIndex(1)
                        .setMcc("310")
                        .setMnc("210")
                        .setEmbedded(false)
                        .setOpportunistic(true)
                        .setCarrierId(1)
                        .setPortIndex(0)
                        .build();
        addActiveSubscriptions(List.of(subscriptionInfo));
        addOppSubscriptions(List.of(subscriptionInfo));
        addUiccCardInfo(List.of(new UiccPortInfo("1", 0, 0, false)));
        doReturn(false).when(mMockEuiccManager).isSimPortAvailable(0);

        List<String> mccMncs = List.of("310210");
        ArrayList<AvailableNetworkInfo> availableNetworkInfos =
                new ArrayList<>(
                        List.of(
                                new AvailableNetworkInfo(
                                        5, PRIORITY_HIGH, mccMncs, new ArrayList<Integer>())));

        doAnswer(mStartFastNetworkScanReceived)
                .when(mONSNetworkScanCtlr)
                .startFastNetworkScan(eq(availableNetworkInfos));

        initONSProfileSelector();

        runUntilReady(
                () -> {
                    mONSProfileSelector.mEuiccManager = mMockEuiccManager;
                    mONSProfileSelector.updateOppSubs();
                    mONSProfileSelector.startProfileSelection(
                            availableNetworkInfos, mUpdateAvailableNetworksCallback);
                });

        runUntilReady(
                () -> {
                    mONSProfileSelector.mNetworkAvailableCallBackCpy.onNetworkAvailability(
                            new ArrayList<>(List.of(createCellInfoLte(310, 210))));
                });

        assertEquals(TelephonyManager.UPDATE_AVAILABLE_NETWORKS_SUCCESS, mResult);
        verify(mONSNetworkScanCtlr).startFastNetworkScan(eq(availableNetworkInfos));
    }
}
