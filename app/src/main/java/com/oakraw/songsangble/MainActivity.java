package com.oakraw.songsangble;

import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import com.melnykov.fab.FloatingActionButton;
import com.oakraw.songsangble.adapter.LeDeviceListAdapter;
import com.oakraw.songsangble.utils.Log;

import java.util.List;

import fr.castorflex.android.smoothprogressbar.SmoothProgressBar;
import fr.castorflex.android.smoothprogressbar.SmoothProgressBarUtils;
import fr.castorflex.android.smoothprogressbar.SmoothProgressDrawable;


public class MainActivity extends Activity {

    private int scanRes = android.R.drawable.stat_notify_sync_noanim;
    private int stopRes = android.R.drawable.ic_menu_close_clear_cancel;

    private LeDeviceListAdapter mLeDeviceListAdapter;
    private BluetoothAdapter mBluetoothAdapter;
    private boolean mScanning;
    private Handler mHandler;

    private static final int REQUEST_ENABLE_BT = 1;
    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;
    private ListView listView;
    private SmoothProgressBar progressBar;
    private FloatingActionButton fab;
    private String mDeviceAddress;


    private BluetoothLeService mBluetoothLeService;
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            Log.d("oak connect");
            if (!mBluetoothLeService.initialize()) {
                Log.e("Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
            mBluetoothLeService.setBLEServiceCb(mDCServiceCb);
            Toast.makeText(getApplicationContext(),"Connected",Toast.LENGTH_SHORT).show();

        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d("oak disconnect");

            mBluetoothLeService = null;
            Toast.makeText(getApplicationContext(),"Disconnected",Toast.LENGTH_SHORT).show();

        }
    };

    private DCServiceCb mDCServiceCb = new DCServiceCb();
    private long selectedId = -1;
    private boolean isServiceBind = false;

    public class DCServiceCb implements BluetoothLeService.BLEServiceCallback {

        @Override
        public void displayRssi(final int rssi) {
           /* runOnUiThread(new Runnable() {
                              @Override
                              public void run() {
                                  DeviceControlActivity.this.displayRssi(String.valueOf(rssi));
                              }
                          }
            );*/

        }

        @Override
        public void displayData(final String data) {
           /* runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    DeviceControlActivity.this.displayData(data);
                }
            });*/

        }

        @Override
        public void notifyConnectedGATT() {
           /* runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mConnected = true;
                    updateConnectionState(R.string.connected);
                    invalidateOptionsMenu();
                }
            });*/

        }

        @Override
        public void notifyDisconnectedGATT() {
            /*runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mConnected = false;
                    updateConnectionState(R.string.disconnected);
                    invalidateOptionsMenu();
                    clearUI();
                }
            });*/
        }

        @Override
        public void displayGATTServices() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mBluetoothLeService != null) {
                        MainActivity.this.displayGattServices(
                                mBluetoothLeService.getSupportedGattServices());
                    }
                }
            });
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listView = (ListView)findViewById(R.id.list);
        fab = (FloatingActionButton)findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mScanning) {
                   scanLeDevice(true);
                } else {
                  scanLeDevice(false);
                }
            }
        });
        progressBar = (SmoothProgressBar)findViewById(R.id.progressbar);
        progressBar.setSmoothProgressDrawableBackgroundDrawable(
                SmoothProgressBarUtils.generateDrawableWithColors(
                        getResources().getIntArray(R.array.pocket_background_colors),
                        ((SmoothProgressDrawable) progressBar.getIndeterminateDrawable()).getStrokeWidth()));

        mHandler = new Handler();

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }


        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }

        // Initializes list view adapter.
        mLeDeviceListAdapter = new LeDeviceListAdapter(this);
        listView.setAdapter(mLeDeviceListAdapter);
        scanLeDevice(true);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final BluetoothDevice device = mLeDeviceListAdapter.getDevice(position);
                if (device == null) return;

                mDeviceAddress = device.getAddress();

                Log.d(selectedId+" "+ id);


                if(id != selectedId) {

                    Log.d("start_connect");

                    if(isServiceBind){
                        unbindService(mServiceConnection);
                    }
                    Log.d(device.getName() + " " + device.getAddress());
                    Intent gattServiceIntent = new Intent(getApplicationContext(), BluetoothLeService.class);
                    bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
                    isServiceBind = true;
                    selectedId = id;
                    for(int i =0; i< parent.getChildCount();i++){
                        parent.getChildAt(i).setBackgroundColor(getResources().getColor(android.R.color.transparent));
                    }

                    view.setBackgroundColor(getResources().getColor(R.color.gray));

                }else{
                    Log.d("stop_connect");

                    unbindService(mServiceConnection);
                    isServiceBind = false;
                    selectedId = -1;
                    view.setBackgroundColor(getResources().getColor(android.R.color.transparent));

                }






            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onDestroy() {
        super.onPause();
        scanLeDevice(false);
        mLeDeviceListAdapter.clear();
    }


    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    progressBar.setVisibility(View.INVISIBLE);
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    fab.setImageResource(scanRes);
                }
            }, SCAN_PERIOD);

            mScanning = true;
            mBluetoothAdapter.startLeScan(mLeScanCallback);
            progressBar.setVisibility(View.VISIBLE);
            fab.setImageResource(stopRes);

        } else {
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
            progressBar.setVisibility(View.INVISIBLE);
            fab.setImageResource(scanRes);
        }
    }


    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {

                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                    Log.e("Scan device rssi is " + rssi);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mLeDeviceListAdapter.addDevice(device);
                            mLeDeviceListAdapter.notifyDataSetChanged();
                        }
                    });
                }
            };

    private void displayGattServices(List<BluetoothGattService> gattServices){
        gattServices.toString();
        int x=2;
    }


}
