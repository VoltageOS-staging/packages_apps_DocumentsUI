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

package com.android.documentsui.bots;

import android.app.UiAutomation;
import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.MotionEvent.PointerCoords;
import android.view.MotionEvent.PointerProperties;

import androidx.test.uiautomator.Configurator;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;

/**
 * A test helper class that provides support for controlling directory list
 * and making assertions against the state of it.
 */
public class GestureBot extends Bots.BaseBot {
    private static final int LONGPRESS_STEPS = 60;
    private static final int TRAVELING_STEPS = 20;
    private static final int BAND_SELECTION_DEFAULT_STEPS = 100;
    private static final int STEPS_INBETWEEN_POINTS = 2;
    // Inserted after each motion event injection.
    private static final int MOTION_EVENT_INJECTION_DELAY_MILLIS = 5;
    private static final int LONG_PRESS_EVENT_INJECTION_DELAY_MILIS = 1000;
    private final String mDirContainerId;
    private final String mDirListId;
    private final UiAutomation mAutomation;
    private long mDownTime = 0;

    public GestureBot(UiDevice device, UiAutomation automation, Context context, int timeout) {
        super(device, context, timeout);
        mDirContainerId = mTargetPackage + ":id/container_directory";
        mDirListId = mTargetPackage + ":id/dir_list";
        mAutomation = automation;
    }

    public void gestureSelectFiles(String startLabel, String endLabel) throws Exception {
        int toolType = Configurator.getInstance().getToolType();
        Configurator.getInstance().setToolType(MotionEvent.TOOL_TYPE_FINGER);
        Rect startCoord = findDocument(startLabel).getBounds();
        Rect endCoord = findDocument(endLabel).getBounds();
        double diffX = endCoord.centerX() - startCoord.centerX();
        double diffY = endCoord.centerY() - startCoord.centerY();
        Point[] points = new Point[LONGPRESS_STEPS + TRAVELING_STEPS];

        // First simulate long-press by having a bunch of MOVE events in the same coordinate
        for (int i = 0; i < LONGPRESS_STEPS; i++) {
            points[i] = new Point(startCoord.centerX(), startCoord.centerY());
        }

        // Next put the actual drag/move events
        for (int i = 0; i < TRAVELING_STEPS; i++) {
            int newX = startCoord.centerX() + (int) (diffX / TRAVELING_STEPS * i);
            int newY = startCoord.centerY() + (int) (diffY / TRAVELING_STEPS * i);
            points[i + LONGPRESS_STEPS] = new Point(newX, newY);
        }
        mDevice.swipe(points, STEPS_INBETWEEN_POINTS);
        Configurator.getInstance().setToolType(toolType);
    }

    public void bandSelection(Point start, Point end) throws Exception {
        bandSelection(start, end, BAND_SELECTION_DEFAULT_STEPS);
    }

    public void fingerSelection(Point start, Point end) throws Exception {
        fingerSelection(start, end, BAND_SELECTION_DEFAULT_STEPS);
    }

    public void bandSelection(Point start, Point end, int steps) throws Exception {
        int toolType = Configurator.getInstance().getToolType();
        Configurator.getInstance().setToolType(MotionEvent.TOOL_TYPE_MOUSE);
        swipe(start.x, start.y, end.x, end.y, steps, MotionEvent.BUTTON_PRIMARY, false);
        Configurator.getInstance().setToolType(toolType);
    }

    private void fingerSelection(Point start, Point end, int steps) throws Exception {
        int toolType = Configurator.getInstance().getToolType();
        Configurator.getInstance().setToolType(MotionEvent.TOOL_TYPE_FINGER);
        swipe(start.x, start.y, end.x, end.y, steps, MotionEvent.BUTTON_PRIMARY, true);
        Configurator.getInstance().setToolType(toolType);
    }

    public UiObject findDocument(String label) throws UiObjectNotFoundException {
        final UiSelector docList = new UiSelector().resourceId(
                mDirContainerId).childSelector(
                new UiSelector().resourceId(mDirListId));

        // Wait for the first list item to appear
        new UiObject(docList.childSelector(new UiSelector())).waitForExists(mTimeout);

        return mDevice.findObject(docList.childSelector(new UiSelector().text(label)));
    }

    private void swipe(int downX, int downY, int upX, int upY, int steps, int button,
            boolean fingerSelection) {
        int swipeSteps = steps;
        double xStep = 0;
        double yStep = 0;

        // avoid a divide by zero
        if (swipeSteps == 0) {
            swipeSteps = 1;
        }

        xStep = ((double) (upX - downX)) / swipeSteps;
        yStep = ((double) (upY - downY)) / swipeSteps;

        // first touch starts exactly at the point requested
        touchDown(downX, downY, button);
        if (fingerSelection) {
            SystemClock.sleep(LONG_PRESS_EVENT_INJECTION_DELAY_MILIS);
        }
        for (int i = 1; i < swipeSteps; i++) {
            touchMove(downX + (int) (xStep * i), downY + (int) (yStep * i), button);
            // set some known constant delay between steps as without it this
            // become completely dependent on the speed of the system and results
            // may vary on different devices. This guarantees at minimum we have
            // a preset delay.
            SystemClock.sleep(MOTION_EVENT_INJECTION_DELAY_MILLIS);
        }
        touchUp(upX, upY);
    }

    private boolean touchDown(int x, int y, int button) {
        long mDownTime = SystemClock.uptimeMillis();
        MotionEvent event = getMotionEvent(mDownTime, mDownTime, MotionEvent.ACTION_DOWN, button, x,
                y);
        return mAutomation.injectInputEvent(event, true);
    }

    private boolean touchUp(int x, int y) {
        final long eventTime = SystemClock.uptimeMillis();
        MotionEvent event = getMotionEvent(mDownTime, eventTime, MotionEvent.ACTION_UP, 0, x, y);
        mDownTime = 0;
        return mAutomation.injectInputEvent(event, true);
    }

    private boolean touchMove(int x, int y, int button) {
        final long eventTime = SystemClock.uptimeMillis();
        MotionEvent event = getMotionEvent(mDownTime, eventTime, MotionEvent.ACTION_MOVE, button, x,
                y);
        return mAutomation.injectInputEvent(event, true);
    }

    /** Helper function to obtain a MotionEvent. */
    private static MotionEvent getMotionEvent(long downTime, long eventTime, int action, int button,
            float x, float y) {

        PointerProperties properties = new PointerProperties();
        properties.id = 0;
        properties.toolType = Configurator.getInstance().getToolType();

        PointerCoords coords = new PointerCoords();
        coords.pressure = 1;
        coords.size = 1;
        coords.x = x;
        coords.y = y;

        return MotionEvent.obtain(downTime, eventTime, action, 1,
                new PointerProperties[]{properties}, new PointerCoords[]{coords},
                0, button, 1.0f, 1.0f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
    }
}
