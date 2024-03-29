/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.documentsui;

import static java.util.Objects.requireNonNull;

import android.app.Activity;
import android.app.UiAutomation;
import android.app.UiModeManager;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.test.ActivityInstrumentationTestCase2;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.test.uiautomator.Configurator;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObjectNotFoundException;

import com.android.documentsui.base.Features;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.UserId;
import com.android.documentsui.bots.Bots;
import com.android.documentsui.files.FilesActivity;

import java.io.IOException;

import javax.annotation.Nullable;

/**
 * Provides basic test environment for UI tests:
 * - Launches activity
 * - Creates and gives access to test root directories and test files
 * - Cleans up the test environment
 */
public abstract class ActivityTest<T extends Activity> extends ActivityInstrumentationTestCase2<T> {

    static final int TIMEOUT = 5000;
    static final int NIGHT_MODE_CHANGE_WAIT_TIME = 1000;

    // Testing files. For custom ones, override initTestFiles().
    public static final String dirName1 = "Dir1";
    public static final String childDir1 = "ChildDir1";
    public static final String fileName1 = "file1.log";
    public static final String fileName2 = "file12.png";
    public static final String fileName3 = "anotherFile0.log";
    public static final String fileName4 = "poodles.text";
    public static final String fileNameNoRename = "NO_RENAMEfile.txt";

    public Bots bots;
    public UiDevice device;
    public Context context;
    public UserId userId;
    public UiAutomation automation;

    public Features features;
    public RootInfo rootDir0;
    public RootInfo rootDir1;
    protected ContentResolver mResolver;
    protected DocumentsProviderHelper mDocsHelper;
    protected ContentProviderClient mClient;
    protected UiModeManager mUiModeManager;

    private String initialScreenOffTimeoutValue = null;
    private String initialSleepTimeoutValue = null;

    public ActivityTest(Class<T> activityClass) {
        super(activityClass);
    }

    /*
     * Returns the root that will be opened within the activity.
     * By default tests are started with one of the test roots.
     * Override the method if you want to open different root on start.
     * @return Root that will be opened. Return null if you want to open activity's default root.
     */
    protected @Nullable RootInfo getInitialRoot() {
        return rootDir0;
    }

    /**
     * Returns the authority of the testing provider begin used.
     * By default it's StubProvider's authority.
     * @return Authority of the provider.
     */
    protected String getTestingProviderAuthority() {
        return StubProvider.DEFAULT_AUTHORITY;
    }

    /**
     * Resolves testing roots.
     */
    protected void setupTestingRoots() throws RemoteException {
        rootDir0 = mDocsHelper.getRoot(StubProvider.ROOT_0_ID);
        rootDir1 = mDocsHelper.getRoot(StubProvider.ROOT_1_ID);
    }

    @Override
    public void setUp() throws Exception {
        device = UiDevice.getInstance(getInstrumentation());
        // NOTE: Must be the "target" context, else security checks in content provider will fail.
        context = getInstrumentation().getTargetContext();
        userId = UserId.DEFAULT_USER;
        automation = getInstrumentation().getUiAutomation();
        features = new Features.RuntimeFeatures(context.getResources(), null);

        bots = new Bots(device, automation, context, TIMEOUT);

        Configurator.getInstance().setToolType(MotionEvent.TOOL_TYPE_MOUSE);

        mResolver = context.getContentResolver();
        mDocsHelper = new DocumentsProviderHelper(userId, getTestingProviderAuthority(), context,
                getTestingProviderAuthority());

        device.setOrientationNatural();
        device.pressKeyCode(KeyEvent.KEYCODE_WAKEUP);
        device.pressKeyCode(KeyEvent.KEYCODE_MENU);

        disableScreenOffAndSleepTimeouts();

        setupTestingRoots();

        launchActivity();
        resetStorage();

        // Since at the launch of activity, ROOT_0 and ROOT_1 have no files, drawer will
        // automatically open for phone devices. Espresso register click() as (x, y) MotionEvents,
        // so if a drawer is on top of a file we want to select, it will actually click the drawer.
        // Thus to start a clean state, we always try to close first.
        bots.roots.closeDrawer();

        // Configure the provider back to default.
        mDocsHelper.configure(null, Bundle.EMPTY);
    }

