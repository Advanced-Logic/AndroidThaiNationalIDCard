package co.advancedlogic.thainationalidcard;

import android.util.Log;

public final class SmartCardMessage {
    private static final String TAG = "SmartCardMessage";

    private boolean isT1 = false;
    private int slotNumber = 0;
    private int sequence = 0x00;
    private int timeout = 0;
    private int exchangeLevel = 0;
    private int maxPacketSize = 64;

    public SmartCardMessage() {
    }

    public SmartCardMessage(boolean t1) {
        this.isT1 = t1;
    }

    public void setMessageTypeT1(boolean t1) {
        this.isT1 = t1;
    }

    public boolean setSlotNumber(int slotNumber) {
        if (slotNumber >= 0 && slotNumber <= 127) {
            this.slotNumber = slotNumber;
            return true;
        }

        Log.w(TAG, "Invalid slot number range");
        return false;
    }

    public void setMaxPacketSize(int size) {
        this.maxPacketSize = size;
    }

    private void initProperties(byte[] message, int type) {
        if (message == null) {
            Log.w(TAG, "message is null");
            return;
        }

        message[0] = (byte)type;
        message[5] = (byte)this.slotNumber;
        message[6] = (byte)this.sequence;
        message[7] = (byte)this.timeout;
        message[8] = (byte)this.exchangeLevel;
        message[9] = (byte)0x00;

        this.sequence++;
    }

    private void initMessageLength(byte[] message, int length) {
        if (message == null) {
            Log.w(TAG, "message is null");
            return;
        }

        message[1] = (byte)(length & 0xff);
        message[2] = (byte)((length >> 8) & 0xff);
        message[3] = (byte)((length >> 16) & 0xff);
        message[4] = (byte)((length >> 24) & 0xff);
    }

    public byte[] getMessageIccPowerOn() {
        byte[] message = new byte[10];

        this.initProperties(message,0x62);

        return message;
    }

    public byte[] getMessageIccPowerOff() {
        byte[] message = new byte[10];

        this.initProperties(message,0x63);

        return message;
    }

    public byte[] getMessageGetSlotStatus() {
        byte[] message = new byte[10];

        this.initProperties(message,0x65);

        return message;
    }

    public byte[] getMessageSlotReset() {
        byte[] message = new byte[10];

        this.initProperties(message,0x6d);

        return message;
    }

    public byte[][] getMessageXfrBlock(byte[] data) {
        byte[][] messages;
        byte[] block, dataTmp;
        int blockSize;
        byte xorSum = 0;

        if (this.isT1) {
            if (data.length > 254) {
                Log.w(TAG, "Invalid data block length");
                return null;
            }
            dataTmp = new byte[data.length + 4];
            dataTmp[2] = (byte)data.length;
            System.arraycopy(data, 0, dataTmp, 3, data.length);
            for (byte b: dataTmp) {
                xorSum ^= b;
            }
            dataTmp[dataTmp.length - 1] = xorSum;

            data = dataTmp;

            StringBuilder sb = new StringBuilder();
            for (byte b: data) {
                sb.append(String.format("%02x", (byte)b));
            }
            Log.d(TAG, "APDU T=1 [" + data.length + "][" + sb.toString() + "]");
        } else {
            StringBuilder sb = new StringBuilder();
            for (byte b: data) {
                sb.append(String.format("%02x", (byte)b));
            }
            Log.d(TAG, "APDU T=0 [" + data.length + "][" + sb.toString() + "]");
        }

        int blockCount = ((data.length + 10) / this.maxPacketSize) + ((((data.length + 10) % this.maxPacketSize) > 0) ? 1:0);

        messages = new byte[blockCount][];

        for (int i = 0; i < blockCount; i++) {
            blockSize = (((data.length + 10) - (i * this.maxPacketSize)) > this.maxPacketSize) ? this.maxPacketSize:((data.length + 10) - (i * this.maxPacketSize));
            block = new byte[blockSize];

            if (i == 0) {
                this.initProperties(block, 0x6f);
                this.initMessageLength(block, data.length);
                blockSize = ((data.length + 10) > this.maxPacketSize) ? (this.maxPacketSize - 10):data.length;

                System.arraycopy(data, 0, block, 10, blockSize);
            } else {
                blockSize = (((data.length + 10) - (i * this.maxPacketSize)) > this.maxPacketSize) ? this.maxPacketSize:((data.length + 10) - (i * this.maxPacketSize));

                System.arraycopy(data, (i * this.maxPacketSize) - 10, block, 0, blockSize);
            }

            messages[i] = block;
        }

        return messages;
    }

    public byte[] getMessageEscapeRequest(byte[] data) {
        byte[] messages;

        messages = new byte[data.length + 10];

        this.initProperties(messages, 0x6b);
        this.initMessageLength(messages, data.length);

        System.arraycopy(data, 0, messages, 10, data.length);

        return messages;
    }

    public DataBlock parseDataBlock(byte[] data) {
        int length;
        DataBlock dataBlock;

        if (data == null) {
            Log.w(TAG, "data is null");
            return null;
        }

        Log.d(TAG, "Parsing data length " + data.length);

        if (data[0] != (byte)0x80) {
            Log.w(TAG, String.format("Invalid data type [%02x]", (byte)data[0]));
            return null;
        }

        dataBlock = new DataBlock();

        length = (int)((((int)data[4] & 0xff) << 24) | (((int)data[3] & 0xff) << 16 ) | (((int)data[2] & 0xff) << 8) | ((int)data[1] & 0xff));

        if (data.length < length + 10) {
            Log.w(TAG, String.format("Invalid data length %d/%d", data.length, length + 10));
            return null;
        }

        dataBlock.status = (int)data[7];
        dataBlock.error = (int)data[8];

        if (length > 0) {
            if (data[10] == (byte)0x3b) {
                dataBlock.dataType = DataType.ATR;
            } else if (data[10] == (byte)0x61 || data[10] == (byte)0x6c) {
                dataBlock.dataType = DataType.APDU_HEADER;
            } else {
                dataBlock.dataType = DataType.APDU_DATA;
            }
        }

        dataBlock.data = new byte[length];

        System.arraycopy(data, 10, dataBlock.data, 0, length);

        return dataBlock;
    }

    public EscapeResponseBlock parseEscapeResponseBlock(byte[] data) {
        int length;
        EscapeResponseBlock escapeBlock;

        if (data == null) {
            Log.w(TAG, "escape is null");
            return null;
        }

        Log.d(TAG, "Parsing escape length " + data.length);

        if (data[0] != (byte)0x83) {
            Log.w(TAG, String.format("Invalid escape type [%02x]", (byte)data[0]));
            return null;
        }

        length = (int)((((int)data[4] & 0xff) << 24) | (((int)data[3] & 0xff) << 16 ) | (((int)data[2] & 0xff) << 8) | ((int)data[1] & 0xff));

        escapeBlock = new EscapeResponseBlock();

        escapeBlock.status = data[7];
        escapeBlock.error = data[8];

        escapeBlock.data = new byte[length];
        if (length > 0) {
            System.arraycopy(data, 10, escapeBlock.data, 0, length);
        }

        return escapeBlock;
    }

    public enum DataType {
        UNKNOWN,
        ATR,
        APDU_HEADER,
        APDU_DATA
    }

    public class DataBlock {
        public DataType dataType = DataType.UNKNOWN;
        public byte[] data = null;
        public int status = 0;
        public int error = 0;
    }

    public class EscapeResponseBlock {
        public byte[] data = null;
        public int status = 0;
        public int error = 0;
        public int rfu;
    }
}
