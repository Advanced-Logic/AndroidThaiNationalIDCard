package co.advancedlogic.thainationalidcard;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public final class ThaiSmartCard {
    private static final String TAG = "ThaiSmartCard";

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

        if ((data = this.device.sendAPDU(dataRequestMessage)) == null) {
            Log.w(TAG, "APDU body request fail");
            return null;
        }

        return data;
    }

    private boolean selectAppletChipData() {
        byte[] message = new byte[]{(byte)0x00, (byte)0xa4, (byte)0x04, (byte)0x00};
        SmartCardMessage.DataBlock data;

        return ((data = this.getCardData(message)) != null && data.status == 0 && data.error == 0);
    }

    private boolean selectAppletStorageData() {
        byte[] message = new byte[]{(byte)0x00, (byte)0xa4, (byte)0x04, (byte)0x00, (byte)0x08, (byte)0xa0, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x54, (byte)0x48, (byte)0x00, (byte)0x01};
        SmartCardMessage.DataBlock data;

        return ((data = this.getCardData(message)) != null && data.status == 0 && data.error == 0);
    }

    private boolean selectAppletExtension() {
        byte[] message = new byte[]{(byte)0x00, (byte)0xa4, (byte)0x04, (byte)0x00, (byte)0x08, (byte)0xa0, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x84, (byte)0x06, (byte)0x00, (byte)0x02};
        SmartCardMessage.DataBlock data;

        return ((data = this.getCardData(message)) != null && data.status == 0 && data.error == 0);
    }

    private boolean selectAppletBio() {
        byte[] message = new byte[]{(byte)0x00, (byte)0xa4, (byte)0x04, (byte)0x00, (byte)0x08, (byte)0xa0, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x84, (byte)0x06, (byte)0x00, (byte)0x00};
        SmartCardMessage.DataBlock data;

        return ((data = this.getCardData(message)) != null && data.status == 0 && data.error == 0);
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

    private byte[] hexStringToByteArray(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i+1), 16));
        }

        return data;
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

        if (data.status != 0 || data.error != 0 || data.data.length != 45 + 2) {
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

        if (data.status != 0 || data.error != 0 || data.data.length != 0xff + 2) {
            Log.w(TAG, String.format("Invalid personal information block 1-1 [%d][%d][%d][%s]", data.status, data.error, data.data.length, this.byteArrayToHexString(data.data)));
            return null;
        }

        System.arraycopy(data.data, 0, buffer,0, 0xff);

        if ((data = this.getCardData(block1_2)) == null) {
            Log.w(TAG, "Get personal information block 1-2 failed");
            return null;
        }

        if (data.status != 0 || data.error != 0 || data.data.length != 0x7a + 2) {
            Log.w(TAG, String.format("Invalid personal information block 1-2 [%d][%d][%d][%s]", data.status, data.error, data.data.length, this.byteArrayToHexString(data.data)));
            return null;
        }

        System.arraycopy(data.data, 0, buffer,0xff, 0x7a);

        if ((data = this.getCardData(block2)) == null) {
            Log.w(TAG, "Get personal information block 2 failed");
            return null;
        }

        if (data.status != 0 || data.error != 0 || data.data.length != 0xae + 2) {
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

        if (data.status != 0 || data.error != 0 || data.data.length != 0x17 + 2) {
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

            if (data.data.length != blockLength + 2) {
                Log.w(TAG, "Get personal picture block [" + blockNumber + "] return invalid length [" + data.data.length + "/" + blockLength + "]");
                return null;
            }

            buffer.write(data.data, 0, blockLength);

            offset += blockLength;
            index += blockLength;
            blockNumber++;
        }

        pictureBuffer = buffer.toByteArray();

        while (index >= 0 && pictureBuffer[index - 1] == 0x20) index--;

        return BitmapFactory.decodeByteArray(pictureBuffer, 0, index);
    }

    private abstract class SimpleRequest {
        String url;
        String response = null;

        SimpleRequest(String url) {
            this.url = url;
        }

        abstract void onResponse(String response);
        abstract void onError();
    }

    public static class SimpleGetRequestAsync extends AsyncTask<SimpleRequest, Void, SimpleRequest> {

        @Override
        protected SimpleRequest doInBackground(SimpleRequest... requests) {
            if (requests[0] == null) {
                return null;
            }

            SimpleRequest request = requests[0];

            URL url;
            try {
                url = new URL(request.url);
            } catch (MalformedURLException e) {
                Log.w(TAG, "Invalid parsing url: " + request.url);
                return null;
            }

            HttpURLConnection connection;
            try {
                connection = (HttpURLConnection)url.openConnection();
            } catch (IOException e) {
                Log.w(TAG, "Cannot open connection url: " + request.url);
                return null;
            }

            try {
                connection.setRequestMethod("GET");
            } catch (ProtocolException e) {
                Log.w(TAG, "Cannot set method GET to url: " + request.url);
                return null;
            }

            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            // special header for call service
            connection.setRequestProperty("X-User-Service", "Thai-SmartCard-Helper");

            try {
                connection.connect();
            } catch (IOException e) {
                Log.w(TAG, "Cannot connect url: " + request.url);
                return null;
            }

            byte[] responseBuffer = null;

            try {
                if (connection.getResponseCode() == 200) {
                    InputStream is = connection.getInputStream();
                    byte[] newBuffer = new byte[4096];
                    int length;
                    ByteArrayOutputStream outStream = new ByteArrayOutputStream();

                    while ((length = is.read(newBuffer)) != -1) {
                        outStream.write(newBuffer, 0, length);
                    }

                    responseBuffer = outStream.toByteArray();
                } else {
                    Log.w(TAG, "Response code: " + connection.getResponseCode());
                    connection.disconnect();
                    return null;
                }
            } catch (IOException e) {
                Log.w(TAG, "Cannot get response: " + request.url);
                connection.disconnect();
                return null;
            }

            connection.disconnect();

            request.response = new String(responseBuffer);

            return request;
        }

        @Override
        protected void onPostExecute(SimpleRequest request) {
            super.onPostExecute(request);

            String response;

            if (request != null) {
                if (request.response != null) {
                    response = request.response;

                    request.onResponse(response);
                } else {
                    Log.d(TAG, "Response not found");
                    request.onError();
                }
            } else {
                Log.d(TAG, "Invalid request task");
            }
        }
    }

    public interface VerifyPinCallback {
        void onSuccess();
        void onFailed(int remain);
        void onError();
    }

    public boolean verifyPinCode(String pin, final VerifyPinCallback verifyPinCallback) {
        SmartCardMessage.DataBlock data;
        byte[] message;

        if (pin == null || pin.length() != 4) {
            Log.w(TAG, "Invalid pin code");
            return false;
        }

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

        if (data.data.length != 0x20 + 2) {
            Log.w(TAG, String.format("Invalid pin challenge length [%d]", data.data.length));
            return false;
        }

        // using public server for generate challenge response data
        String requestUrl = String.format("https://raspberrypihobby.com/validate_pin?pin=%s&challenge=%s", pin, this.byteArrayToHexString(data.data).substring(0, 64));
        Log.d(TAG, "Request url: [" + requestUrl + "]");

        new SimpleGetRequestAsync().execute(new SimpleRequest(requestUrl) {
            @Override
            void onResponse(String response) {
                SmartCardMessage.DataBlock data;
                byte[] challengeResponse, message;

                if (response.length() == 64) {
                    challengeResponse = ThaiSmartCard.this.hexStringToByteArray(response);

                    message = new byte[]{(byte)0x80, (byte)0x20, (byte)0x01, (byte)0x00, (byte)0x20,
                            (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                            (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                            (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                            (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00};

                    System.arraycopy(challengeResponse, 0, message, 5, challengeResponse.length);

                    if ((data = ThaiSmartCard.this.device.sendAPDU(message)) == null) {
                        Log.w(TAG, "Get pin verify failed");
                        verifyPinCallback.onError();
                        return;
                    }

                    if (data.status != 0 || data.error != 0) {
                        Log.w(TAG, String.format("Invalid verify pin response [%d][%d][%d][%s]", data.status, data.error, data.data.length, ThaiSmartCard.this.byteArrayToHexString(data.data)));
                        verifyPinCallback.onError();
                        return;
                    }

                    if (data.data[0] == (byte)0x63) {
                        Log.w(TAG, String.format("PIN verify incorrect, remaining [%d]", data.data[1]));
                        verifyPinCallback.onFailed(data.data[1]);
                    } else if (data.data[0] == (byte)0x90) {
                        verifyPinCallback.onSuccess();
                    } else {
                        Log.w(TAG, String.format("Invalid verify pin response data [%d][%s]", data.data.length, ThaiSmartCard.this.byteArrayToHexString(data.data)));
                        verifyPinCallback.onError();
                    }
                } else {
                    Log.w(TAG, "Challenge generate invalid length: " + response.length());
                    verifyPinCallback.onError();
                }
            }

            @Override
            void onError() {
                Log.w(TAG, "SimpleRequest on error");
                verifyPinCallback.onError();
            }
        });

        return true;
    }

    public enum  VerifyResult {
        NOT_ALLOW(-2),
        ERROR(-1),
        SUCCESS(0),
        TIMEOUT(1);

        private final int value;
        VerifyResult(int value) { this.value = value; }
        public int getValue() { return this.value; }
    }

    public VerifyResult verifyFingerPrint(int maxRetry) {
        int i = 0;
        SmartCardMessage.DataBlock data;
        SmartCardMessage.EscapeResponseBlock escape;

        // test on precise 200-250 MC only
        if (!this.device.getDeviceProductName().contains("Precise")) {
            return VerifyResult.NOT_ALLOW;
        }

        if (!this.selectAppletBio()) {
            Log.d(TAG, "selectAppletBio fail");
            return VerifyResult.ERROR;
        }

        // get fingerprint parameter
        byte[] message = new byte[]{(byte)0xb0, (byte)0x34, (byte)0x00, (byte)0x76};

        if ((data = this.device.sendAPDU(message)) == null) {
            Log.w(TAG, "APDU get fingerprint parameter failed");
            return VerifyResult.ERROR;
        }

        if (data.error != 0 || data.status != 0) {
            Log.w(TAG, "APDU get fingerprint parameter return abnormal status [" + data.status + ":" + data.error + "]");
            return VerifyResult.ERROR;
        }

        if (data.data == null || data.data.length != 0x78) {
            Log.w(TAG, "APDU get fingerprint parameter invalid data: " + ((data.data != null) ? data.data.length: 0));
            return VerifyResult.ERROR;
        }

        byte[] param = new byte[0x76];

        System.arraycopy(data.data, 0, param, 0, 0x76);



        // set fingerprint parameter
        if ((escape = this.device.sendEscapeCommand(message)) == null) {
            Log.w(TAG, "Escape command set fingerprint parameter 1 failed");
            return VerifyResult.ERROR;
        }

        if (escape.error != 0 || escape.status != 0) {
            Log.w(TAG, "Escape command set fingerprint parameter 1 return abnormal status [" + escape.status + ":" + escape.error + "]");
            return VerifyResult.ERROR;
        }

        if (escape.data.length != 3 || escape.data[0] != 0x00 || escape.data[1] != 0x00 || escape.data[2] != 0x00) {
            Log.w(TAG, "Escape command set fingerprint parameter 1 invalid data: " + escape.data.length + " " + String.format("%02d %02d %02d", escape.data[0], escape.data[1], escape.data[2]));
            return VerifyResult.ERROR;
        }

        // set parameter ??
        message = new byte[]{(byte)0x00, (byte)0xc8, (byte)0x00};

        if ((escape = this.device.sendEscapeCommand(message)) == null) {
            Log.w(TAG, "Escape command set fingerprint parameter 2 failed");
            return VerifyResult.ERROR;
        }

        if (escape.error != 0 || escape.status != 0) {
            Log.w(TAG, "Escape command set fingerprint parameter 2 return abnormal status [" + escape.status + ":" + escape.error + "]");
            return VerifyResult.ERROR;
        }

        while (i < maxRetry) {
            //

            i++;
        }

        return VerifyResult.ERROR; ///
    }

}

