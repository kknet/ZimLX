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

package org.zimmob.zimlx;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.graphics.Point;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Xml;
import android.view.Display;
import android.view.WindowManager;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.zimmob.zimlx.config.FeatureFlags;
import org.zimmob.zimlx.config.ProviderConfig;
import org.zimmob.zimlx.preferences.IPreferenceProvider;
import org.zimmob.zimlx.preferences.PreferenceFlags;
import org.zimmob.zimlx.util.Thunk;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

public class InvariantDeviceProfile {

    private static final float ICON_SIZE_DEFINED_IN_APP_DP = 48;

    // Constants that affects the interpolation curve between statically defined device profile
    // buckets.
    private static float KNEARESTNEIGHBOR = 3;
    private static float WEIGHT_POWER = 5;

    // used to offset float not being able to express extremely small weights in extreme cases.
    private static float WEIGHT_EFFICIENT = 100000f;

    public int numRowsOriginal;
    public int numColumnsOriginal;
    public int numColumnsDrawer;

    /**
     * Number of icons per row and column in the workspace.
     */
    public int numRows;
    public int numRowsDrawer;
    public int numColumns;
    public float allAppsIconSize;
    public float iconSizeOriginal;
    public float allAppsIconTextSize;

    /**
     * Number of icons per row and column in the folder.
     */
    public int numFolderRows;
    public int numFolderColumns;
    public float iconSize;
    public int searchHeightAddition;
    public int numHotseatIconsOriginal;
    public int iconBitmapSize;
    public int fillResIconDpi;
    public float iconTextSize;
    // Profile-defining invariant properties
    String name;
    float minWidthDps;

    /**
     * Number of icons inside the hotseat area.
     */
    public int numHotseatIcons;
    float minHeightDps;
    float hotseatIconSize;
    float hotseatIconSizeOriginal;
    int defaultLayoutId;

    DeviceProfile landscapeProfile;
    DeviceProfile portraitProfile;

    public Point defaultWallpaperSize;

    public InvariantDeviceProfile() {
    }

    public InvariantDeviceProfile(InvariantDeviceProfile p) {
        this(p.name, p.minWidthDps, p.minHeightDps, p.numRows, p.numColumns, p.numColumnsDrawer,
                p.numFolderRows, p.numFolderColumns,
                p.iconSize, p.iconTextSize, p.numHotseatIcons, p.hotseatIconSize,
                p.defaultLayoutId);
    }

    InvariantDeviceProfile(String n, float w, float h, int r, int c, int cd, int fr, int fc,
                           float is, float its, int hs, float his, int dlId) {
        name = n;
        minWidthDps = w;
        minHeightDps = h;
        numRows = r;
        numColumns = c;
        numColumnsDrawer = cd;
        numFolderRows = fr;
        numFolderColumns = fc;
        iconSize = is;
        iconTextSize = its;
        numHotseatIcons = hs;
        hotseatIconSize = his;
        defaultLayoutId = dlId;
    }

