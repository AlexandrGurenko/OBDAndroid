package biz.brighta.obdandroid;

import android.content.Context;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;

import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;


public class UsbCommService extends CommService {

    static UsbSerialPort serialPort = null;
    private SerialInputOutputManager serialInputOutputManager;

    UsbCommService(Context context) {
        super(context);
    }

    private void setDevice(UsbSerialPort port) {
        serialPort = port;
        serialInputOutputManager = new SerialInputOutputManager(serialPort);
    }

    @Override
    public void connect(Object device) {
        setDevice((UsbSerialPort) device);
        start();
    }

    @Override
    public void start() {
        if(serialPort != null){
            UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
            UsbDeviceConnection connection;
            connection = Objects.requireNonNull(usbManager).openDevice(serialPort.getDriver().getDevice());
            log.info("start()");
            if(connection == null){
                log.info("connection == null");
                connectionFailed();
                return;
            }

            try{
                log.info("serialPort.open()...");
                serialPort.open(connection);
                log.info("serialPort is open");
                log.info("setDTR...");
                serialPort.setDTR(true);
                log.info("setDTR -> Ok");
                log.info("setParameters...");
                serialPort.setParameters(500000, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
                log.info("setParameters -> Ok");
                log.info("Starting IO Manger ...");

                Thread runner = new Thread(serialInputOutputManager);
                runner.setPriority(Thread.MAX_PRIORITY);
                runner.start();
                log.info("Thread started");

                connectionEstablished("Connected to " + usbManager.toString());
                log.info(serialPort.toString());
            }
            catch (IOException e) {
                log.log(Level.SEVERE, "Error setting up device: " + e.getMessage(), e);
                try {
                    serialPort.close();
                }
                catch (IOException err){
                    log.info("Error serialPort.close(): " + err.getMessage());
                }
                connectionFailed();
                serialPort = null;
            }
        }
    }

    @Override
    public void stop() {
        log.info("stop()");
        if(serialInputOutputManager != null) {
            log.info("Stopping IO Manager ...");
            serialInputOutputManager.stop();
            serialInputOutputManager = null;
        }
    }

    @Override
    public void write(byte[] data) {
        try {
            serialInputOutputManager.writeAsync(data);
        }
        catch (Exception e){
            log.log(Level.INFO, "Write error", e);
            connectionLost();
        }
    }

}
