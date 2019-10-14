package biz.brighta.obdandroid;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import biz.brighta.obdandroid.Frames.CanFrame;
import biz.brighta.obdandroid.utils.HexData;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final Logger rootLogger = Logger.getLogger("");
    private final Logger log = Logger.getLogger(getClass().getSimpleName());

    public static final long EXIT_TIMEOUT = 2500;
    private static long lastBackPressTime = 0;
    private static final String ACTION_USB_PERMISSION = "com.google.android.HID.action.USB_PERMISSION";

    private static Menu menu;
    private static SharedPreferences preferences;

    private UsbManager usbManager;
    private UsbDeviceConnection connection;
    private UsbSerialPort port;
    private UsbListener usbListener;
    private CommService commService;
    static Thread thread;

    private final List<UsbSerialPort> entries = new ArrayList<>();


    Button rpmBtn, tempBtn, pidListBtn;
    TextView tvDtc, tvConnectionState;

    private boolean isConnectToECU;
    private boolean isConnectedToAdapter;
    private boolean isAdapterConnect;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        rpmBtn = findViewById(R.id.rpmBtn);
        rpmBtn.setOnClickListener(this);
        tempBtn = findViewById(R.id.tempBtn);
        tempBtn.setOnClickListener(this);
        pidListBtn = findViewById(R.id.pidListBtn);
        pidListBtn.setOnClickListener(this);
        tvDtc = findViewById(R.id.tvDtc);
        tvConnectionState = findViewById(R.id.connectionState);
        tvConnectionState.setText(getString(R.string.not_connected));

        connection = null;
        port = null;
        usbListener = null;
        commService = null;
        thread = null;
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        setupLoggers();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            final int REQUEST_EXTERNAL_STORAGE = 1;
            final String[] PERMISSION_STORAGE = {
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            };

            requestPermissions(PERMISSION_STORAGE, REQUEST_EXTERNAL_STORAGE);

            StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
            StrictMode.setVmPolicy(builder.build());
        }

        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener =
                new SharedPreferences.OnSharedPreferenceChangeListener() {
                    @Override
                    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {

                    }
                };
        preferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener);

        usbListener = new UsbListener(this, true, new UsbListener.OnUsbListener() {
            @Override
            public void onAttached(UsbDevice device) {
                log.info("onAttached: " + device.getDeviceName());

                isAdapterConnect = true;
                menu.findItem(R.id.connect).setEnabled(true);
                menu.findItem(R.id.disconnect).setEnabled(false);
            }

            @Override
            public void onDetached(UsbDevice device) {
                log.info("onDetached: " + device.getDeviceName());

                if (thread != null) {
                    thread.interrupt();
                    thread = null;
                }
                isAdapterConnect = false;
                isConnectedToAdapter = false;
                menu.findItem(R.id.connect).setEnabled(false);
                menu.findItem(R.id.disconnect).setEnabled(false);

                if (commService != null) {
                    commService.stop();
                    isConnectedToAdapter = false;
                    isConnectToECU = false;
                    tvConnectionState.setText(R.string.not_connected);
                    enableButtons(false);
                    menu.findItem(R.id.connect).setEnabled(true);
                    menu.findItem(R.id.disconnect).setEnabled(false);
                }
            }
        });

        isConnectToECU = false;
        isAdapterConnect = false;

        enableButtons(false);

        log.info(getString(R.string.app_name).concat(" starting"));
    }

    @Override
    public void onClick(View view) {

        final short diagnosticID = PIDs.PID_REQUEST;
        CanFrame canFrame;

        port = UsbCommService.serialPort;

        if (thread == null) {
            thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    synchronized (this) {
                        final ByteBuffer buffer = ByteBuffer.allocate(8);
                        buffer.putShort((short) 0x300);
                        buffer.put(new byte[]{(byte) 0xA1, 0x0F, (byte) 0x8A, (byte) 0xFF, 0x4A, (byte) 0xFF});
                        final byte a = (byte) 0xA3;
                        final byte[] b = new byte[8];
                        try {

                            do {
                                try {
                                    log.info("" + a);
                                    log.info(Arrays.toString(b));
                                    port.write(new byte[]{a}, 500);
                                    port.read(b, 500);
                                    synchronized (thread) {
                                        thread.wait(500);
                                    }
                                } catch (IOException e) {
                                    log.info("Loop error IO: " + e.getMessage());
                                } catch (InterruptedException e) {
                                    log.info("Loop error Interrupted: " + e.getMessage());
                                }
                            } while (thread != null);
                        } catch (Exception e) {
                            log.info(e.getMessage());
                        }
                    }
                }
            });
            thread.start();
        }
        switch (view.getId()) {
            case R.id.rpmBtn:
                byte[] requestRPM = new byte[]{0x02, 0x01, PIDs.ENGINE_RPM, 0x00, 0x00, 0x00, 0x00, 0x00};
                canFrame = new CanFrame(diagnosticID, requestRPM);
                readWrite(canFrame, "RPM");
                break;
            case R.id.tempBtn:
                byte[] requestTemp = new byte[]{0x02, 0x01, PIDs.ENGINE_COOLANT_TEMP, 0x00, 0x00, 0x00, 0x00, 0x00};
                canFrame = new CanFrame(diagnosticID, requestTemp);
                readWrite(canFrame, "Temperature");
                break;
            case R.id.pidListBtn:
                byte[] requestPID = new byte[]{0x02, 0x01, PIDs.PID_0_20, 0x00, 0x00, 0x00, 0x00, 0x00};
                canFrame = new CanFrame(diagnosticID, requestPID);
                readWrite(canFrame, "PID List");
                break;
            default:
                break;
        }
    }

    @SuppressLint("SetTextI18n")
    private void setTvDtc(String s, byte[] data) {
        if (data.length > 0)
            tvDtc.setText(s + HexData.hexToString(data));
        else
            tvDtc.setText(s + " : Read nothing");
    }

    private void readWrite(CanFrame canFrame, String requestName) {
        if (UsbCommService.serialPort == null)
            connect();

        port = UsbCommService.serialPort;

        log.info("Click " + requestName);
        try {
            byte[] buffer = new byte[32];

            log.info("Send request... " + HexData.hexToString(canFrame.getBytes()));
            port.write(canFrame.getBytes(), 500);

            log.info("Send request OK");
            int numBytesRead = port.read(buffer, 500);

            log.info("Read " + numBytesRead + " bytes");

            byte[] data = new byte[numBytesRead];
            if (numBytesRead > 0) {
                System.arraycopy(buffer, 0, data, 0, numBytesRead);
                setTvDtc(requestName, data);
            } else
                setTvDtc(requestName, data);

        } catch (IOException e) {
            log.info(requestName + " Err: " + e.getMessage());
            e.printStackTrace();
        }
    }

    void showListOfDevice(final Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        if (usbManager.getDeviceList().isEmpty()) {
            builder.setTitle(R.string.usb_connect_title)
                    .setPositiveButton(R.string.ok, null);
        } else {
            builder.setTitle(R.string.usb_select_title);
            List<CharSequence> list = new LinkedList<>();
            for (UsbDevice usbDevice : usbManager.getDeviceList().values()) {
                list.add("Device ID: " + usbDevice.getDeviceId()
                        + " VID: " + Integer.toHexString(usbDevice.getVendorId())
                        + " PID: " + Integer.toHexString(usbDevice.getProductId()) + " "
                        + usbDevice.getDeviceName());
            }
            final CharSequence[] deviceName = new CharSequence[usbManager.getDeviceList().size()];
            list.toArray(deviceName);
            builder.setItems(deviceName, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    UsbDevice device = (UsbDevice) usbManager.getDeviceList().values().toArray()[which];
                    usbManager.requestPermission(device, PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), 0));
                }
            });
        }
        builder.setCancelable(true);
        builder.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        MainActivity.menu = menu;
        menu.findItem(R.id.connect).setEnabled(true);
        menu.findItem(R.id.disconnect).setEnabled(false);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.connect:
                connect();
                return true;
            case R.id.disconnect:
                disconnect();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void connect() {
        updateDeviceList();
        if (!isConnectedToAdapter) {
            try {
                if (!entries.isEmpty()) {
                    commService = new UsbCommService(this);
                    commService.connect(entries.get(0)); // select the first device
                    enableButtons(true);
                    isConnectedToAdapter = true;
                    tvConnectionState.setText(R.string.connected);
                    menu.findItem(R.id.connect).setEnabled(false);
                    menu.findItem(R.id.disconnect).setEnabled(true);
                } else {
                    toastShort("No devices found");
                    log.info("No devices found");
                }
            } catch (Exception e) {
                log.info(e.getMessage());
            }
        }
    }

    private void disconnect() {
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }

        if (commService != null) {
            commService.stop();
            isConnectedToAdapter = false;
            isConnectToECU = false;
            tvConnectionState.setText(R.string.not_connected);
            tvDtc.setText(null);
            enableButtons(false);
            menu.findItem(R.id.connect).setEnabled(true);
            menu.findItem(R.id.disconnect).setEnabled(false);
        }
    }

    private void enableButtons(boolean isEnabled) {
        rpmBtn.setEnabled(isEnabled);
        tempBtn.setEnabled(isEnabled);
        pidListBtn.setEnabled(isEnabled);
    }

    @Override
    public void onBackPressed() {
        if (lastBackPressTime + EXIT_TIMEOUT > System.currentTimeMillis())
            super.onBackPressed();
        else
            Toast.makeText(this, "Press back again to close this app", Toast.LENGTH_SHORT).show();
        lastBackPressTime = System.currentTimeMillis();
    }

    private void updateDeviceList() {
        log.info("Refreshing device list...");
        final List<UsbSerialDriver> drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        final List<UsbSerialPort> result = new ArrayList<>();

        for (final UsbSerialDriver driver : drivers) {
            connection = usbManager.openDevice(driver.getDevice());
            port = driver.getPorts().get(0);
            final List<UsbSerialPort> ports = driver.getPorts();
            log.info(String.format("+ %s: %s selected port %s", driver, ports.size(), ports.size() == 1 ? "" : "s"));
            result.addAll(ports);
        }

        entries.clear();
        entries.addAll(result);
        log.info("Find " + result.size() + " device(s)");
        log.info("Done Refreshing, " + entries.size() + " entries found.");
    }

    private void toastShort(String s) {
        if (!s.isEmpty())
            Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private void setupLoggers() {
        String logFilename = Environment.getExternalStorageDirectory()
                + File.separator
                + this.getApplicationContext().getPackageName().concat(File.separator).concat("log");
        try {
            new File(logFilename).mkdirs();
            FileHandler logFileHandler = new FileHandler(logFilename.concat("/OBDAndroid.log.%d.txt"),
                    250 * 1024 * 1024,
                    5,
                    false);

            logFileHandler.setFormatter(new SimpleFormatter() {
                final String format = "%1$tF\t%1$tT.%1$tL\t%4$s\t%3$s\t%5$s%n";

                @SuppressLint("DefaultLocale")
                @Override
                public synchronized String format(LogRecord lr) {
                    return String.format(format,
                            new Date(lr.getMillis()),
                            lr.getSourceClassName(),
                            lr.getLoggerName(),
                            lr.getLevel().getName(),
                            lr.getMessage());
                }
            });

            rootLogger.addHandler(logFileHandler);

        } catch (IOException e) {
            log.info(e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        log.info("onDestroy()");
        usbListener.dispose();
        if (port != null) {
            try {
                port.close();
            } catch (IOException e) {
                log.info("Try to close the port failed");
                e.printStackTrace();
            }
        }
        super.onDestroy();
    }
}
