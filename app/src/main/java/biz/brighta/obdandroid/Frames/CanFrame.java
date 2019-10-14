package biz.brighta.obdandroid.Frames;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class CanFrame {

    private short id;
    private byte[] data;

    private CanFrame() {

    }

    public CanFrame(short id, byte[] data) {
        this();
        this.id = id;
        this.data = new byte[data.length];
        System.arraycopy(data, 0, this.data, 0, data.length);
    }

    public short getId() {
        return id;
    }

    public void setId(short id) {
        this.id = id;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = new byte[data.length];
        System.arraycopy(data, 0, this.data, 0, data.length);
    }

    public byte[] getBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(2 + data.length);
        buffer.putShort(id);
        buffer.put(data);
        return buffer.array();
    }

    @NonNull
    @Override
    public String toString() {
        return "[ID: " + id + " , Data: " + Arrays.toString(this.data) + "]";
    }
}