    @TargetApi(Build.VERSION_CODES.M)
    InvariantDeviceProfile(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        DisplayMetrics dm = new DisplayMetrics();
        display.getMetrics(dm);

        Point smallestSize = new Point();
        Point largestSize = new Point();
        display.getCurrentSizeRange(smallestSize, largestSize);

        // This guarantees that width < height
        minWidthDps = Utilities.dpiFromPx(Math.min(smallestSize.x, smallestSize.y), dm);
        minHeightDps = Utilities.dpiFromPx(Math.min(largestSize.x, largestSize.y), dm);

        ArrayList<InvariantDeviceProfile> closestProfiles = findClosestDeviceProfiles(
                minWidthDps, minHeightDps, getPredefinedDeviceProfiles(context));
        InvariantDeviceProfile interpolatedDeviceProfileOut =
                invDistWeightedInterpolate(context, minWidthDps, minHeightDps, closestProfiles);

        InvariantDeviceProfile closestProfile = closestProfiles.get(0);
        numRows = closestProfile.numRows;
        numRowsOriginal = numRows;
        numColumns = closestProfile.numColumns;
        numColumnsOriginal = numColumns;
        numColumnsDrawer = numColumns;
        numRowsDrawer = numRows;
        numHotseatIcons = closestProfile.numHotseatIcons;
        numHotseatIconsOriginal = numHotseatIcons;
        defaultLayoutId = closestProfile.defaultLayoutId;
        numFolderRows = closestProfile.numFolderRows;
        numFolderColumns = closestProfile.numFolderColumns;

        iconSize = interpolatedDeviceProfileOut.iconSize;
        iconSizeOriginal = iconSize;
        allAppsIconSize = iconSize;
        iconBitmapSize = Utilities.pxFromDp(iconSize, dm);
        searchHeightAddition = iconBitmapSize;
        iconTextSize = interpolatedDeviceProfileOut.iconTextSize;
        allAppsIconTextSize = iconTextSize;
        hotseatIconSize = interpolatedDeviceProfileOut.hotseatIconSize;
        hotseatIconSizeOriginal = hotseatIconSize;
        fillResIconDpi = getLauncherIconDensity(iconBitmapSize);

        customizationHook(context);

        Point realSize = new Point();
        display.getRealSize(realSize);
        // The real size never changes. smallSide and largeSide will remain the
        // same in any orientation.
        int smallSide = Math.min(realSize.x, realSize.y);
        int largeSide = Math.max(realSize.x, realSize.y);

        landscapeProfile = new DeviceProfile(context, this, smallestSize, largestSize,
                largeSide, smallSide, true /* isLandscape */);
        portraitProfile = new DeviceProfile(context, this, smallestSize, largestSize,
                smallSide, largeSide, false /* isLandscape */);

        // We need to ensure that there is enough extra space in the wallpaper
        // for the intended parallax effects
        if (context.getResources().getConfiguration().smallestScreenWidthDp >= 720) {
            defaultWallpaperSize = new Point(
                    (int) (largeSide * wallpaperTravelToScreenWidthRatio(largeSide, smallSide)),
                    largeSide);
        } else {
            defaultWallpaperSize = new Point(Math.max(smallSide * 2, largeSide), largeSide);
        }
    }

    /**
     * As a ratio of screen height, the total distance we want the parallax effect to span
     * horizontally
     */
    private static float wallpaperTravelToScreenWidthRatio(int width, int height) {
        float aspectRatio = width / (float) height;

        // At an aspect ratio of 16/10, the wallpaper parallax effect should span 1.5 * screen width
        // At an aspect ratio of 10/16, the wallpaper parallax effect should span 1.2 * screen width
        // We will use these two data points to extrapolate how much the wallpaper parallax effect
        // to span (ie travel) at any aspect ratio:

        final float ASPECT_RATIO_LANDSCAPE = 16 / 10f;
        final float ASPECT_RATIO_PORTRAIT = 10 / 16f;
        final float WALLPAPER_WIDTH_TO_SCREEN_RATIO_LANDSCAPE = 1.5f;
        final float WALLPAPER_WIDTH_TO_SCREEN_RATIO_PORTRAIT = 1.2f;

        // To find out the desired width at different aspect ratios, we use the following two
        // formulas, where the coefficient on x is the aspect ratio (width/height):
        //   (16/10)x + y = 1.5
        //   (10/16)x + y = 1.2
        // We solve for x and y and end up with a final formula:
        final float x =
                (WALLPAPER_WIDTH_TO_SCREEN_RATIO_LANDSCAPE - WALLPAPER_WIDTH_TO_SCREEN_RATIO_PORTRAIT) /
                        (ASPECT_RATIO_LANDSCAPE - ASPECT_RATIO_PORTRAIT);
        final float y = WALLPAPER_WIDTH_TO_SCREEN_RATIO_PORTRAIT - x * ASPECT_RATIO_PORTRAIT;
        return x * aspectRatio + y;
    }

    public void refresh(Context context) {
        landscapeProfile.refresh();
        portraitProfile.refresh();
        customizationHook(context);
    }

