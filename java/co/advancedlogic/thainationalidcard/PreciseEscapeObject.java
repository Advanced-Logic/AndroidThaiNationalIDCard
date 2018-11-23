package co.advancedlogic.thainationalidcard;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedList;

public class PreciseEscapeObject {
    private static final String TAG = "PreciseEscapeObject";

    private byte command;
    private byte[] data = null;
    private LinkedList<PreciseEscapeObject> elements = null;

    public PreciseEscapeObject(byte command) {
        this.command = command;
        this.elements = new LinkedList<PreciseEscapeObject>();
    }

    public PreciseEscapeObject(byte command, byte[] data) {
        this.command = command;
        this.data = data;
    }

    public PreciseEscapeObject(byte command, PreciseEscapeObject element) {
        this.command = command;
        this.elements = new LinkedList<PreciseEscapeObject>();
        this.elements.add(element);
    }

    public PreciseEscapeObject(byte command, LinkedList<PreciseEscapeObject> elements) {
        this.command = command;
        this.elements = elements;
    }

    public byte getCommand() {
        return this.command;
    }

    public byte[] getData() {
        return this.data;
    }

    public LinkedList<PreciseEscapeObject> getElements() {
        if (this.data != null) {
            return null;
        }
        return this.elements;
    }

    public void addElement(PreciseEscapeObject element) {
        if (this.data != null) {
            return;
        }
        this.elements.add(element);
    }

    public byte[] serialize() {
        byte[] data, tmp;
        int length, index;

        if (this.data != null) {
            tmp = this.data;
        } else if (this.elements.size() > 0) {
            ByteArrayOutputStream dataStream = new ByteArrayOutputStream();

            for (PreciseEscapeObject element : this.elements) {
                if ((tmp = element.serialize()) == null || tmp.length == 0) {
                    Log.w(TAG, "PreciseEscapeObject serializing return failed");
                    try {
                        dataStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return null;
                }

                try {
                    dataStream.write(tmp);
                } catch (IOException e) {
                    Log.w(TAG, "PreciseEscapeObject serializing write to bytes buffer failed");
                    e.printStackTrace();
                    return null;
                }
            }
            tmp = dataStream.toByteArray();

            try {
                dataStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            Log.w(TAG, "PreciseEscapeObject serializing but data not found");
            return null;
        }

        length = tmp.length;
        if (length < 0x80) {
            data = new byte[(length + 2)];
            data[1] = (byte)length;
            index = 2;
        } else if (length <= 0xff) {
            data = new byte[(length + 3)];
            data[1] = (byte)0x81;
            data[2] = (byte)length;
            index = 3;
        } else if (length <= 0xffff) {
            data = new byte[(length + 4)];
            data[1] = (byte)0x82;
            data[2] = (byte)((length >> 8) & 0xff);
            data[3] = (byte)(length & 0xff);
            index = 4;
        } else if (length <= 0xffffff) {
            data = new byte[(length + 5)];
            data[1] = (byte)0x83;
            data[2] = (byte)((length >> 16) & 0xff);
            data[3] = (byte)((length >> 8) & 0xff);
            data[4] = (byte)(length & 0xff);
            index = 5;
        } else {
            Log.w(TAG, "PreciseEscapeObject serializing data length exceed");
            return null;
        }

        data[0] = this.command;
        System.arraycopy(tmp, 0, data, index, length);

        return data;
    }

    private static PreciseEscapeObject deserializePlainData(byte[] data) {
        byte[] buffer;
        int length;

        if (data == null) {
            Log.w(TAG, "Invalid data");
            return null;
        }

        length = (int)data[1];

        if (length < 0x80) {
            buffer = new byte[length];
            if (length > 0) {
                System.arraycopy(data, 2, buffer, 0, length);
            }

            return new PreciseEscapeObject(data[0], buffer);
        } else if (length == 0x81) {
            length = (int)data[2];
            buffer = new byte[length];
            if (length > 0) {
                System.arraycopy(data, 3, buffer, 0, length);
            }

            return new PreciseEscapeObject(data[0], buffer);
        } else if (length == 0x82) {
            length = (((int)data[2]) << 8) | (int)data[3];
            buffer = new byte[length];
            if (length > 0) {
                System.arraycopy(data, 4, buffer, 0, length);
            }

            return new PreciseEscapeObject(data[0], buffer);
        } else if (length == 0x83) {
            length = (((int)data[2]) << 16) | (((int)data[3]) << 8) | (int)data[4];
            buffer = new byte[length];
            if (length > 0) {
                System.arraycopy(data, 5, buffer, 0, length);
            }

            return new PreciseEscapeObject(data[0], buffer);
        } else {
            Log.w(TAG, "Invalid data length");
            return null;
        }
    }

    public static PreciseEscapeObject deserialize(byte[] data) {
        byte command;
        int length;

        if (data == null) {
            Log.w(TAG, "Invalid data");
            return null;
        }

        command = data[0];

        switch (command) {
            case (byte)0xc5:
                return PreciseEscapeObject.deserializePlainData(data);

            default:
                length = (int)data[1];

                if (length < 0x80) {
                    ///
                } else if (length == 0x81) {
                    ///
                } else if (length == 0x82) {
                    ///
                } else if (length == 0x83) {
                    ///
                } else {
                    Log.w(TAG, "Invalid data length");
                    return null;
                }
        }

        return null;
    }
}
