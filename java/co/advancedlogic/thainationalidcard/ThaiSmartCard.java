package co.advancedlogic.thainationalidcard;

        import android.util.Log;

public final class ThaiSmartCard {
    private final String TAG = "ThaiSmartCard";

    private SmartCardDevice device;

    public ThaiSmartCard(SmartCardDevice device) {
        if (device == null) {
            throw new NullPointerException("Invalid Device");
        }

        if (!device.isStarted()) {
            device.start();
        }

        this.device = device;
    }

    public boolean isInserted() {
        SmartCardMessage.DataBlock data;
        return ((data = this.device.getATR()) != null && data.dataType == SmartCardMessage.DataType.ATR && data.status == 0 && data.error == 0);
    }

    private SmartCardMessage.DataBlock getCardData(byte[] requestMessage) {
        SmartCardMessage.DataBlock data;
        byte[] dataRequestMessage = new byte[5];

        if (requestMessage == null) {
            Log.d(TAG, "Invalid request message");
            return null;
        }

        if ((data = this.device.sendAPDU(requestMessage)) == null) {
            Log.w(TAG, "APDU header request fail");
            return null;
        }

        if (data.status != 0 || data.error != 0) {
            Log.w(TAG, "APDU header response abnormal [" + data.status+ ":" + data.error + "]");
            return null;
        }

        if (data.data.length != 2) {
            Log.w(TAG, "APDU header response invalid length: " + data.data.length);
            return null;
        }

        if (data.data[0] == (byte)0x61) {
            dataRequestMessage[0] = (byte)0x00;
            dataRequestMessage[1] = (byte)0xc0;
            dataRequestMessage[2] = (byte)0x00;
            dataRequestMessage[3] = (byte)0x00;
        } else if (data.data[0] == (byte)0x6c) {
            System.arraycopy(requestMessage, 0, dataRequestMessage, 0, 4);
        } else {
            Log.w(TAG, String.format("APDU header response invalid code: %02x", (byte)data.data[0]));
            return null;
        }

        dataRequestMessage[4] = data.data[1];

        if ((data = this.device.sendAPDU(requestMessage)) == null) {
            Log.w(TAG, "APDU body request fail");
            return null;
        }

        return data;
    }

    private boolean selectAppletChipData() {
        byte[] requestMessage = new byte[]{(byte)0x00, (byte)0xa4, (byte)0x04, (byte)0x00};
        SmartCardMessage.DataBlock data;

        return ((data = this.getCardData(requestMessage)) != null && data.status == 0 && data.error == 0);
    }

    private boolean selectAppletStorageData() {
        byte[] requestMessage = new byte[]{(byte)0x00, (byte)0xa4, (byte)0x04, (byte)0x00, (byte)0x08, (byte)0xa0, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x54, (byte)0x48, (byte)0x00, (byte)0x01};
        SmartCardMessage.DataBlock data;

        return ((data = this.getCardData(requestMessage)) != null && data.status == 0 && data.error == 0);
    }

    private boolean selectAppletExtension() {
        byte[] requestMessage = new byte[]{(byte)0x00, (byte)0xa4, (byte)0x04, (byte)0x00, (byte)0x08, (byte)0xa0, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x84, (byte)0x06, (byte)0x00, (byte)0x02};
        SmartCardMessage.DataBlock data;

        return ((data = this.getCardData(requestMessage)) != null && data.status == 0 && data.error == 0);
    }

    private String byteArrayToHexString(byte[] input) {
        StringBuilder output;

        if (input == null) {
            return "";
        }

        output = new StringBuilder();

        for (byte b: input) {
            output.append(String.format("%02x", b));
        }

        return output.toString();
    }

    private String byteArrayToHexString(byte[] input, int index, int length) {
        byte[] selectBytes;

        if ((length + index) > input.length) {
            length = input.length - index;
        }

        selectBytes = new byte[length];

        System.arraycopy(input, index, selectBytes, 0, length);

        return this.byteArrayToHexString(selectBytes);
    }

    public String getCardID() {
        SmartCardMessage.DataBlock data;

        if (!this.selectAppletChipData()) {
            Log.d(TAG, "selectAppletChipData fail");
            return null;
        }

        byte[] requestMessage = new byte[]{(byte)0x80, (byte)0xca, (byte)0x9f, (byte)0x7f};

        if ((data = this.getCardData(requestMessage)) == null) {
            Log.d(TAG, "Get chip card information fail");
            return null;
        }

        if (data.status != 0 || data.error != 0 || data.data.length != 45) {
            Log.w(TAG, String.format("Invalid chip card information [%d][%d][%d][%s]", data.status, data.error, data.data.length, this.byteArrayToHexString(data.data)));
            return null;
        }

        return this.byteArrayToHexString(data.data, 13, 8);
    }
}
