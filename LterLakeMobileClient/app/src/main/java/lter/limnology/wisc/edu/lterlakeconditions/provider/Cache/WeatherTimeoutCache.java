package lter.limnology.wisc.edu.lterlakeconditions.provider.Cache;

import android.app.AlarmManager;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.os.SystemClock;
import android.util.Log;
import lter.limnology.wisc.edu.lterlakeconditions.Data.WeatherData;
import lter.limnology.wisc.edu.lterlakeconditions.provider.WeatherContract;
import lter.limnology.wisc.edu.lterlakeconditions.provider.WeatherContract.WeatherValuesEntry;

/**
 *
 */
public class WeatherTimeoutCache
        implements TimeoutCache<String, WeatherData> {
    /**
     * LogCat tag.
     */
    private final static String TAG =
            WeatherTimeoutCache.class.getSimpleName();

    boolean updateCache;
    /**
     * Default cache timeout in to 15 minutes (in milliseconds).
     */
    private static final long DEFAULT_TIMEOUT = Long.valueOf(900000L);

    /**
     * Cache is cleaned up at regular intervals (i.e., twice a day) to
     * remove expired WeatherData.
     */
    public static final long CLEANUP_SCHEDULER_TIME_INTERVAL =
            AlarmManager.INTERVAL_HALF_DAY;

    /**
     * AlarmManager provides access to the system alarm services.
     * Used to schedule Cache cleanup at regular intervals to remove
     * expired Values.
     */
    private AlarmManager mAlarmManager;

    /**
     * Defines the selection clause used to query for weather values
     * that has a specific id.
     */
    private static final String WEATHER_VALUES_LOCATION_KEY_SELECTION =
            WeatherValuesEntry.COLUMN_LAKE_ID
                    + " = ?";


    /**
     * The timeout for an instance of this class in milliseconds.
     */
    private long mDefaultTimeout;

    /**
     * Context used to access the contentResolver.
     */
    private Context mContext;

    /**
     * Constructor that sets the default timeout for the cache (in
     * seconds).
     */
    public WeatherTimeoutCache(Context context) {
        // Store the context.
        mContext = context;

        // Set the timeout in milliseconds.
        mDefaultTimeout = DEFAULT_TIMEOUT;

        // Get the AlarmManager system service.
        mAlarmManager = (AlarmManager)
                context.getSystemService(Context.ALARM_SERVICE);

        // If Cache Cleanup is not scheduled, then schedule it.
        scheduleCacheCleanup(context);
    }

    /**
     * Helper method that creates a content values object that can be
     * inserted into the db's WeatherValuesEntry table from a given
     * WeatherData object.
     */
    private ContentValues makeWeatherDataContentValues(WeatherData wd,
                                                       long timeout,
                                                       String locationKey) {

        ContentValues cvs = new ContentValues();

        cvs.put(WeatherContract.WeatherValuesEntry.COLUMN_LAKE_NAME,
                wd.getLakeName());
        cvs.put(WeatherContract.WeatherValuesEntry.COLUMN_DATE,wd.getSampleDate());

        cvs.put(WeatherContract.WeatherValuesEntry.COLUMN_AIR_TEMP,
                wd.getAirTemp());
        cvs.put(WeatherContract.WeatherValuesEntry.COLUMN_LAKE_ID,
                wd.getLakeId());
        cvs.put(WeatherContract.WeatherValuesEntry.COLUMN_PHYCO_MEDIAN,
                wd.getPhycoMedian());
        cvs.put(WeatherContract.WeatherValuesEntry.COLUMN_SECCHI_EST,
                wd.getSecchiEst());
        cvs.put(WeatherContract.WeatherValuesEntry.COLUMN_WIND_DIR,
                wd.getWindDir());
        cvs.put(WeatherContract.WeatherValuesEntry.COLUMN_WIND_SPEED,
                wd.getWindSpeed());
        cvs.put(WeatherContract.WeatherValuesEntry.COLUMN_WATER_TEMP,
                wd.getWaterTemp());
        cvs.put(WeatherContract.WeatherValuesEntry.COLUMN_THERMOCLINE_MEASURE,
                wd.getThermoclineDepth());

        cvs.put(WeatherContract.WeatherValuesEntry.COLUMN_EXPIRATION_TIME,
                System.currentTimeMillis()
                        + timeout);
        return cvs;
    }

    /**
     * Place the WeatherData object into the cache. It assumes that a
     * get() method has already attempted to find this object's
     * location in the cache, and returned null.
     */
    @Override
    public void put(String key,
                    WeatherData obj) {
        putImpl(key,
                obj,
                mDefaultTimeout);
    }

    /**
     * Places the WeatherData object into the cache with a user specified
     * timeout.
     */
    @Override
    public void put(String key,
                    WeatherData obj,
                    int timeout) {
        putImpl(key,
                obj,
                // Timeout must be expressed in milliseconds.
                timeout * 1000);
    }

    /**
     * Helper method that places a WeatherData object into the
     * database.
     */
    private void putImpl(String locationKey,
                         WeatherData wd,
                         long timeout) {
        if(get(locationKey)==null) {
            // Enter the main WeatherData into the WeatherValues table.
            mContext.getContentResolver().insert
                    (WeatherContract.WeatherValuesEntry.WEATHER_VALUES_CONTENT_URI,
                            makeWeatherDataContentValues(wd,
                                    timeout,
                                    locationKey));
        }else{
            if(updateCache == true) {
                mContext.getContentResolver().update
                        (WeatherContract.WeatherValuesEntry.WEATHER_VALUES_CONTENT_URI,
                                makeWeatherDataContentValues(wd,
                                        timeout,
                                        locationKey),
                                WEATHER_VALUES_LOCATION_KEY_SELECTION,
                                new String[]{locationKey}
                        );
                updateCache = false;
            }
        }
    }

    /**
     * Attempts to retrieve the given key's corresponding WeatherData
     * object.  If the key doesn't exist or has timed out, null is
     * returned.
     */
    @Override
    public WeatherData get(final String locationKey) {
        // Attempt to retrieve the location's data from the content
        // provider.
        Cursor wdCursor = mContext.getContentResolver().query
                (WeatherContract.ACCESS_ALL_DATA_FOR_LOCATION_URI,
                        null,
                        WEATHER_VALUES_LOCATION_KEY_SELECTION,
                        new String[]{locationKey},
                        null);
        // Check that the cursor isn't null and contains an item.
        if (wdCursor != null
                && wdCursor.moveToFirst()) {
            Log.v(TAG,
                    "Cursor not null and has first item");

            // If cursor has a Weather Values object corresponding
            // to the location, check to see if it has expired.
            // If it has, delete it concurrently, else return the
            // data.
            if (wdCursor.getLong
                    (wdCursor.getColumnIndex
                            (WeatherContract.WeatherValuesEntry.COLUMN_EXPIRATION_TIME))
                    < System.currentTimeMillis()) {

                // Concurrently delete the stale data from the db
                // in a new thread.
                new Thread(new Runnable() {
                    public void run() {
                        remove(locationKey);

                    }
                }).start();
                return null;
            } else
                // Convert the contents of the cursor into a
                // WeatherData object.
                return getWeatherDataFromCursor(wdCursor);

        } else
            // Query was empty or returned null.
            return null;
    }

    private WeatherData getWeatherDataFromCursor(Cursor data) {
        if (data == null
                || !data.moveToFirst())
            return null;
        else {
            final String sampleDate =
                    data.getString(data.getColumnIndex(WeatherValuesEntry.COLUMN_DATE));
            final String lakeName =
                    data.getString(data.getColumnIndex(WeatherValuesEntry.COLUMN_LAKE_NAME));
            final String lakeId =
                    data.getString(data.getColumnIndex(WeatherValuesEntry.COLUMN_LAKE_ID));
            final Double airTemp =
                    data.getDouble(data.getColumnIndex(WeatherValuesEntry.COLUMN_AIR_TEMP));
            final Double waterTemp =
                    data.getDouble(data.getColumnIndex(WeatherValuesEntry.COLUMN_WATER_TEMP));
            final Double windSpeed =
                    data.getDouble(data.getColumnIndex(WeatherValuesEntry.COLUMN_WIND_SPEED));
            final Integer windDir =
                    data.getInt(data.getColumnIndex(WeatherValuesEntry.COLUMN_WIND_DIR));
            final Double secchiEst =
                    data.getDouble(data.getColumnIndex(WeatherValuesEntry.COLUMN_SECCHI_EST));
            final Double phycoMedian =
                    data.getDouble(data.getColumnIndex(WeatherValuesEntry.COLUMN_PHYCO_MEDIAN));
            final Double thermoclineDepth =
                    data.getDouble(data.getColumnIndex(WeatherValuesEntry.COLUMN_THERMOCLINE_MEASURE));
            return new WeatherData(sampleDate,
                                   lakeName,
                                   lakeId,
                                   airTemp,
                                   waterTemp,
                                   windSpeed,
                                   windDir,
                                   secchiEst,
                                   phycoMedian,
                                   thermoclineDepth);


        }
    }
    /**
     * Delete the Weather Values
     */
    @Override
    public void remove(String locationKey) {
        // Delete expired entries from the WeatherValues table.
        mContext.getContentResolver().delete
                (WeatherValuesEntry.WEATHER_VALUES_CONTENT_URI,
                        WEATHER_VALUES_LOCATION_KEY_SELECTION,
                        new String[]{locationKey});

    }

    /**
     * Return the current number of WeatherData objects in Database.
     *
     * @return size
     */
    @Override
    public int size() {
        // Query the data for all rows of the Weather Values table.
        Cursor cursor =
                mContext.getContentResolver().query
                        (WeatherContract.WeatherValuesEntry.WEATHER_VALUES_CONTENT_URI,
                                new String[]{WeatherValuesEntry._ID},
                                null,
                                null,
                                null);
        // Return the number of rows in the table, which is equivlent
        // to the number of objects
        return cursor.getCount();
    }


    /**
     * Remove all expired WeatherData rows from the database.  This
     * method is called periodically via the AlarmManager.
     */
    public void removeExpiredWeatherData() {
        // Defines the selection clause used to query for weather values
        // that has expired.
        final String EXPIRATION_SELECTION =
                WeatherValuesEntry.COLUMN_EXPIRATION_TIME
                        + " <= ?";

        // First query the db to find all expired Weather Values
        // objects' ids.
        Cursor expiredData =
                mContext.getContentResolver().query
                        (WeatherValuesEntry.WEATHER_VALUES_CONTENT_URI,
                                new String[]{WeatherValuesEntry.COLUMN_LAKE_ID},
                                EXPIRATION_SELECTION,
                                new String[]{String.valueOf(System.currentTimeMillis())},
                                null);
        // Use the expired data id's to delete the designated
        // entries from table.
        if (expiredData != null
                && expiredData.moveToFirst()) {
            do {
                // Get the location to delete.
                final String deleteLocation =
                        expiredData.getString
                                (expiredData.getColumnIndex
                                        (WeatherValuesEntry.COLUMN_LAKE_ID));
                remove(deleteLocation);

            } while (expiredData.moveToNext());
        }
    }


    /**
     * Helper method that uses AlarmManager to schedule Cache Cleanup at regular
     * intervals.
     *
     * @param context
     */
    private void scheduleCacheCleanup(Context context) {
        // Only schedule the Alarm if it's not already scheduled.
        if (!isAlarmActive(context)) {
            // Schedule an alarm after a certain timeout to start a
            // service to delete expired data from Database.
            mAlarmManager.setInexactRepeating
                    (AlarmManager.ELAPSED_REALTIME_WAKEUP,
                            SystemClock.elapsedRealtime()
                                    + CLEANUP_SCHEDULER_TIME_INTERVAL,
                            CLEANUP_SCHEDULER_TIME_INTERVAL,
                            CacheCleanupReceiver.makeReceiverPendingIntent(context));
        }
    }

    /**
     * Helper method to check whether the Alarm is already active or not.
     *
     * @param context
     * @return boolean, whether the Alarm is already active or not
     */
    private boolean isAlarmActive(Context context) {
        // Check whether the Pending Intent already exists or not.
        return CacheCleanupReceiver.makeCheckAlarmPendingIntent(context) != null;
    }
}
