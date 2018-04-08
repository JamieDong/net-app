package com.warrior.util;

import android.content.res.Resources;
import android.util.DisplayMetrics;

import com.warrior.app.Jc;

/**
 * Created by Jamie
 */

public final class JcUtil {
    public static int getScreenWidth() {
        final Resources resources = Jc.getApplicationContext().getResources();
        final DisplayMetrics dm = resources.getDisplayMetrics();
        return dm.widthPixels;
    }

    public static int getScreenHeight() {
        final Resources resources = Jc.getApplicationContext().getResources();
        final DisplayMetrics dm = resources.getDisplayMetrics();
        return dm.heightPixels;
    }
}
