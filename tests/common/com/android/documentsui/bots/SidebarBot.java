/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.swipeLeft;
import static androidx.test.espresso.action.ViewActions.swipeRight;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import android.content.Context;
import android.util.Log;
import android.view.View;

import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;

import com.android.documentsui.R;

import junit.framework.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A test helper class that provides support for controlling and asserting against
 * the roots list drawer.
 */
public class SidebarBot extends Bots.BaseBot {
    private static final String TAG = "RootsListBot";

    private final String mRootListId;

    public SidebarBot(UiDevice device, Context context, int timeout) {
        super(device, context, timeout);
        mRootListId = mTargetPackage + ":id/roots_list";
    }

    private UiObject findRoot(String label) throws UiObjectNotFoundException {
        // We might need to expand drawer if not visible
        openDrawer();

        final UiSelector rootsList = new UiSelector().resourceId(
                mTargetPackage + ":id/container_roots").childSelector(
                new UiSelector().resourceId(mRootListId));

        // Wait for the first list item to appear
        new UiObject(rootsList.childSelector(new UiSelector())).waitForExists(mTimeout);

        // Now scroll around to find our item
        new UiScrollable(rootsList).scrollIntoView(new UiSelector().text(label));
        return new UiObject(rootsList.childSelector(new UiSelector().text(label)));
    }

    public void openRoot(String label) throws UiObjectNotFoundException {
        findRoot(label).click();
        // Close the drawer in case we select a pre-selected root already
        closeDrawer();
    }

    public void openDrawer() throws UiObjectNotFoundException {
        final UiSelector rootsList = new UiSelector().resourceId(
                mTargetPackage + ":id/container_roots").childSelector(
                new UiSelector().resourceId(mRootListId));

        // We might need to expand drawer if not visible
        if (!new UiObject(rootsList).waitForExists(mTimeout)) {
            Log.d(TAG, "Failed to find roots list; trying to expand");
            final UiSelector hamburger = new UiSelector().resourceId(
                    mTargetPackage + ":id/toolbar").childSelector(
                    new UiSelector().className("android.widget.ImageButton").clickable(true));
            new UiObject(hamburger).click();
        }
    }

    public void closeDrawer() {
      // Espresso will try to close the drawer if it's opened
      // But if no drawer exists (Tablet devices), we will have to catch the exception
      // and continue on the test
      // Why can't we do something like .exist() first?
      // http://stackoverflow.com/questions/20807131/espresso-return-boolean-if-view-exists
      try {
        if (mContext.getResources().getConfiguration()
            .getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
            onView(withId(R.id.drawer_layout)).perform(swipeRight());
        } else {
          onView(withId(R.id.drawer_layout)).perform(swipeLeft());
        }
      } catch (Exception e) {
      }
    }

    public void assertRootsPresent(String... labels) throws UiObjectNotFoundException {
        List<String> missing = new ArrayList<>();
        for (String label : labels) {
            if (!findRoot(label).exists()) {
                missing.add(label);
            }
        }
        if (!missing.isEmpty()) {
            Assert.fail(
                    "Expected roots " + Arrays.asList(labels) + ", but missing " + missing);
        }
    }

    public void assertRootsAbsent(String... labels) throws UiObjectNotFoundException {
        List<String> unexpected = new ArrayList<>();
        for (String label : labels) {
            if (findRoot(label).exists()) {
                unexpected.add(label);
            }
        }
        if (!unexpected.isEmpty()) {
            Assert.fail("Unexpected roots " + unexpected);
        }
    }

    public void assertHasFocus() {
        assertHasFocus(mRootListId);
    }
}