    private void customizationHook(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        DisplayMetrics dm = new DisplayMetrics();
        display.getMetrics(dm);
        IPreferenceProvider prefs = Utilities.getPrefs(context);
        String valueDefault = PreferenceFlags.PREF_DEFAULT_STRING;
        if (!prefs.numRows(valueDefault).equals(valueDefault)) {
            numRows = Integer.valueOf(prefs.numRows(""));
        } else {
            numRows = numRowsOriginal;
        }
        if (!prefs.numCols(valueDefault).equals(valueDefault)) {
            numColumns = Integer.valueOf(prefs.numCols(""));
        } else {
            numColumns = numColumnsOriginal;
        }
        if (!prefs.numColsDrawer(valueDefault).equals(valueDefault)) {
            numColumnsDrawer = Integer.valueOf(prefs.numColsDrawer(""));
        } else {
            numColumnsDrawer = numColumnsOriginal;
        }
        if (!prefs.numRowsDrawer(valueDefault).equals(valueDefault)) {
            numRowsDrawer = Integer.valueOf(prefs.numRowsDrawer(""));
        } else {
            numRowsDrawer = numRowsOriginal;
        }
        if (!prefs.numHotseatIcons(valueDefault).equals(valueDefault)) {
            numHotseatIcons = Integer.valueOf(prefs.numHotseatIcons(""));
        } else {
            numHotseatIcons = numHotseatIconsOriginal;
        }
        if (prefs.getIconScaleSB() != 1f) {
            float iconScale = prefs.getIconScaleSB();
            iconSize *= iconScale;
        }
        if (prefs.getHotseatIconScale() != 1f) {
            float iconScale = prefs.getHotseatIconScale();
            hotseatIconSize *= iconScale;
        }
        if (prefs.getAllAppsIconScale() != 1f) {
            float iconScale = prefs.getAllAppsIconScale();
            allAppsIconSize *= iconScale;
        }
        float maxSize = Math.max(Math.max(iconSize, allAppsIconSize), hotseatIconSize);
        iconBitmapSize = Math.max(1, Utilities.pxFromDp(maxSize, dm));
        fillResIconDpi = getLauncherIconDensity(iconBitmapSize);
        if (prefs.getIconTextScaleSB() != 1f) {
            iconTextSize *= prefs.getIconTextScaleSB();
        }
        if (prefs.getAllAppsIconTextScale() != 1f) {
            allAppsIconTextSize *= prefs.getAllAppsIconTextScale();
        }
    }

    private int getLauncherIconDensity(int requiredSize) {
        // Densities typically defined by an app.
        int[] densityBuckets = new int[]{
                DisplayMetrics.DENSITY_LOW,
                DisplayMetrics.DENSITY_MEDIUM,
                DisplayMetrics.DENSITY_TV,
                DisplayMetrics.DENSITY_HIGH,
                DisplayMetrics.DENSITY_XHIGH,
                DisplayMetrics.DENSITY_XXHIGH,
                DisplayMetrics.DENSITY_XXXHIGH
        };

        int density = DisplayMetrics.DENSITY_XXXHIGH;
        for (int i = densityBuckets.length - 1; i >= 0; i--) {
            float expectedSize = ICON_SIZE_DEFINED_IN_APP_DP * densityBuckets[i]
                    / DisplayMetrics.DENSITY_DEFAULT;
            if (expectedSize >= requiredSize) {
                density = densityBuckets[i];
            }
        }

        return density;
    }

    @Thunk
    float dist(float x0, float y0, float x1, float y1) {
        return (float) Math.hypot(x1 - x0, y1 - y0);
    }

