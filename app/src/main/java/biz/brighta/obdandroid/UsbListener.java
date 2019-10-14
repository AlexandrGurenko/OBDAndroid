package biz.brighta.obdandroid;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

import java.util.Objects;
import java.util.logging.Logger;

import static android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED;
import static android.hardware.usb.UsbManager.ACTION_USB_DEVICE_DETACHED;

class UsbListener {

    private static final String TAG = UsbListener.class.getSimpleName();
    private static final Logger log = Logger.getLogger(TAG);

    private static final String ACTION_USB_PERMISSION = "biz.brighta.obdandroid.action.USB_PERMISSION";

    private Context context;
    private  boolean needPermission;
    private OnUsbListener onUsbListener;
    private UsbDevice device = null;

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @SuppressLint("DefaultLocale")
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                synchronized (this) {
                    device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if(device == null)
                        return;
                    boolean hasPermission = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                    log.info(String.format("%s with vendor ID %d attached, has permission? %s",
                            device.getDeviceName(), device.getVendorId(), hasPermission ? "Yes" : "No"));

                    if(needPermission && !hasPermission) {
                        log.info("Request permission for " + device.getDeviceName());
                        PendingIntent pendingIntent = PendingIntent.getBroadcast(context,
                                0,
                                new Intent(ACTION_USB_PERMISSION),
                                PendingIntent.FLAG_ONE_SHOT);

                        ((UsbManager) Objects.requireNonNull(context.getSystemService(Context.USB_SERVICE))).requestPermission(device, pendingIntent);
                    }
                    else onUsbListener.onAttached(device);
                }
            }
            else if(ACTION_USB_DEVICE_DETACHED.equals(action)) {
                synchronized (this) {
                    device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    onUsbListener.onDetached(device);
                }
            }
            else if(ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    boolean hasPermission = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                    log.info(String.format("Received permission for %s, has permission? %s", device.getDeviceName(), hasPermission ? "Yes" : "No"));

                    if(hasPermission)
                        onUsbListener.onAttached(device);
                }
            }
        }
    };

    UsbListener(Context context, boolean needPermission, OnUsbListener onUsbListener) {
        this.context = context;
        this.needPermission = needPermission;
        this.onUsbListener = onUsbListener;

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_USB_DEVICE_ATTACHED);
        intentFilter.addAction(ACTION_USB_DEVICE_DETACHED);
        if(needPermission)
            intentFilter.addAction(ACTION_USB_PERMISSION);
        context.registerReceiver(usbReceiver, intentFilter);
    }

    void dispose() {
        context.unregisterReceiver(usbReceiver);
    }

    public interface OnUsbListener {
        void onAttached(UsbDevice device);
        void onDetached(UsbDevice device);
    }

}
