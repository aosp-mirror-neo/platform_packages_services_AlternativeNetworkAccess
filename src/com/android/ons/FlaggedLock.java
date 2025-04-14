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

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * A flagging and locking utility for simplifying working with
 * conditional locks. Given a lock and a flag value, a FlaggedLock
 * will handle determining whether the lock should be acquired given
 * context from the caller.
 */
public class FlaggedLock {
    private final ReentrantLock mLock;
    private final boolean mFlagValue;

    public FlaggedLock(ReentrantLock lock, boolean flagValue) {
        mLock = lock;
        mFlagValue = flagValue;
    }

    private void maybeLock(boolean withFlag) {
        if (mFlagValue == withFlag) {
            mLock.lock();
        }
    }

    private void maybeUnlock(boolean withFlag) {
        if (mFlagValue == withFlag) {
            mLock.unlock();
        }
    }

    /**
     * If the flag is enabled, run with the lock acquired. Otherwise, run without the lock.
     */
    public void runLockedWhenEnabled(Runnable task) {
        runLockedWhenEnabled(() -> {
            task.run();
            return null;
        });
    }

    /**
     * If the flag is enabled, run with the lock acquired. Otherwise, run without the lock.
     */
    public <T> T runLockedWhenEnabled(Supplier<T> task) {
        return runWithConditionalLock(task, true /* withFlag */);
    }

    /**
     * If the flag is disabled, run with the lock acquired. Otherwise, run without the lock.
     */
    public void runLockedWhenDisabled(Runnable task) {
        runLockedWhenDisabled(() -> {
            task.run();
            return null;
        });
    }

    /**
     * If the flag is disabled, run with the lock acquired. Otherwise, run without the lock.
     */
    public <T> T runLockedWhenDisabled(Supplier<T> task) {
        return runWithConditionalLock(task, false /* withFlag */);
    }

    private <T> T runWithConditionalLock(Supplier<T> task, boolean withFlag) {
        maybeLock(withFlag);
        try {
            return task.get();
        } finally {
            maybeUnlock(withFlag);
        }
    }

    /**
     * Run with the lock acquired regardless of flag value.
     */
    public void runLocked(Runnable task) {
        runLocked(() -> {
            task.run();
            return null;
        });
    }

    /**
     * Run with the lock acquired regardless of flag value.
     */
    public <T> T runLocked(Supplier<T> task) {
        mLock.lock();
        try {
            return task.get();
        } finally {
            mLock.unlock();
        }
    }
}
