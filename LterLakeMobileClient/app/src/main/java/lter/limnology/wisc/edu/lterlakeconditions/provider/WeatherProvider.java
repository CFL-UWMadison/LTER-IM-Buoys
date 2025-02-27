package lter.limnology.wisc.edu.lterlakeconditions.provider;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

/**
 *
 */
public class WeatherProvider extends ContentProvider {
    /**
     * Logcat tag.
     */
    private final String TAG =
            getClass().getCanonicalName();

    /**
     * Constant for the Weather Values table's name.
     */
    private static final String WEATHER_VALUES_TABLE_NAME =
            WeatherContract.WeatherValuesEntry.WEATHER_VALUES_TABLE_NAME;


    /**
     * The database helper that is used to manage the providers
     * database.
     */
    private WeatherDatabaseHelper mDatabaseHelper;

    /**
     * Hook method called when the provider is created.
     */
    @Override
    public boolean onCreate() {
        mDatabaseHelper =
                new WeatherDatabaseHelper(getContext());
        return true;
    }

    /**
     * Helper method that appends a given key id to the end of the
     * WHERE statement parameter.
     */
    private static String addKeyIdCheckToWhereStatement(String whereStatement,
                                                        long id) {
        String newWhereStatement;
        if (TextUtils.isEmpty(whereStatement))
            newWhereStatement = "";
        else
            newWhereStatement = whereStatement + " AND ";

        return newWhereStatement
                + " _id = "
                + "'"
                + id
                + "'";
    }

    /**
     * Get a Cursor containing all data for a selected location.    It
     * will have a row for each Weather object corresponding to the
     * location, with the Weather Values columns repeated.
     */
    private Cursor getAllLocationsData(String locationKey) {

        final String FROM_WHERE_STATEMENT_ALL_LOCATION_DATA =
                "SELECT * FROM "
                        + WEATHER_VALUES_TABLE_NAME
                        + " WHERE "
                        + WEATHER_VALUES_TABLE_NAME
                        + "."
                        + WeatherContract.WeatherValuesEntry.COLUMN_LAKE_ID
                        + " = ? ";


        // Retreive the database from the helper
        final SQLiteDatabase db =
                mDatabaseHelper.getReadableDatabase();

        // Formulate the query statement.
        final String selectQuery =
                FROM_WHERE_STATEMENT_ALL_LOCATION_DATA;

        Log.v(TAG,
                selectQuery);

        return db.rawQuery(selectQuery,
                new String[] { locationKey });
    }

