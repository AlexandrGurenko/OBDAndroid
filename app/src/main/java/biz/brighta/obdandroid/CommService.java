package biz.brighta.obdandroid;

import android.content.Context;
import android.widget.Toast;

import java.util.logging.Logger;

public abstract class CommService {

    static final Logger log = Logger.getLogger(CommService.class.getSimpleName());

    Context context = null;

    private CommService() {
        super();
    }


    CommService(Context context) {
        this();
        this.context = context;
    }

    protected abstract void start();

    protected abstract void stop();

    public abstract void write(byte[] data);

    public abstract void connect(Object device);

    void connectionFailed() {
        log.info("connectionFailed()");

        stop();
        Toast.makeText(context, "Unable to connection device", Toast.LENGTH_SHORT).show();
    }

    void connectionLost() {
        log.info("connectionLost()");

        stop();
        Toast.makeText(context, "Device connection was lost", Toast.LENGTH_SHORT).show();
    }

    void connectionEstablished(String deviceName) {
        log.info("connectionEstablished()");

        Toast.makeText(context, deviceName, Toast.LENGTH_SHORT).show();
    }

}
