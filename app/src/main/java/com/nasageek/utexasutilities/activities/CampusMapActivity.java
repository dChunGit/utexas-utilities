package com.nasageek.utexasutilities.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.SearchManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatDialogFragment;
import android.support.v7.widget.SearchView;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.commonsware.cwac.security.RuntimePermissionUtils;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.InfoWindowAdapter;
import com.google.android.gms.maps.GoogleMap.OnInfoWindowClickListener;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.android.MarkerManager;
import com.nasageek.utexasutilities.AnalyticsHandler;
import com.nasageek.utexasutilities.AsyncTask;
import com.nasageek.utexasutilities.BuildConfig;
import com.nasageek.utexasutilities.BuildingProvider;
import com.nasageek.utexasutilities.R;
import com.nasageek.utexasutilities.ThemedArrayAdapter;
import com.nasageek.utexasutilities.UTilitiesApplication;
import com.nasageek.utexasutilities.model.Placemark;
//<<<<<<< HEAD
//=======
//import com.squareup.okhttp.MediaType;
//import com.squareup.okhttp.OkHttpClient;
//import com.squareup.okhttp.Request;
//import com.squareup.okhttp.RequestBody;
//import com.squareup.okhttp.Response;
//>>>>>>> 165b1c1e80dab75a67382a640eb98622801d8b1b

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CampusMapActivity extends BaseActivity implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

    private LocationRequest locationRequest;

    // Request code to use when launching the resolution activity
    private static final int REQUEST_RESOLVE_ERROR = 1001;
    // Unique tag for the error dialog fragment
    private static final String DIALOG_ERROR = "dialog_error";
    // Bool to track whether the app is already resolving an error
    private boolean mResolvingError = false;
    private static final String STATE_RESOLVING_ERROR = "resolving_error";
    private GoogleApiClient apiClient;
    private boolean locationEnabled;
    private static final String STATE_LOCATION_ENABLED = "location_enabled";

    private AssetManager assets;
    private List<String> stops_al;
    private List<String> traces_al;
    private String routeid = null;
    private int routeIndex;
    private static final String STATE_ROUTE_INDEX = "route_index";
    private Collection<Placemark> buildingDataSet;

    private SharedPreferences settings;

    private View mapView;
    protected Boolean mSetCameraToBounds = false;
    private boolean setInitialLocation = false;
    private static final String STATE_SET_INITIAL_LOCATION = "set_initial_location";
    private LatLngBounds.Builder llbuilder;
    private boolean showAllBuildings = false;
    private static final String STATE_SHOW_ALL_BUILDINGS = "show_all_buildings";
    private List<String> buildingIdList = new ArrayList<>();
    private static final String STATE_BUILDING_LIST = "building_id_list";

    private MarkerManager.Collection shownBuildings;
    private MarkerManager.Collection shownStops;
    private static final String STOP_TIME_PLACEHOLDER = "Loading...";
    private Map<String, Polyline> polylineMap;
    private GoogleMap mMap;

    private static final int CURRENT_ROUTES_VERSION = 1;
    private static final int BURNT_ORANGE = Color.parseColor("#DDCC5500");
    private static final LatLng UT_TOWER_LOC = new LatLng(30.285706, -97.739423);
    private static final String NO_ROUTE_ID = "0";

    private boolean haveRequestedLocationPermission;
    private static final String STATE_REQUESTED_LOC_PERMISSION = "requested_loc_permission";
    private static final int REQUEST_LOCATION_FOR_MAP = 1;
    private RuntimePermissionUtils runtimePermissions;

    //@formatter:off
    public enum Route {
        No_Overlay(0, "No Bus Route Overlay"),
        Crossing_Place(670, "Crossing Place"),
        East_Campus(641, "East Campus"),
        Forty_Acres(640, "Forty Acres"),
        Forty_Acres_East_Campus(682, "Forty Acres/East Campus"),
        Far_West(661, "Far West"),
        Intramural_Fields(656, "Intramural Fields"),
        Intramural_Fields_Far_West(681, "Intramural Fields/Far West"),
        Lake_Austin(663, "Lake Austin"),
        Lakeshore(672, "Lakeshore"),
        North_Riverside(671, "North Riverside"),
        North_Riverside_Lakeshore(680, "North Riverside/Lakeshore"),
        Red_River(653, "Red River"),
        West_Campus(642, "West Campus");
        //@formatter:on
        private final int code;
        private final String fullName;

        Route(int c, String fullName) {
            code = c;
            this.fullName = fullName;
        }

        public String getCode() {
            return code + "";
        }

        @Override
        public String toString() {
            return fullName;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.map_layout);
        settings = PreferenceManager.getDefaultSharedPreferences(this);
        runtimePermissions = new RuntimePermissionUtils(this);
        if (savedInstanceState != null) {
            mResolvingError = savedInstanceState.getBoolean(STATE_RESOLVING_ERROR, false);
            setInitialLocation = savedInstanceState.getBoolean(STATE_SET_INITIAL_LOCATION, false);
            buildingIdList = savedInstanceState.getStringArrayList(STATE_BUILDING_LIST);
            showAllBuildings = savedInstanceState.getBoolean(STATE_SHOW_ALL_BUILDINGS);
            routeIndex = savedInstanceState.getInt(STATE_ROUTE_INDEX);
            locationEnabled = savedInstanceState.getBoolean(STATE_LOCATION_ENABLED);
            haveRequestedLocationPermission = savedInstanceState.getBoolean(STATE_REQUESTED_LOC_PERMISSION);
        } else {
            routeIndex = Integer.parseInt(settings.getString("default_bus_route", NO_ROUTE_ID));
            locationEnabled = runtimePermissions.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION);
            haveRequestedLocationPermission = false;
        }
        locationRequest = new LocationRequest()
                .setInterval(5000)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        assets = getAssets();
        polylineMap = new HashMap<>();

        setupActionBar();
        buildingDataSet = getBuildingData();
        ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                .getMapAsync(this);
    }

    public void setupLocationIfNeeded() {
        if (apiClient == null) {
            apiClient = new GoogleApiClient.Builder(this)
                    .addApi(LocationServices.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
            apiClient.connect();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (runtimePermissions.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            setupLocationIfNeeded();
        } else if (!haveRequestedLocationPermission) {
            haveRequestedLocationPermission = true;
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION_FOR_MAP);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_LOCATION_FOR_MAP:
                locationEnabled = grantResults.length > 0 &&
                        grantResults[0] == PackageManager.PERMISSION_GRANTED;
                if (locationEnabled) {
                    setupLocationIfNeeded();
                } else {
                    moveToInitialLoc(false);
                }
                if (checkReady()) {
                    mMap.setMyLocationEnabled(locationEnabled);
                }
                break;
        }
    }

    public void requestLocationUpdates() {
        LocationServices.FusedLocationApi.requestLocationUpdates(apiClient, locationRequest, this);
    }

    @Override
    public void onConnected(Bundle bundle) {
        // hopefully this will keep the current location fairly fresh? Not really sure
        // if this is necessary
        requestLocationUpdates();
        moveToInitialLoc(true);
    }

    @Override
    public void onConnectionSuspended(int i) {
        if (BuildConfig.DEBUG) {
            Toast.makeText(this, "ApiClient connection suspended", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        if (mResolvingError) {
            // Already attempting to resolve an error.
            return;
        } else if (connectionResult.hasResolution()) {
            try {
                mResolvingError = true;
                connectionResult.startResolutionForResult(this, REQUEST_RESOLVE_ERROR);
            } catch (IntentSender.SendIntentException e) {
                // There was an error with the resolution intent. Try again.
                apiClient.connect();
            }
        } else {
            // Show dialog using GoogleApiAvailability.getErrorDialog()
            showErrorDialog(connectionResult.getErrorCode());
            mResolvingError = true;
        }
    }

    private void showErrorDialog(int errorCode) {
        // Create a fragment for the error dialog
        ErrorDialogFragment dialogFragment = new ErrorDialogFragment();
        // Pass the error that should be displayed
        Bundle args = new Bundle();
        args.putInt(DIALOG_ERROR, errorCode);
        dialogFragment.setArguments(args);
        dialogFragment.show(getSupportFragmentManager(), "errordialog");
    }

    /* Called from ErrorDialogFragment when the dialog is dismissed. */
    public void onDialogDismissed() {
        mResolvingError = false;
    }

    /* A fragment to display an error dialog */
    public static class ErrorDialogFragment extends AppCompatDialogFragment {
        public ErrorDialogFragment() { }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Get the error code and retrieve the appropriate dialog
            int errorCode = this.getArguments().getInt(DIALOG_ERROR);
            return GoogleApiAvailability.getInstance().getErrorDialog(
                    this.getActivity(), errorCode, REQUEST_RESOLVE_ERROR);
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            ((CampusMapActivity) getActivity()).onDialogDismissed();
        }
    }

    @Override
    public void onLocationChanged(Location location) {
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setMyLocationEnabled(locationEnabled);
        MarkerManager markerManager = new MarkerManager(mMap);
        shownBuildings = markerManager.newCollection();
        shownStops = markerManager.newCollection();

        UiSettings ui = mMap.getUiSettings();
        ui.setMyLocationButtonEnabled(true);
        ui.setZoomControlsEnabled(true);
        ui.setAllGesturesEnabled(true);
        ui.setCompassEnabled(true);
        Intent testPackage = new Intent();
        testPackage.setPackage("com.google.android.apps.maps");
        ui.setMapToolbarEnabled(testPackage.resolveActivity(getPackageManager()) != null);

        shownStops.setOnInfoWindowAdapter(new StopInfoWindowAdapter());
        shownBuildings.setOnInfoWindowAdapter(new MyInfoWindowAdapter());
        mMap.setOnInfoWindowClickListener(new InfoClickListener());
        mMap.setInfoWindowAdapter(markerManager);

        loadRoute(routeid);
        if (buildingIdList.size() > 0) {
            loadBuildingOverlay(false, false);
        }

        mapView = getSupportFragmentManager().findFragmentById(R.id.map).getView();
        if (mapView != null && mapView.getViewTreeObserver() != null
                && mapView.getViewTreeObserver().isAlive()) {
            mapView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @SuppressLint("NewApi")
                @SuppressWarnings("deprecation")
                @Override
                public void onGlobalLayout() {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                        mapView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                    } else {
                        mapView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }
                    if (mSetCameraToBounds) {
                        mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(llbuilder.build(), 100));
                        mSetCameraToBounds = false;
                        setInitialLocation = true;
                    }
                }
            });
        }
        // If location is enabled, then we want the GoogleApiClient to handle moving to the
        // initial location. Otherwise, we might not have a location by the time this is called
        if (!locationEnabled) {
            moveToInitialLoc(locationEnabled);
        }
        handleIntent(getIntent());

    }

    /**
     * Get data about all buildings on campus
     *
     * @return Collection of Placemarks
     */
    private Collection<Placemark> getBuildingData() {
        String[] projection = {"abbr", "name", "lng", "lat"};
        Cursor cursor = getContentResolver().query(
                BuildingProvider.CONTENT_URI, projection, null, null, null);
        ArrayDeque<Placemark> buildings = new ArrayDeque<>(cursor.getCount());
        while (cursor.moveToNext()) {
            buildings.add(new Placemark(cursor.getString(cursor.getColumnIndex("abbr")),
                                        cursor.getString(cursor.getColumnIndex("name")),
                                        cursor.getDouble(cursor.getColumnIndex("lat")),
                                        cursor.getDouble(cursor.getColumnIndex("lng"))));
        }
        cursor.close();
        return buildings;
    }

    @Override
    protected void setupActionBar() {
        super.setupActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        final ArrayAdapter<Route> adapter = new ThemedArrayAdapter<>(actionBar.getThemedContext(),
                android.R.layout.simple_spinner_item, Route.values());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        actionBar.setListNavigationCallbacks(adapter, (itemPosition, itemId) -> {
            loadRoute(adapter.getItem(itemPosition).getCode());
            return true;
        });

        // use a simple versioning scheme to ensure that I can trigger a wipe
        // of the default route on an update
        int routesVersion = settings.getInt("routes_version", 0);
        if (routesVersion < CURRENT_ROUTES_VERSION) {
            settings.edit().putString("default_bus_route", NO_ROUTE_ID).apply();
            settings.edit().putInt("routes_version", CURRENT_ROUTES_VERSION).apply();
            // only bother the user if they've set a default route
            if (routeIndex != 0) {
                Toast.makeText(
                        this,
                        "Your default bus route has been reset due to" +
                                " a change in UT's shuttle system.",
                        Toast.LENGTH_LONG).show();
            }
            routeIndex = 0;
        }

        routeid = adapter.getItem(routeIndex).getCode();
        actionBar.setSelectedNavigationItem(routeIndex);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_RESOLVE_ERROR) {
            mResolvingError = false;
            if (resultCode == RESULT_OK) {
                // Make sure the app is not already connected or attempting to connect
                if (!apiClient.isConnecting() && !apiClient.isConnected()) {
                    apiClient.connect();
                }
            }
        }
    }

    /**
     * Loads the buildings specified in buildingIdList or shows the user an
     * error if any of the buildingIds are invalid
     *
     * @param autoZoom - true to autozoom to 16 when moving (will not animate!)
     *                 to the building, false to just animate to building; should
     *                 only be true when you are entering the map from an entry point
     *                 other than the dashboard
     */
    public void loadBuildingOverlay(boolean centerCameraOnBuildings, boolean autoZoom) {
        // TODO: don't center on buildings when restoring state
        int foundCount = 0;
        llbuilder = LatLngBounds.builder();

        for (Placemark pm : buildingDataSet) {
            if (buildingIdList.contains(pm.getTitle())) {
                foundCount++;

                LatLng buildingLatLng = new LatLng(pm.getLatitude(), pm.getLongitude());
                Marker buildingMarker = shownBuildings.addMarker(new MarkerOptions()
                            .position(buildingLatLng)
                            .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_building2))
                            .title(pm.getTitle())
                            .snippet(pm.getDescription()));
                llbuilder.include(buildingLatLng);

                // don't move the camera around or show InfoWindows for more than one building
                if (buildingIdList.size() == 1 && centerCameraOnBuildings) {
                    if (autoZoom) {
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(buildingLatLng, 16f));
                    } else {
                        mMap.animateCamera(CameraUpdateFactory.newLatLng(buildingLatLng));
                    }
                    setInitialLocation = true;
                    buildingMarker.showInfoWindow();
                }
            }
        }
        if (foundCount > 1 && centerCameraOnBuildings) {
            mSetCameraToBounds = true;
        }
        if (foundCount != buildingIdList.size()) {
            Toast.makeText(this, "One or more buildings could not be found", Toast.LENGTH_SHORT)
                    .show();
        }
        buildingIdList.clear();
    }

    /**
     * Centers map at some location, either the user's or the UT Tower's, depending on a
     * combination of user preferences and permissions. This will only run once, so don't
     * worry about calling it too much.
     *
     * @param locationEnabled whether user has granted the location permission
     */
    public void moveToInitialLoc(boolean locationEnabled) {
        if (checkReady() && !setInitialLocation) {
            LatLng initialLocation;
            if (settings.getBoolean("starting_location", false) && locationEnabled) {
                Location loc = LocationServices.FusedLocationApi.getLastLocation(apiClient);
                if (loc != null) {
                    initialLocation = new LatLng(loc.getLatitude(), loc.getLongitude());
                } else {
                    Snackbar.make(findViewById(R.id.map),
                            "User location unavailable",
                            Snackbar.LENGTH_LONG)
                            .setAction("Enable location", v -> {
                                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                                startActivity(intent);
                            })
                            .show();
                    initialLocation = UT_TOWER_LOC;
                }
            } else {
                initialLocation = UT_TOWER_LOC;
            }
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(initialLocation, 16f));
            setInitialLocation = true;
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putBoolean(STATE_RESOLVING_ERROR, mResolvingError);
        savedInstanceState.putBoolean(STATE_SET_INITIAL_LOCATION, setInitialLocation);
        Set<String> savedBuildings = new HashSet<>();
        if (shownBuildings != null) {
            for (Marker m : shownBuildings.getMarkers()) {
                savedBuildings.add(m.getTitle());
            }
        }
        savedInstanceState.putStringArrayList(STATE_BUILDING_LIST, new ArrayList<>(savedBuildings));
        savedInstanceState.putBoolean(STATE_SHOW_ALL_BUILDINGS, showAllBuildings);
        savedInstanceState.putInt(STATE_ROUTE_INDEX, actionBar.getSelectedNavigationIndex());
        savedInstanceState.putBoolean(STATE_LOCATION_ENABLED, locationEnabled);
        savedInstanceState.putBoolean(STATE_REQUESTED_LOC_PERMISSION, haveRequestedLocationPermission);
    }

    @Override
    public void onNewIntent(Intent intent) {
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (checkReady()) {
            if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
                buildingIdList.add(intent.getStringExtra(SearchManager.QUERY).toUpperCase(
                        Locale.ENGLISH));
                loadBuildingOverlay(true, false); // IDs gotten from search, no need to zoom
            } else if (getString(R.string.building_intent).equals(intent.getAction())) {
                if (!intent.hasExtra("buildings")) // didn't come from an external activity
                {
                    buildingIdList.add(intent.getDataString());
                    loadBuildingOverlay(true, false); // IDs from search suggestions, no auto-zoom

                } else {
                    buildingIdList.addAll(intent.getStringArrayListExtra("buildings"));
                    loadBuildingOverlay(true, true); // IDs from external source, should auto-zoom
                }
            }
        }
    }

    public void search(String q) {
        // buildingId = q;
        buildingIdList.add(q.toUpperCase(Locale.ENGLISH));
    }

    // lifted from http://stackoverflow.com/a/23952928/3214339
    private String loadAssetAsString(String path) {
        StringBuilder buffer = new StringBuilder();
        InputStream in = null;
        try {
            in = assets.open(path);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String str;
            boolean isFirst = true;
            while ((str = reader.readLine()) != null) {
                if (isFirst)
                    isFirst = false;
                else
                    buffer.append('\n');
                buffer.append(str);
            }
        } catch (IOException e) {
            Log.d(CampusMapActivity.class.getSimpleName(), "Error opening asset");
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    Log.d(CampusMapActivity.class.getSimpleName(), "Error closing asset");
                }
            }
        }
        return buffer.toString();
    }

    /**
     * Displays a route as a set of stop markers and polylines
     *
     * @param routeid - the route id to load
     */
    private void loadRoute(String routeid) {
        if (!checkReady()) {
            return;
        }
        this.routeid = routeid;
        if (NO_ROUTE_ID.equals(routeid)) {
            // remove any currently showing routes and return
            clearAllMapRoutes();
            shownStops.clear();
            return;
        }
        AnalyticsHandler.trackBusRouteEvent();
        try {
            initRouteData();
            String tracePath = "traces/" + traces_al.get(traces_al.indexOf(routeid + ".txt"));
            String trace = loadAssetAsString(tracePath);
            Deque<LatLng> navData = new ArrayDeque<>();
            for (String latlng : trace.split(";")) {
                navData.add(new LatLng(Double.parseDouble(latlng.split(",")[0]),
                                       Double.parseDouble(latlng.split(",")[1])));
            }

            drawPath(navData, BURNT_ORANGE);

            String stopPath = "stops/" + stops_al.get(stops_al.indexOf(routeid + "_stops.txt"));
            String stopData = loadAssetAsString(stopPath);
            String[] stops = stopData.split("\n");

            // clear the stops from the old route
            shownStops.clear();

            for (String stop : stops) {
                String data[] = stop.split("\t");
                Double lat = Double.parseDouble(data[0].split(",")[0].trim());
                Double lng = Double.parseDouble(data[0].split(",")[1].trim());
                String title = data[1];
                int stopid = Integer.parseInt(data[2].trim());

                Marker marker = shownStops.addMarker(new MarkerOptions()
                        .position(new LatLng(lat, lng))
                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_bus))
                        .title(title)
                        .snippet(STOP_TIME_PLACEHOLDER));
                marker.setTag(new BusStop(stopid));
            }

        } catch (IOException e) {
            e.printStackTrace();
            Log.d("DirectionMap",
                    "Exception loading some file related to the kml or the stops files.");
        }
    }

    class BusStop {
        public int stopid;
        public String times = "";
        public CheckStopTask fetchTimesTask;
        public boolean refreshing = false;

        public BusStop(int stopid) {
            this.stopid = stopid;
        }
    }

    private void initRouteData() throws IOException {
        String[] stops = assets.list("stops");
        String[] traces = assets.list("traces");
        stops_al = Arrays.asList(stops);
        traces_al = Arrays.asList(traces);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mMap != null) {
            mMap.getUiSettings().setCompassEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(locationEnabled);
        }
        if (apiClient != null && apiClient.isConnected()) {
            requestLocationUpdates();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mMap != null) {
            mMap.getUiSettings().setCompassEnabled(false);
            mMap.getUiSettings().setMyLocationButtonEnabled(false);
        }
        if (apiClient != null && apiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(apiClient, this);
        }
    }

    @Override
    public void onStop() {
        if (apiClient != null) {
            apiClient.disconnect();
        }
        super.onStop();
    }

    @Override
    public void onDestroy() {
        if (apiClient != null) {
            apiClient.unregisterConnectionCallbacks(this);
            apiClient.unregisterConnectionFailedListener(this);
        }
        super.onDestroy();
    }

    /**
     * When the map is not ready the CameraUpdateFactory cannot be used. This
     * should be called on all entry points that call methods on the Google Maps
     * API.
     */
    private boolean checkReady() {
        if (mMap == null) {
            return false;
        }
        return true;
    }

    /**
     * Does the actual drawing of the route polyline, based on the geo points provided in the navset
     *
     * @param navSet Navigation set bean that holds the route information, incl. geo pos
     * @param color  Color in which to draw the lines
     */
    private void drawPath(Deque<LatLng> navSet, int color) {
        clearAllMapRoutes();
        PolylineOptions polyOpt = new PolylineOptions()
                .color(color)
                .width(5f);
        polyOpt.addAll(navSet);
        Polyline routePolyline = mMap.addPolyline(polyOpt);
        polylineMap.put(routePolyline.getId(), routePolyline);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = this.getMenuInflater();
        inflater.inflate(R.menu.map_menu, menu);
        menu.findItem(R.id.showAllBuildings).setChecked(showAllBuildings);
        final MenuItem searchItem = menu.findItem(R.id.search);
        final SearchView searchView = (SearchView) MenuItemCompat.getActionView(searchItem);
        SearchManager searchManager = (SearchManager) getSystemService(SEARCH_SERVICE);
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        searchView.setOnSuggestionListener(new SearchView.OnSuggestionListener() {
            @Override
            public boolean onSuggestionSelect(int position) {
                return false;
            }

            @Override
            public boolean onSuggestionClick(int position) {
                MenuItemCompat.collapseActionView(searchItem);
                return false;
            }
        });
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                MenuItemCompat.collapseActionView(searchItem);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.showAllBuildings:
                if (checkReady()) {
                    if (item.isChecked()) {
                        shownBuildings.clear();
                    } else {
                        showAllBuildingMarkers();
                    }
                    showAllBuildings = !item.isChecked();
                    item.setChecked(showAllBuildings);
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void showAllBuildingMarkers() {
        for (Placemark pm : buildingDataSet) {
            shownBuildings.addMarker(new MarkerOptions()
                    .position(new LatLng(pm.getLatitude(), pm.getLongitude()))
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_building2))
                    .title(pm.getTitle())
                    .snippet(pm.getDescription()));
        }
    }

    private void clearAllMapRoutes() {
        for (String id : polylineMap.keySet()) {
            polylineMap.get(id).remove();
        }
        polylineMap.clear();
    }

    class MyInfoWindowAdapter implements InfoWindowAdapter {
        protected final LinearLayout infoLayout;
        protected final TextView infoTitle, infoSnippet;

        public MyInfoWindowAdapter() {
            infoLayout = (LinearLayout) getLayoutInflater().inflate(R.layout.info_window_layout,
                    null);
            infoTitle = (TextView) infoLayout.findViewById(R.id.iw_title);
            infoSnippet = (TextView) infoLayout.findViewById(R.id.iw_snippet);
        }

        @Override
        public View getInfoContents(Marker marker) {
            String title = marker.getTitle();
            String snippet = marker.getSnippet();
            if (!infoTitle.getText().equals(title)) {
                // Span for bolding the title
                SpannableString styledTitle = new SpannableString(title);
                styledTitle.setSpan(new StyleSpan(Typeface.BOLD), 0, title.length(), 0);

                infoTitle.setText(styledTitle);
                infoSnippet.setText(snippet);
            }
            return infoLayout;
        }

        @Override
        public View getInfoWindow(Marker marker) { return null; }
    }

    class StopInfoWindowAdapter extends MyInfoWindowAdapter {
        @Override
        public View getInfoContents(Marker marker) {
            BusStop stopInfo = (BusStop) marker.getTag();
            if (stopInfo.refreshing ||
                    (stopInfo.fetchTimesTask != null &&
                            stopInfo.fetchTimesTask.getStatus() == AsyncTask.Status.FINISHED &&
                            !stopInfo.fetchTimesTask.failed)) {
                View view = super.getInfoContents(marker);
                infoSnippet.setText(stopInfo.times);
                return view;
            } else if (stopInfo.fetchTimesTask == null || stopInfo.fetchTimesTask.failed) {
                int stopid = stopInfo.stopid;
                stopInfo.fetchTimesTask = new CheckStopTask(infoSnippet);
                stopInfo.fetchTimesTask.execute(stopid, marker);
            }
            return super.getInfoContents(marker);
        }
    }

    private class CheckStopTask extends AsyncTask<Object, Void, String> {
        public static final String ERROR_NO_STOP_TIMES =
                "There are no upcoming times\nfor this stop on capmetro.org";
        public static final String ERROR_COULD_NOT_REACH_CAPMETRO =
                "CapMetro.org could not be reached;\ntry checking your internet connection";
        public static final String CAPMETRO_STOP_URL =
                "http://capmetro.hafas.cloud/bin/mgate.exe";

        Marker stopMarker;
        TextView snippet;
        boolean failed = false;

        public CheckStopTask(TextView snippet) {
            this.snippet = snippet;
        }

        @Override
        protected String doInBackground(Object... params) {
            String stopid = Integer.toString((Integer) params[0]);
            stopMarker = (Marker) params[1];
            String times = "";
            OkHttpClient httpclient = UTilitiesApplication.getInstance(CampusMapActivity.this)
                    .getHttpClient();
            JSONObject post_data, data;
            try {
                post_data = new JSONObject(String.format("{" +
                        "    'client': {}," +
                        "    'formatted': false," +
                        "    'lang': 'eng'," +
                        "    'svcReqL': [" +
                        "        {" +
                        "            'id': '1|1'," +
                        "            'meth': 'StationBoard'," +
                        "            'req': {" +
                        "                'jnyFltrL': [{" +
                        "                    'mode': 'INC'," +
                        "                    'type': 'PROD'," +
                        "                    'value': 32" +
                        "                }]," +
                        "                'getPasslist': false," +
                        "                'maxJny': 20," +
                        "                'stbLoc': {'extId': '%s'}" +
                        "            }" +
                        "        }" +
                        "    ]," +
                        "    'ver': '1.13'" +
                        "}", stopid));
            } catch (JSONException e) {
                failed = true;
                throw new RuntimeException(e);
            }

            try {
                RequestBody body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), post_data.toString());
                Request get = new Request.Builder()
                        .url(CAPMETRO_STOP_URL)
                        .post(body)
                        .build();
                Response response = httpclient.newCall(get).execute();
                if (!response.isSuccessful()) {
                    throw new IOException("Bad response code " + response);
                }
                data = new JSONObject(response.body().string());
            } catch (IOException | JSONException e) {
                failed = true;
                e.printStackTrace();
                return ERROR_COULD_NOT_REACH_CAPMETRO;
            }

            try {
                JSONObject svcRes = data.getJSONArray("svcResL").getJSONObject(0);
                if (!svcRes.getString("err").equals("OK")) {
                    failed = true;
                    return ERROR_NO_STOP_TIMES;
                } else {
                    JSONArray products = svcRes.getJSONObject("res").getJSONObject("common").getJSONArray("prodL");
                    JSONArray journies = svcRes.getJSONObject("res").optJSONArray("jnyL");
                    int foundTimes = 0;
                    for (int i = 0; i < (journies == null ? 0 : journies.length()); i++) {
                        JSONObject journey = journies.getJSONObject(i);
                        int productIdx = journey.getInt("prodX");
                        JSONObject product = products.getJSONObject(productIdx);
                        String journeyRoute = product.optString("number").equals(routeid)
                                ? product.getString("number")
                                : product.optString("nameS");
                        if (!journeyRoute.equals(routeid)) {
                            continue;
                        }
                        if (foundTimes++ >= 4) {
                            break;
                        }
                        String arriveTime = journey.getJSONObject("stbStop").getString("dTimeS");
                        // arriveTime sometimes contains 2 extra (seemingly unnecessary) leading 0s
                        if (arriveTime.length() == 8) {
                            arriveTime = arriveTime.substring(2);
                        }
                        SimpleDateFormat parser = new SimpleDateFormat("kkmmss");
                        SimpleDateFormat formatter = new SimpleDateFormat("h:mm aa");
                        try {
                            Date date = parser.parse(arriveTime);
                            times += formatter.format(date).toLowerCase() + '\n';
                        } catch (ParseException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    if (foundTimes == 0) {
                        failed = true;
                        return ERROR_NO_STOP_TIMES;
                    }
                    // trim off that trailing \n
                    times = times.trim();
                }
            } catch (JSONException je) {
                failed = true;
                return ERROR_COULD_NOT_REACH_CAPMETRO;
            }
            return times;
        }

        @Override
        protected void onPostExecute(String times) {
            BusStop stopInfo = (BusStop) stopMarker.getTag();
            stopInfo.times = times;
            if (snippet.getText().equals(STOP_TIME_PLACEHOLDER)) {
                // fix issue with InfoWindow "cycling" if the user taps
                // other markers while a marker's InfoWindow is loading data.
                if (stopMarker.isInfoWindowShown()) {
                    snippet.setText(times);
                    stopInfo.refreshing = true;
                    stopMarker.showInfoWindow();
                    stopInfo.refreshing = false;
                }
            }
        }
    }

    class InfoClickListener implements OnInfoWindowClickListener {
        @Override
        public void onInfoWindowClick(final Marker marker) {
            final String markerType;
            if (shownBuildings.getMarkers().contains(marker)) {
                markerType = "building";
            } else if (shownStops.getMarkers().contains(marker)) {
                markerType = "stop";
            } else {
                markerType = "location";
            }
            AlertDialog.Builder opendirections_builder = new AlertDialog.Builder(
                    CampusMapActivity.this);
            opendirections_builder
                    .setMessage(
                            "Would you like to open Google Maps for directions to this "
                                    + markerType + "?"
                    ).setCancelable(true)
                    .setTitle("Get directions")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        double dstLat = marker.getPosition().latitude;
                        double dstLng = marker.getPosition().longitude;

                        AnalyticsHandler.trackGetDirectionsEvent();
                        Uri dirUri = Uri.parse("google.navigation:q="+dstLat+","+dstLng+"&mode=w");
                        Intent intent = new Intent(Intent.ACTION_VIEW, dirUri);
                        if (intent.resolveActivity(CampusMapActivity.this.getPackageManager()) != null) {
                            startActivity(intent);
                        } else {
                            Snackbar.make(findViewById(R.id.map),
                                    "No apps available to handle directions",
                                    Snackbar.LENGTH_LONG)
                                    .show();
                        }
                    }).setNegativeButton("No", (dialog, id) -> {
                        dialog.cancel();
                    });
            AlertDialog opendirections = opendirections_builder.create();
            opendirections.show();
        }
    }
}
