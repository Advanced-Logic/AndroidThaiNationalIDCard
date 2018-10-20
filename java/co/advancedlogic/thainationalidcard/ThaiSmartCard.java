package co.advancedlogic.thainationalidcard;

import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;

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

    public class CardInformation {
        public String CardInfo;
        public String PersonalID;
        public String NameTH;
        public String NameEN;
        public String BirthDate;
        public String Address;
        public String PictureTag;
        public String Issuer;
        public String IssuerCode;
        public String IssueDate;
        public String ExpireDate;
    }

    public CardInformation getCardInformation() {
        SmartCardMessage.DataBlock data;
        CardInformation cardInformation;

        byte[] block1_1 = new byte[]{(byte)0x80, (byte)0xb0, (byte)0x00, (byte)0x00, (byte)0x02, (byte)0x00, (byte)0xff};
        byte[] block1_2 = new byte[]{(byte)0x80, (byte)0xb0, (byte)0x00, (byte)0xff, (byte)0x02, (byte)0x00, (byte)0x7a};
        byte[] block2 = new byte[]{(byte)0x80, (byte)0xb0, (byte)0x15, (byte)0x79, (byte)0x02, (byte)0x00, (byte)0xae};

        if (!this.selectAppletStorageData()) {
            Log.d(TAG, "selectAppletStorageData fail");
            return null;
        }

        byte[] buffer = new byte[377];

        if ((data = this.getCardData(block1_1)) == null) {
            Log.d(TAG, "Get card information block 1-1 failed");
            return null;
        }

        if (data.status != 0 || data.error != 0 || data.data.length != 0xff) {
            Log.w(TAG, String.format("Invalid card information block 1-1 [%d][%d][%d][%s]", data.status, data.error, data.data.length, this.byteArrayToHexString(data.data)));
            return null;
        }

        System.arraycopy(data.data, 0, buffer,0, 0xff);

        if ((data = this.getCardData(block1_2)) == null) {
            Log.w(TAG, "Get card information block 1-2 failed");
            return null;
        }

        if (data.status != 0 || data.error != 0 || data.data.length != 0x7a) {
            Log.w(TAG, String.format("Invalid card information block 1-2 [%d][%d][%d][%s]", data.status, data.error, data.data.length, this.byteArrayToHexString(data.data)));
            return null;
        }

        System.arraycopy(data.data, 0, buffer,0xff, 0x7a);

        if ((data = this.getCardData(block2)) == null) {
            Log.w(TAG, "Get card information block 2 failed");
            return null;
        }

        if (data.status != 0 || data.error != 0 || data.data.length != 0xae) {
            Log.w(TAG, String.format("Invalid card information block 2 [%d][%d][%d][%s]", data.status, data.error, data.data.length, this.byteArrayToHexString(data.data)));
            return null;
        }

        // NameTH split
        byte[] nameTHBuffer;
        int length;

        nameTHBuffer = Arrays.copyOfRange(buffer, 17, 117);
        for (length = 100; length > 0 && nameTHBuffer[length - 1] == 0x20; length--);

        for (int i = 0; i < length; i++) {
            if (nameTHBuffer[i] == 0x23) {
                nameTHBuffer[i] = 0x20;
            }
        }
        nameTHBuffer = Arrays.copyOf(nameTHBuffer, length);

        // NameTH split
        byte[] nameENBuffer;

        nameENBuffer = Arrays.copyOfRange(buffer, 117, 217);
        for (length = 100; length > 0 && nameENBuffer[length - 1] == 0x20; length--);

        for (int i = 0; i < length; i++) {
            if (nameENBuffer[i] == 0x23) {
                nameENBuffer[i] = 0x20;
            }
        }
        nameENBuffer = Arrays.copyOf(nameENBuffer, length);

        // issuer split
        byte[] issuerBuffer;
        int index;

        for (index = 246; index < 346; index++) {
            if (buffer[index] == 0x20) {
                break;
            }
        }
        if (index >= 346) {
            Log.w(TAG, "Invalid issuer data split");
            return null;
        }

        issuerBuffer = Arrays.copyOfRange(buffer, 246, index);

        // Address split
        byte[] addressBuffer;

        addressBuffer = Arrays.copyOfRange(data.data, 0, 160);
        for (length = 160; length > 0 && addressBuffer[length - 1] == 0x20; length--);

        for (int i = 0; i < length; i++) {
            if (addressBuffer[i] == 0x23) {
                addressBuffer[i] = 0x20;
            }
        }
        addressBuffer = Arrays.copyOf(addressBuffer, length);

        // return object
        cardInformation = new CardInformation();

        try {
            cardInformation.CardInfo = String.format("%s-%s-%s-%s", new String(Arrays.copyOfRange(buffer, 375, 377), "TIS620"), new String(Arrays.copyOfRange(buffer, 0, 4), "TIS620"), new String(Arrays.copyOfRange(buffer, 226, 237), "TIS620"), new String(Arrays.copyOfRange(buffer, 238, 246), "TIS620"));
            cardInformation.PersonalID = new String(Arrays.copyOfRange(buffer, 4, 17), "TIS620");
            cardInformation.NameTH = new String(nameTHBuffer, "TIS620");
            cardInformation.NameEN = new String(nameENBuffer, "TIS620");
            cardInformation.BirthDate = new String(Arrays.copyOfRange(buffer, 217, 225), "TIS620");
            cardInformation.Issuer = new String(issuerBuffer, "TIS620");
            cardInformation.IssuerCode = new String(Arrays.copyOfRange(buffer, 346, 359), "TIS620");
            cardInformation.IssueDate = new String(Arrays.copyOfRange(buffer, 359, 367), "TIS620");
            cardInformation.ExpireDate = new String(Arrays.copyOfRange(buffer, 367, 375), "TIS620");
            cardInformation.Address = new String(addressBuffer, "TIS620");
            cardInformation.PictureTag = new String(Arrays.copyOfRange(data.data, 160, 174), "TIS620");
        } catch (UnsupportedEncodingException e) {
            Log.w(TAG, "Cannot decode TIS620 string");
            return null;
        }

        return cardInformation;
    }
}