    @Override
    public void tearDown() throws Exception {
        device.unfreezeRotation();
        mDocsHelper.cleanUp();
        restoreScreenOffAndSleepTimeouts();
        super.tearDown();
    }

    protected void launchActivity() {
        final Intent intent = new Intent(context, FilesActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        if (getInitialRoot() != null) {
            intent.setAction(Intent.ACTION_VIEW);
            intent.setDataAndType(getInitialRoot().getUri(), DocumentsContract.Root.MIME_TYPE_ITEM);
        }
        setActivityIntent(intent);
        getActivity();  // Launch the activity.
    }

    protected void resetStorage() throws RemoteException {
        mDocsHelper.clear(null, null);
        device.waitForIdle();
    }

    protected void initTestFiles() throws RemoteException {
        mDocsHelper.createFolder(rootDir0, dirName1);
        mDocsHelper.createDocument(rootDir0, "text/plain", fileName1);
        mDocsHelper.createDocument(rootDir0, "image/png", fileName2);
        mDocsHelper.createDocumentWithFlags(rootDir0.documentId, "text/plain", fileNameNoRename,
                Document.FLAG_SUPPORTS_WRITE);

        mDocsHelper.createDocument(rootDir1, "text/plain", fileName3);
        mDocsHelper.createDocument(rootDir1, "text/plain", fileName4);
    }

    void assertDefaultContentOfTestDir0() throws UiObjectNotFoundException {
        bots.directory.waitForDocument(fileName1);
        bots.directory.waitForDocument(fileName2);
        bots.directory.waitForDocument(dirName1);
        bots.directory.waitForDocument(fileNameNoRename);
        bots.directory.assertDocumentsCount(4);
    }

    void assertDefaultContentOfTestDir1() throws UiObjectNotFoundException {
        bots.directory.waitForDocument(fileName3);
        bots.directory.waitForDocument(fileName4);
        bots.directory.assertDocumentsCount(2);
    }

    /**
     * Setup test Activity UI Mode YES or not(AUTO/YES/NO) before start to testing
     * @param uiModeNight Constant for {@link #setNightMode(int)}
     *      0 - MODE_NIGHT_AUTO
     *      1 - MODE_NIGHT_NO
     *      2 - MODE_NIGHT_YES
     */
    protected void setSystemUiModeNight(int uiModeNight) {
        int systemUiMode = getActivity().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        if(uiModeNight != systemUiMode) {
            /* TODO since ag/4947691 enable config_lockDayNightMode to block app setNightMode()
               create b/115315612 to handle the UiModeManager permission deny problem */
            mUiModeManager = (UiModeManager) getActivity()
                    .getSystemService(Context.UI_MODE_SERVICE);
            mUiModeManager.setNightMode(uiModeNight);
            device.waitForIdle(NIGHT_MODE_CHANGE_WAIT_TIME);
        }
    }

    private void disableScreenOffAndSleepTimeouts() throws IOException {
        initialScreenOffTimeoutValue = device.executeShellCommand(
                "settings get system screen_off_timeout");
        initialSleepTimeoutValue = device.executeShellCommand(
                "settings get secure sleep_timeout");
        device.executeShellCommand("settings put system screen_off_timeout -1");
        device.executeShellCommand("settings put secure sleep_timeout -1");
    }

    private void restoreScreenOffAndSleepTimeouts() throws IOException {
        requireNonNull(initialScreenOffTimeoutValue);
        requireNonNull(initialSleepTimeoutValue);
        try {
            device.executeShellCommand(
                    "settings put system screen_off_timeout " + initialScreenOffTimeoutValue);
            device.executeShellCommand(
                    "settings put secure sleep_timeout " + initialSleepTimeoutValue);
        } finally {
            initialScreenOffTimeoutValue = null;
            initialSleepTimeoutValue = null;
        }
    }
}
