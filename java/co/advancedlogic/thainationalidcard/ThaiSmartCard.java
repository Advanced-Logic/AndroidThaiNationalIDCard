package co.advancedlogic.thainationalidcard;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Locale;

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

    public class PersonalInformation {
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

    public PersonalInformation getPersonalInformation() {
        SmartCardMessage.DataBlock data;
        PersonalInformation personalInformation;

        byte[] block1_1 = new byte[]{(byte)0x80, (byte)0xb0, (byte)0x00, (byte)0x00, (byte)0x02, (byte)0x00, (byte)0xff};
        byte[] block1_2 = new byte[]{(byte)0x80, (byte)0xb0, (byte)0x00, (byte)0xff, (byte)0x02, (byte)0x00, (byte)0x7a};
        byte[] block2 = new byte[]{(byte)0x80, (byte)0xb0, (byte)0x15, (byte)0x79, (byte)0x02, (byte)0x00, (byte)0xae};

        if (!this.selectAppletStorageData()) {
            Log.d(TAG, "selectAppletStorageData fail");
            return null;
        }

        byte[] buffer = new byte[377];

        if ((data = this.getCardData(block1_1)) == null) {
            Log.d(TAG, "Get personal information block 1-1 failed");
            return null;
        }

        if (data.status != 0 || data.error != 0 || data.data.length != 0xff) {
            Log.w(TAG, String.format("Invalid personal information block 1-1 [%d][%d][%d][%s]", data.status, data.error, data.data.length, this.byteArrayToHexString(data.data)));
            return null;
        }

        System.arraycopy(data.data, 0, buffer,0, 0xff);

        if ((data = this.getCardData(block1_2)) == null) {
            Log.w(TAG, "Get personal information block 1-2 failed");
            return null;
        }

        if (data.status != 0 || data.error != 0 || data.data.length != 0x7a) {
            Log.w(TAG, String.format("Invalid personal information block 1-2 [%d][%d][%d][%s]", data.status, data.error, data.data.length, this.byteArrayToHexString(data.data)));
            return null;
        }

        System.arraycopy(data.data, 0, buffer,0xff, 0x7a);

        if ((data = this.getCardData(block2)) == null) {
            Log.w(TAG, "Get personal information block 2 failed");
            return null;
        }

        if (data.status != 0 || data.error != 0 || data.data.length != 0xae) {
            Log.w(TAG, String.format("Invalid personal information block 2 [%d][%d][%d][%s]", data.status, data.error, data.data.length, this.byteArrayToHexString(data.data)));
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
        personalInformation = new PersonalInformation();

        try {
            personalInformation.CardInfo = String.format("%s-%s-%s-%s", new String(Arrays.copyOfRange(buffer, 375, 377), "TIS620"), new String(Arrays.copyOfRange(buffer, 0, 4), "TIS620"), new String(Arrays.copyOfRange(buffer, 226, 237), "TIS620"), new String(Arrays.copyOfRange(buffer, 238, 246), "TIS620"));
            personalInformation.PersonalID = new String(Arrays.copyOfRange(buffer, 4, 17), "TIS620");
            personalInformation.NameTH = new String(nameTHBuffer, "TIS620");
            personalInformation.NameEN = new String(nameENBuffer, "TIS620");
            personalInformation.BirthDate = new String(Arrays.copyOfRange(buffer, 217, 225), "TIS620");
            personalInformation.Issuer = new String(issuerBuffer, "TIS620");
            personalInformation.IssuerCode = new String(Arrays.copyOfRange(buffer, 346, 359), "TIS620");
            personalInformation.IssueDate = new String(Arrays.copyOfRange(buffer, 359, 367), "TIS620");
            personalInformation.ExpireDate = new String(Arrays.copyOfRange(buffer, 367, 375), "TIS620");
            personalInformation.Address = new String(addressBuffer, "TIS620");
            personalInformation.PictureTag = new String(Arrays.copyOfRange(data.data, 160, 174), "TIS620");
        } catch (UnsupportedEncodingException e) {
            Log.w(TAG, "Cannot decode TIS620 string");
            return null;
        }

        return personalInformation;
    }

    public class ChipCardADM {
        public String Version;
        public String State;
        public String Authorize;
        public String LaserNumber;
    }

    public ChipCardADM getChipCardADM() {
        SmartCardMessage.DataBlock data;
        ChipCardADM chipCardADM;

        byte[] message = new byte[]{(byte)0x80, (byte)0x00, (byte)0x00, (byte)0x00};

        if (!this.selectAppletExtension()) {
            Log.d(TAG, "selectAppletExtension fail");
            return null;
        }

        if ((data = this.getCardData(message)) == null) {
            Log.d(TAG, "Get card information block 1-1 failed");
            return null;
        }

        if (data.status != 0 || data.error != 0 || data.data.length != 0x17) {
            Log.w(TAG, String.format("Invalid card ADM response [%d][%d][%d][%s]", data.status, data.error, data.data.length, this.byteArrayToHexString(data.data)));
            return null;
        }

        chipCardADM = new ChipCardADM();

        chipCardADM.Version = String.format(Locale.US, "%d.%d", data.data[0], data.data[1]);
        chipCardADM.State = String.format(Locale.US, "%d", data.data[2]);
        chipCardADM.Authorize = String.format(Locale.US, "%d", data.data[3]);
        chipCardADM.LaserNumber = new String(Arrays.copyOfRange(data.data, 7,23));

        return chipCardADM;
    }

    public Bitmap getPersonalPicture() {
        SmartCardMessage.DataBlock data;
        byte[] message = new byte[]{(byte)0x80, (byte)0xb0, (byte)0x00, (byte)0x00, (byte)0x02, (byte)0x00, (byte)0x00};
        byte[] pictureBuffer;

        if (!this.selectAppletStorageData()) {
            Log.d(TAG, "selectAppletStorageData fail");
            return null;
        }

        int offset = 0x017b, blockNumber = 1, blockLength, index = 0;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int PERSONAL_PIC_LENGTH = 5118;

        while (index < PERSONAL_PIC_LENGTH) {
            blockLength = ((PERSONAL_PIC_LENGTH - index) > 0xff) ? 0xff:(PERSONAL_PIC_LENGTH - index);

            message[2] = (byte)((offset >> 8) & 0xff);
            message[3] = (byte)(offset & 0xff);
            message[6] = (byte)blockLength;

            if ((data = this.getCardData(message)) == null) {
                Log.w(TAG, "Get personal picture block [" + blockNumber + "] failed");
                return null;
            }

            if (data.status != 0 || data.error != 0) {
                Log.w(TAG, String.format("Invalid personal picture response [%d][%d][%d][%d][%s]", blockNumber, data.status, data.error, data.data.length, this.byteArrayToHexString(data.data)));
                return null;
            }

            if (data.data.length != blockLength) {
                Log.w(TAG, "Get personal picture block [" + blockNumber + "] return invalid length [ " + data.data.length + "/" + blockLength + "]");
                return null;
            }

            try {
                buffer.write(data.data);
            } catch (IOException e) {
                Log.w(TAG, "Get personal picture block [" + blockNumber + "] append to buffer failed");
                return null;
            }

            offset += blockLength;
            index += blockLength;
            blockNumber++;
        }

        pictureBuffer = buffer.toByteArray();

        while (index >= 0 && pictureBuffer[index - 1] == 0x20) index--;

        return BitmapFactory.decodeByteArray(pictureBuffer, 0, index);
    }

    public boolean verifyPinCode(String pin) {
        SmartCardMessage.DataBlock data;
        byte[] message, pinBuffer, challengeBuffer;

        if (pin == null || pin.length() != 4) {
            Log.w(TAG, "Invalid pin code");
            return false;
        }

        pinBuffer = pin.getBytes(Charset.forName("TIS620"));

        if (!this.selectAppletExtension()) {
            Log.d(TAG, "selectAppletExtension fail");
            return false;
        }

        // pin challenge
        message = new byte[]{(byte)0x80, (byte)0xb4, (byte)0x00, (byte)0x00};

        if ((data = this.getCardData(message)) == null) {
            Log.w(TAG, "Get pin challenge failed");
            return false;
        }

        if (data.status != 0 || data.error != 0) {
            Log.w(TAG, String.format("Invalid pin challenge response [%d][%d][%d][%s]", data.status, data.error, data.data.length, this.byteArrayToHexString(data.data)));
            return false;
        }

        if (data.data.length != 32) {
            Log.w(TAG, String.format("Invalid pin challenge length [%d]", data.data.length));
            return false;
        }

        // TODO: calculate pin challenge response

        // verify pin
        message = new byte[]{(byte)0x80, (byte)0x20, (byte)0x01, (byte)0x00, (byte)0x20,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00};

        if ((data = this.getCardData(message)) == null) {
            Log.w(TAG, "Get pin challenge failed");
            return false;
        }

        if (data.status != 0 || data.error != 0) {
            Log.w(TAG, String.format("Invalid verify pin response [%d][%d][%d][%s]", data.status, data.error, data.data.length, this.byteArrayToHexString(data.data)));
            return false;
        }

        if (data.data[0] == (byte)0x63) {
            Log.w(TAG, String.format("PIN verify incorrect, remaining [%d]", data.data[1]));
            return false;
        } else if (data.data[0] == (byte)0x90) {
            return true;
        } else {
            Log.w(TAG, String.format("Invalid verify pin response data [%d][%s]", data.data.length, this.byteArrayToHexString(data.data)));
            return false;
        }
    }
}