    ArrayList<InvariantDeviceProfile> getPredefinedDeviceProfiles(Context context) {
        ArrayList<InvariantDeviceProfile> profiles = new ArrayList<>();
        try (XmlResourceParser parser = context.getResources().getXml(R.xml.device_profiles)) {
            final int depth = parser.getDepth();
            int type;

            while (((type = parser.next()) != XmlPullParser.END_TAG ||
                    parser.getDepth() > depth) && type != XmlPullParser.END_DOCUMENT) {
                if ((type == XmlPullParser.START_TAG) && "profile".equals(parser.getName())) {
                    TypedArray a = context.obtainStyledAttributes(
                            Xml.asAttributeSet(parser), R.styleable.InvariantDeviceProfile);
                    int numRows = a.getInt(R.styleable.InvariantDeviceProfile_numRows, 0);
                    int numColumns = a.getInt(R.styleable.InvariantDeviceProfile_numColumns, 0);
                    float iconSize = a.getFloat(R.styleable.InvariantDeviceProfile_iconSize, 0);
                    profiles.add(new InvariantDeviceProfile(
                            a.getString(R.styleable.InvariantDeviceProfile_name),
                            a.getFloat(R.styleable.InvariantDeviceProfile_minWidthDps, 0),
                            a.getFloat(R.styleable.InvariantDeviceProfile_minHeightDps, 0),
                            numRows,
                            numColumns,
                            numColumns,
                            a.getInt(R.styleable.InvariantDeviceProfile_numFolderRows, numRows),
                            a.getInt(R.styleable.InvariantDeviceProfile_numFolderColumns, numColumns),
                            iconSize,
                            a.getFloat(R.styleable.InvariantDeviceProfile_iconTextSize, 0),
                            a.getInt(R.styleable.InvariantDeviceProfile_numHotseatIcons, numColumns),
                            a.getFloat(R.styleable.InvariantDeviceProfile_hotseatIconSize, iconSize),
                            a.getResourceId(R.styleable.InvariantDeviceProfile_defaultLayoutId, 0)));
                    a.recycle();
                }
            }
        } catch (IOException | XmlPullParserException e) {
            throw new RuntimeException(e);
        }
        return profiles;
    }

    public boolean isAllAppsButtonRank(int rank) {
        return rank == getAllAppsButtonRank();
    }

    /**
     * Returns the closest device profiles ordered by closeness to the specified width and height
     */
    // Package private visibility for testing.
    ArrayList<InvariantDeviceProfile> findClosestDeviceProfiles(
            final float width, final float height, ArrayList<InvariantDeviceProfile> points) {

        // Sort the profiles by their closeness to the dimensions
        ArrayList<InvariantDeviceProfile> pointsByNearness = points;
        Collections.sort(pointsByNearness, (a, b) -> Float.compare(dist(width, height, a.minWidthDps, a.minHeightDps),
                dist(width, height, b.minWidthDps, b.minHeightDps)));

        return pointsByNearness;
    }

    // Package private visibility for testing.
    InvariantDeviceProfile invDistWeightedInterpolate(Context context, float width, float height,
                                                      ArrayList<InvariantDeviceProfile> points) {
        float weights = 0;

        InvariantDeviceProfile p = points.get(0);
        if (dist(width, height, p.minWidthDps, p.minHeightDps) == 0) {
            return p;
        }

        InvariantDeviceProfile out = new InvariantDeviceProfile();
        for (int i = 0; i < points.size() && i < KNEARESTNEIGHBOR; ++i) {
            p = new InvariantDeviceProfile(points.get(i));
            float w = weight(width, height, p.minWidthDps, p.minHeightDps, WEIGHT_POWER);
            weights += w;
            out.add(p.multiply(w));
        }
        return out.multiply(1.0f / weights);
    }

    private void add(InvariantDeviceProfile p) {
        iconSize += p.iconSize;
        iconTextSize += p.iconTextSize;
        hotseatIconSize += p.hotseatIconSize;
    }

    public DeviceProfile getDeviceProfile(Context context) {
        return context.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE ? landscapeProfile : portraitProfile;
    }

    private float weight(float x0, float y0, float x1, float y1, float pow) {
        float d = dist(x0, y0, x1, y1);
        if (Float.compare(d, 0f) == 0) {
            return Float.POSITIVE_INFINITY;
        }
        return (float) (WEIGHT_EFFICIENT / Math.pow(d, pow));
    }

    private InvariantDeviceProfile multiply(float w) {
        iconSize *= w;
        iconTextSize *= w;
        hotseatIconSize *= w;
        return this;
    }

    public int getAllAppsButtonRank() {
        if (ProviderConfig.IS_DOGFOOD_BUILD && FeatureFlags.NO_ALL_APPS_ICON) {
            throw new IllegalAccessError("Accessing all apps rank when all-apps is disabled");
        }
        return numHotseatIcons / 2;
    }
}