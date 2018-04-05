package com.hrily.artutorial;

import android.content.Context;
import android.location.Location;

/**
 * Created by krzysztofjackowski on 24/09/15.
 */
public interface OnLocationChangedListener {
    void onLocationChanged(Location currentLocation);
    Location getLocation(Context context);
}