    /**
     * Method called to handle query requests from client
     * applications.
     */
    @Override
    public Cursor query(Uri uri,
                        String[] projection,
                        String whereStatement,
                        String[] whereStatementArgs,
                        String sortOrder) {
        // Create a SQLite query builder that will be modified based
        // on the Uri passed.
        final SQLiteQueryBuilder queryBuilder =
                new SQLiteQueryBuilder();

        // Use the passed Uri to determine how to build the
        // query. This will determine the table that the query will
        // act on and possibly add row qualifications to the WHERE
        // clause.
        switch (WeatherContract.sUriMatcher.match(uri)) {
            case WeatherContract.WEATHER_VALUES_ITEMS:
                queryBuilder.setTables(WEATHER_VALUES_TABLE_NAME);
                break;
            case WeatherContract.WEATHER_VALUES_ITEM:
                queryBuilder.setTables(WEATHER_VALUES_TABLE_NAME);
                whereStatement =
                        addKeyIdCheckToWhereStatement(whereStatement,
                                ContentUris.parseId(uri));
                break;

            case WeatherContract.ACCESS_ALL_DATA_FOR_LOCATION_ITEM:
                // This is a special Uri that is querying for an entire
                // WeatherData object.
                return getAllLocationsData(whereStatementArgs[0]);
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // Once the query builder has been initialized based on the
        // provided Uri, use it to query the database.
        final Cursor cursor =
                queryBuilder.query(mDatabaseHelper.getReadableDatabase(),
                        projection,
                        whereStatement,
                        whereStatementArgs,
                        null,	// GROUP BY (not used)
                        null,	// HAVING   (not used)
                        sortOrder);

        // Register to watch a content URI for changes.
        cursor.setNotificationUri(getContext().getContentResolver(),
                uri);
        return cursor;
    }

    /**
     * Method called to handle type requests from client applications.
     * It returns the MIME type of the data associated with each URI.
     */
    @Override
    public String getType(Uri uri) {
        // Use the passed Uri to determine what data is being asked
        // for and return the appropriate MIME type
        switch (WeatherContract.sUriMatcher.match(uri)) {
            case WeatherContract.WEATHER_VALUES_ITEM:
                return WeatherContract.WeatherValuesEntry.WEATHER_VALUES_ITEM;
            default:
                throw new IllegalArgumentException("Unknown URI "
                        + uri);
        }
    }

    /**
     * Method called to handle insert requests from client
     * applications.
     */
    @Override
    public Uri insert(Uri uri,
                      ContentValues values) {
        // The table to perform the insert on.
        String table;

        // The Uri containing the inserted row's id that is returned
        // to the caller.
        Uri resultUri;

        // Determine the base Uri to return and the table to insert on
        // using the UriMatcher.
        switch (WeatherContract.sUriMatcher.match(uri)) {
            case WeatherContract.WEATHER_VALUES_ITEMS:
                table = WEATHER_VALUES_TABLE_NAME;
                resultUri =
                        WeatherContract.WeatherValuesEntry.WEATHER_VALUES_CONTENT_URI;
                break;
            default:
                throw new IllegalArgumentException("Unknown URI "
                        + uri);
        }
        // Insert the data into the correct table.
        final long insertRow =
                mDatabaseHelper.getWritableDatabase().insert
                        (table,
                                null,
                                values);

        // Check to ensure that the insertion worked.
        if (insertRow > 0) {
            // Create the result URI.
            Uri newUri = ContentUris.withAppendedId(resultUri,
                    insertRow);

            // Register to watch a content URI for changes.
            getContext().getContentResolver().notifyChange(newUri,
                    null);
            return newUri;
        } else
            throw new SQLException("Fail to add a new record into "
                    + uri);
    }


    /**
     * Method called to handle update requests from client
     * applications.
     */
    @Override
    public int update(Uri uri,
                      ContentValues values,
                      String whereStatement,
                      String[] whereStatementArgs) {
        // Number of rows updated.
        int rowsUpdated;

        final SQLiteDatabase db =
                mDatabaseHelper.getWritableDatabase();

        // Update the appropriate rows.  If the URI includes a
        // specific row to update, add that row to the where
        // statement.
        switch (WeatherContract.sUriMatcher.match(uri)) {
            case WeatherContract.WEATHER_VALUES_ITEMS:
                rowsUpdated =
                        db.update(WEATHER_VALUES_TABLE_NAME,
                                values,
                                whereStatement,
                                whereStatementArgs);
                break;
            case WeatherContract.WEATHER_VALUES_ITEM:
                rowsUpdated =
                        db.update(WEATHER_VALUES_TABLE_NAME,
                                values,
                                addKeyIdCheckToWhereStatement
                                        (whereStatement,
                                                ContentUris.parseId(uri)),
                                whereStatementArgs);
                break;

            default:
                throw new IllegalArgumentException("Unknown URI "
                        + uri);
        }

        // Register to watch a content URI for changes.
        getContext().getContentResolver().notifyChange(uri,
                null);

        return rowsUpdated;
    }

    /**
     * Method called to handle delete requests from client
     * applications.
     */
    @Override
    public int delete(Uri uri,
                      String whereStatement,
                      String[] whereStatementArgs) {
        // Number of rows deleted.
        int rowsDeleted;

        final SQLiteDatabase db =
                mDatabaseHelper.getWritableDatabase();

        // Delete the appropriate rows based on the Uri. If the URI
        // includes a specific row to delete, add that row to the
        // WHERE statement.
        switch (WeatherContract.sUriMatcher.match(uri)) {
            case WeatherContract.WEATHER_VALUES_ITEMS:
                rowsDeleted =
                        db.delete(WEATHER_VALUES_TABLE_NAME,
                                whereStatement,
                                whereStatementArgs);
                break;
            case WeatherContract.WEATHER_VALUES_ITEM:
                rowsDeleted =
                        db.delete(WEATHER_VALUES_TABLE_NAME,
                                addKeyIdCheckToWhereStatement
                                        (whereStatement,
                                                ContentUris.parseId(uri)),
                                whereStatementArgs);
                break;

            default:
                throw new IllegalArgumentException("Unknown URI "
                        + uri);
        }

        // Register to watch a content URI for changes.
        getContext().getContentResolver().notifyChange(uri,
                null);
        return rowsDeleted;
    }
}
