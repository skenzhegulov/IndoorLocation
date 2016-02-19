package saken.usis.indoorlocation;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;

import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.api.GoogleApiClient;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer;
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;

import saken.usis.indoorlocation.Trilateration.NonLinearLeastSquaresSolver;
import saken.usis.indoorlocation.Trilateration.TrilaterationFunction;

/**
 * Indoor Location:
 * Finds user's approximate location using iBeacons.
 * At least 3 beacons needed.
 * Designed for rectangular rooms without walls.
 * <p/>
 * Trilateration algorithm for N points and Kalman Filter used to calculate the user's location.
 * <p/>
 * Implementation is still far from being accurate.
 * Needs more calibration of constants and some optimization.
 * <p/>
 * Used different implementation methods, that's why some code lines are commented.
 * <p/>
 * Trilateration algorithm for n points is referenced from: https://github.com/lemmingapex/Trilateration
 **/

public class MainActivity extends AppCompatActivity implements BeaconConsumer {

    protected static final String TAG = "Indoor";
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    //BeaconManager handles all operations realated with Beacons
    private BeaconManager beaconManager;
    //ArrayLists to store detected and added beacons
    ArrayList<Beacon> mBeacons;
    ArrayList<String> aBeacons;
    ArrayList<KalmanFilter> RSSI;
    //Kalman Filter for user location calculation noises
    KalmanFilter x, y;
    View view;
    //Room dimensions
    int height;
    int width;
    //Distance calibration constant
    double n;
    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    private GoogleApiClient client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        view = findViewById(R.id.room);
        //Get Room's data
        getData();

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        //Beacon Manager initialize
        beaconManager = BeaconManager.getInstanceForApplication(this);
        //Verify bluetooth on mobile device
        verifyBluetooth();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android M Permission check
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("This app needs location access");
                builder.setMessage("Please grant location access so this app can detect beacons in the background.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

                    @TargetApi(23)
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                                PERMISSION_REQUEST_COARSE_LOCATION);
                    }

                });
                builder.show();
            }
        }
        //Initialization of variables
        mBeacons = new ArrayList<>();
        aBeacons = new ArrayList<>();
        RSSI = new ArrayList<>();
        n = 2.0;
        x = new KalmanFilter(0, 0.1, 0.01);
        y = new KalmanFilter(0, 0.1, 0.01);

        //Adding beacon parser according beacon specifications
        beaconManager.getBeaconParsers().clear();
        beaconManager.getBeaconParsers().add(new BeaconParser().
                setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"));

        beaconManager.bind(this);
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.add_beacon) {
            showDialog();
            return true;
        } else if (id == R.id.clear_beacons) {
            Room.clear();
            aBeacons.clear();
            view.postInvalidate();
            return true;
        } else if (id == R.id.change_data) {
            getData();
        } else if (id == R.id.change_n) {
            changeN();
        }

        return super.onOptionsItemSelected(item);
    }

    //Method used to show Dialog Window to get new constant 'n' used for distance calculation
    private void changeN() {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        final EditText edittext = new EditText(MainActivity.this);
        edittext.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        alert.setTitle("Change n");

        alert.setView(edittext);

        alert.setPositiveButton("Change", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                String s = edittext.getText().toString();
                n = Double.parseDouble(s);
            }
        });

        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                // what ever you want to do with No option.
            }
        });

        alert.show();
    }

    //Method used to show Dialog Window to get or change Room's dimensions
    private void getData() {
        final Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog);
        dialog.setTitle(R.string.dialog_title);
        dialog.setCanceledOnTouchOutside(true);

        dialog.show();

        Button btn = (Button) dialog.findViewById(R.id.btn_ok);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText text = (EditText) dialog.findViewById(R.id.length);
                String s = text.getText().toString();
                height = Integer.parseInt(s);
                text = (EditText) dialog.findViewById(R.id.width);
                s = text.getText().toString();
                width = Integer.parseInt(s);
                dialog.dismiss();
            }
        });

    }

    //Method used to show list of detected beacons, and add them by placing onto the screen
    private void showDialog() {
        final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(
                MainActivity.this,
                android.R.layout.select_dialog_singlechoice);

        for (Beacon b : mBeacons) {
            int index = aBeacons.indexOf(b.getBluetoothAddress());
            if (index == -1) arrayAdapter.add(b.getBluetoothAddress());
        }

        new AlertDialog.Builder(MainActivity.this)
                .setAdapter(arrayAdapter, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //TODO - Code when list item is clicked (int which - is param that gives you the index of clicked item)
                        String name = arrayAdapter.getItem(which);
                        Room.setEdit();
                        aBeacons.add(name);
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setTitle("Choose a beacon")
                .setCancelable(false)
                .create()
                .show();
    }

    //Checks Bluetooth Permission
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "coarse location permission granted");
                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Functionality limited");
                    builder.setMessage("Since location access has not been granted, this app will not be able to discover beacons when in the background.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

                        @Override
                        public void onDismiss(DialogInterface dialog) {
                        }

                    });
                    builder.show();
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        beaconManager.unbind(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (beaconManager.isBound(this)) beaconManager.setBackgroundMode(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (beaconManager.isBound(this)) beaconManager.setBackgroundMode(false);
    }

    //Method which detects beacons and calls location updater from the background
    @Override
    public void onBeaconServiceConnect() {
        beaconManager.setRangeNotifier(new RangeNotifier() {
            @Override
            public void didRangeBeaconsInRegion(final Collection<Beacon> beacons, Region region) {
                if (beacons.size() > 0) {
                    for (Beacon b : beacons) {
                        addBeacon(b);
                    }
                }
                if (Room.points.size() > 2 && aBeacons.size() == Room.points.size()) {
                    Loc();
                }
            }
        });

        try {
            beaconManager.startRangingBeaconsInRegion(new Region("myRangingUniqueId", null, null, null));
        } catch (RemoteException e) {
            Log.d(TAG, "Exception caught in ServiceConnect");
        }
    }

    //Method to add a new or update already added beacon's RSSI
    void addBeacon(Beacon beacon) {
        int index = -1;
        int i = 0;
        for (Beacon b : mBeacons) {
            if (b.getBluetoothAddress().equals(beacon.getBluetoothAddress())) {
                index = i;
                break;
            }
            i++;
        }
        if (index == -1) {
            mBeacons.add(beacon);
            RSSI.add(new KalmanFilter(beacon.getRssi(), 7, 0.05));
        } else {
            RSSI.get(index).Filter(beacon.getRssi());
            mBeacons.get(index).setRssi((int) RSSI.get(index).get());
        }
    }

    //Method to calculate beacon's distance
    protected Double dist(int rssi, int txPower) {
    /*
     * RSSI = TxPower - 10 * n * lg(d)
     * n = 2 (in free space)
     *
     * d = 10 ^ ((TxPower - RSSI) / (10 * n))
     */
        return Math.pow(10d, ((double) txPower - rssi) / (10 * n));
    }

    public class CustomComparator implements Comparator<Circle> {
        @Override
        public int compare(Circle o1, Circle o2) {
            return o1.r.compareTo(o2.r);
        }
    }

    //Method to calculate approximate location which uses trilateration algorithm for n points
    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void Loc() {
        //Working with the data
        ArrayList<Circle> c = new ArrayList<>();
        Double rW = width * 1. / view.getWidth();
        Double rH = height * 1. / view.getHeight();
        for (Beacon b : mBeacons) {
            int index = aBeacons.indexOf(b.getBluetoothAddress());
            if (index != -1) {
                c.add(new Circle(Room.points.get(index).x * rW, Room.points.get(index).y * rH, dist(b.getRssi(), b.getTxPower())));
            }
        }

        double[][] positions = new double[c.size()][];
        double[] distances = new double[c.size()];

        for (int i = 0; i < c.size(); i++) {
            positions[i] = new double[]{c.get(i).x, c.get(i).y};
            distances[i] = c.get(i).r;
        }
        //Calculating approximate coordinates according to the data
        NonLinearLeastSquaresSolver solver = new NonLinearLeastSquaresSolver(new TrilaterationFunction(positions, distances), new LevenbergMarquardtOptimizer());
        LeastSquaresOptimizer.Optimum optimum = solver.solve();

        double[] centroid = optimum.getPoint().toArray();
        double X = centroid[0];
        double Y = centroid[1];


        //Updating User location
        Room.X = (int) Math.round(X / rW);
        Room.X = Math.max(0, Room.X);
        Room.X = Math.min(view.getWidth(), Room.X);
        Room.Y = (int) Math.round(Y / rH);
        Room.Y = Math.max(0, Room.Y);
        Room.Y = Math.min(view.getHeight(), Room.Y);

        Collections.sort(c, new CustomComparator());
        //Another methods to calculate approximate location but not accurate and uses only 3 points

        /*
        double xa = c.get(0).x, xb = c.get(1).x, xc = c.get(2).x, ya = c.get(0).y, yb = c.get(1).y, yc = c.get(2).y, ra = c.get(0).r, rb = c.get(1).r, rc = c.get(2).r;
        double S = (Math.pow(xc, 2.) - Math.pow(xb, 2.) + Math.pow(yc, 2.) - Math.pow(yb, 2.) + Math.pow(rb, 2.) - Math.pow(rc, 2.)) / 2.0;
        double T = (Math.pow(xa, 2.) - Math.pow(xb, 2.) + Math.pow(ya, 2.) - Math.pow(yb, 2.) + Math.pow(rb, 2.) - Math.pow(ra, 2.)) / 2.0;
        Y = ((T * (xb - xc)) - (S * (xb - xa))) / (((ya - yb) * (xb - xc)) - ((yc - yb) * (xb - xa)));
        X = ((Y * (ya - yb)) - T) / (xb - xa);
       */

        /*
        double top = 0;
        double bot = 0;
        for (int i=0; i<3; i++) {
            Circle c1 = c.get(i);
            Circle c2, c3;
            if (i==0) {
                c2 = c.get(1);
                c3 = c.get(2);
            }
            else if (i==1) {
                c2 = c.get(0);
                c3 = c.get(2);
            }
            else {
                c2 = c.get(0);
                c3 = c.get(1);
            }

            double d = c2.x - c3.x;
            double v1 = (c1.x * c1.x + c1.y * c1.y) - (c1.r * c1.r);
            top += d*v1;
            double v2 = c1.y * d;
            bot += v2;

        }

        Y = top / (2*bot);
        Circle c1 = c.get(0);
        Circle c2 = c.get(1);
        top = c2.r*c2.r+c1.x*c1.x+c1.y*c1.y-c1.r*c1.r-c2.x*c2.x-c2.y*c2.y-2*(c1.y-c2.y)*Y;
        bot = c1.x-c2.x;
        X = top / (2*bot);
        */

        //Updating User location for 3-points methods
        /*
        Room.currX = (int) Math.round(X/rW);
        Room.currX = Math.max(0, Room.currX);
        Room.currX = Math.min(view.getWidth(), Room.currX);
        Room.currY = (int) Math.round(Y/rH);
        Room.currY = Math.max(0, Room.currY);
        Room.currY = Math.min(view.getHeight(), Room.currY);
        */

        //Updating the view
        view.postInvalidate();
    }

    @Override
    public void onStart() {
        super.onStart();
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client.connect();
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Main Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app deep link URI is correct.
                Uri.parse("android-app://saken.usis.indoorlocation/http/host/path")
        );
        AppIndex.AppIndexApi.start(client, viewAction);
    }

    @Override
    public void onStop() {
        super.onStop();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Main Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app deep link URI is correct.
                Uri.parse("android-app://saken.usis.indoorlocation/http/host/path")
        );
        AppIndex.AppIndexApi.end(client, viewAction);
        client.disconnect();
    }

    private void verifyBluetooth() {

        try {
            if (!BeaconManager.getInstanceForApplication(this).checkAvailability()) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Bluetooth not enabled");
                builder.setMessage("Please enable bluetooth in settings and restart this application.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        finish();
                        System.exit(0);
                    }
                });
                builder.show();
            }
        } catch (RuntimeException e) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Bluetooth LE not available");
            builder.setMessage("Sorry, this device does not support Bluetooth LE.");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

                @Override
                public void onDismiss(DialogInterface dialog) {
                    finish();
                    System.exit(0);
                }

            });
            builder.show();

        }

    }
}
