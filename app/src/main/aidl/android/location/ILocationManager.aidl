package android.location;

import android.location.Location;

interface ILocationManager {
    void addTestProvider(String name, boolean requiresNetwork, boolean requiresSatellite,
            boolean requiresCell, boolean hasMonetaryCost, boolean supportsAltitude,
            boolean supportsSpeed, boolean supportsBearing, int powerRequirement, int accuracy);

    void removeTestProvider(String provider);

    void setTestProviderLocation(String provider, in Location loc);

    void setTestProviderEnabled(String provider, boolean enabled);
}