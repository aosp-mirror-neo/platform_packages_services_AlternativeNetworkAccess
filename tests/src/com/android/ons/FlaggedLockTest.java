/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static junit.framework.Assert.assertTrue;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public class FlaggedLockTest {
    @Mock ReentrantLock mLock;
    boolean mRan = false;
    Runnable mConfirmableRunnable = () -> {
        mRan = true;
    };
    Supplier<Boolean> mConfirmableSupplier = () -> {
        mConfirmableRunnable.run();
        return true;
    };

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testRunLockedWhenEnabledWithFlagEnabled() {
        FlaggedLock lock = new FlaggedLock(mLock, true);
        assertRuns(() -> lock.runLockedWhenEnabled(mConfirmableRunnable));
        assertLockedAndUnlocked();
    }

    @Test
    public void testRunLockedWhenEnabledWithFlagDisabled() {
        FlaggedLock lock = new FlaggedLock(mLock, false);
        assertRuns(() -> lock.runLockedWhenEnabled(mConfirmableRunnable));
        assertNoLocking();
    }

    @Test
    public void testRunLockedWhenEnabledWithValue() {
        FlaggedLock lock = new FlaggedLock(mLock, true);
        assertRuns(() -> assertTrue(lock.runLockedWhenEnabled(mConfirmableSupplier)));
        assertLockedAndUnlocked();
    }

    @Test
    public void testRunLockedWhenDisabledWithFlagEnabled() {
        FlaggedLock lock = new FlaggedLock(mLock, true);
        assertRuns(() -> lock.runLockedWhenDisabled(mConfirmableRunnable));
        assertNoLocking();
    }

    @Test
    public void testRunLockedWhenDisabledWithFlagDisabled() {
        FlaggedLock lock = new FlaggedLock(mLock, false);
        assertRuns(() -> lock.runLockedWhenDisabled(mConfirmableRunnable));
        assertLockedAndUnlocked();
    }

    @Test
    public void testRunLockedWhenDisabledWithValue() {
        FlaggedLock lock = new FlaggedLock(mLock, false);
        assertRuns(() -> assertTrue(lock.runLockedWhenDisabled(mConfirmableSupplier)));
        assertLockedAndUnlocked();
    }

    @Test
    public void testRunLockedWithFlagEnabled() {
        FlaggedLock lock = new FlaggedLock(mLock, true);
        assertRuns(() -> lock.runLocked(mConfirmableRunnable));
        assertLockedAndUnlocked();
    }

    @Test
    public void testRunLockedWithFlagDisabled() {
        FlaggedLock lock = new FlaggedLock(mLock, false);
        assertRuns(() -> lock.runLocked(mConfirmableRunnable));
        assertLockedAndUnlocked();
    }

    @Test
    public void testRunLockedWithValue() {
        FlaggedLock lock = new FlaggedLock(mLock, true);
        assertRuns(() -> assertTrue(lock.runLocked(mConfirmableSupplier)));
        assertLockedAndUnlocked();
    }

    private void assertRuns(Runnable task) {
        mRan = false;
        task.run();
        assertTrue(mRan);
    }

    private void assertNoLocking() {
        verifyNoInteractions(mLock);
    }

    private void assertLockedAndUnlocked() {
        InOrder inOrder = inOrder(mLock);
        inOrder.verify(mLock).lock();
        inOrder.verify(mLock).unlock();
        inOrder.verifyNoMoreInteractions();
    }
}
