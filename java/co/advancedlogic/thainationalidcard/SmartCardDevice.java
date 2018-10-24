package co.advancedlogic.thainationalidcard;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;

public final class SmartCardDevice {
    private static final String ACTION_USB_PERMISSION = "ninkoman.smartcardreader.USB_PERMISSION";
    private static final String TAG = "SmartCardDevice";

    private Context context;
    private UsbDevice device;
    private SmartCardMessage message;
    private PendingIntent mPermissionIntent;
    private boolean havePermission = false;
    private UsbDeviceConnection deviceConnection = null;
    private UsbInterface deviceInterface = null;
    private UsbEndpoint inputEndpoint = null;
    private UsbEndpoint outputEndpoint = null;

    private int infIndex = 0;
    private int endpointInputIndex = 0;
    private int endpointOutputIndex = 0;
    private boolean stopped = true;
    private boolean started = false;
    private boolean deviceDetachedRegister = false;

    private SmartCardDeviceEvent eventCallback = null;

    public SmartCardDevice(Context context, UsbDevice device, int infIndex, int endpointInputIndex, int endpointOutputIndex, SmartCardDeviceEvent eventCallback) {
        if (context == null || device == null) {
            throw new NullPointerException();
        }

        this.context = context;
        this.device = device;
        this.message = new SmartCardMessage();

        this.infIndex = infIndex;
        this.endpointInputIndex = endpointInputIndex;
        this.endpointOutputIndex = endpointOutputIndex;
        this.eventCallback = eventCallback;

        mPermissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        context.registerReceiver(this.mUsbPermissionReceiver, filter);
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        this.stop();
    }

    public interface SmartCardDeviceEvent {
        void OnReady(SmartCardDevice device);
        void OnDetached(SmartCardDevice device);
    }

    public static SmartCardDevice getSmartCardDevice(Context context, String nameContain, int infIndex, int endpointInputIndex, int endpointOutputIndex, SmartCardDeviceEvent eventCallback) {
        UsbManager manager;
        HashMap<String, UsbDevice> deviceList;
        UsbDevice device = null;
        SmartCardDevice cardDevice = null;

        manager = (UsbManager)context.getSystemService(Context.USB_SERVICE);
        if (manager == null) {
            Log.w(TAG, "USB manager not found");
            return null;
        }

        deviceList = manager.getDeviceList();
        if (deviceList == null) {
            Log.w(TAG, "USB device list not found");
            return null;
        }

        for (String key : deviceList.keySet()) {
            Log.d(TAG, "Search device contain name [" + nameContain + "] with [" + deviceList.get(key).getProductName() + "] [" + deviceList.get(key).getDeviceName() + "]");
            if (deviceList.get(key).getProductName().contains(nameContain)) {
                device = deviceList.get(key);
                Log.d(TAG, "Found device: " + device.getProductName());
                break;
            }
        }

        if (device == null) {
            Log.w(TAG, "Device name [" + nameContain + "] not found");
            return null;
        }

        cardDevice = new SmartCardDevice(context, device, infIndex, endpointInputIndex, endpointOutputIndex, eventCallback);

        cardDevice.start();

        return cardDevice;
    }

    public static SmartCardDevice getSmartCardDevice(Context context, String nameContain, SmartCardDeviceEvent eventCallback) {
        UsbManager manager;
        HashMap<String, UsbDevice> deviceList;
        UsbDevice device = null;
        SmartCardDevice cardDevice = null;

        manager = (UsbManager)context.getSystemService(Context.USB_SERVICE);
        if (manager == null) {
            Log.w(TAG, "USB manager not found");
            return null;
        }

        deviceList = manager.getDeviceList();
        if (deviceList == null) {
            Log.w(TAG, "USB device list not found");
            return null;
        }

        for (String key : deviceList.keySet()) {
            Log.d(TAG, "Search device contain name [" + nameContain + "] in [" + deviceList.get(key).getProductName() + "] [" + deviceList.get(key).getDeviceName() + "]");
            if (deviceList.get(key).getProductName().contains(nameContain)) {
                device = deviceList.get(key);
                Log.d(TAG, "Found device: " + device.getProductName());
                break;
            }
        }

        if (device == null) {
            Log.w(TAG, "Device name [" + nameContain + "] not found");
            return null;
        }

        UsbInterface intf = device.getInterface(0);
        int[] endpointIndex = new int[2];
        int index = 0;

        for (int i = 0; i < intf.getEndpointCount(); i++) {
            if (intf.getEndpoint(i).getAttributes() == 2) {
                endpointIndex[index] = i;
                index++;

                if (index >= 2) {
                    break;
                }
            }
        }
        if (index < 2) {
            Log.d(TAG, "Smart Card device endpoint detect failed");
            return null;
        }

        cardDevice = new SmartCardDevice(context, device, 0, endpointIndex[0], endpointIndex[1], eventCallback);

        cardDevice.start();

        return cardDevice;
    }

    public void start() {
        UsbManager manager = (UsbManager)this.context.getSystemService(Context.USB_SERVICE);
        if (this.started) {
            return;
        }
        this.started = true;

        if (manager != null) {
            Log.d(TAG, "Start request permission");
            manager.requestPermission(device, mPermissionIntent);
        } else {
            throw new RuntimeException("USB manager not found");
        }
    }

    public void stop() {
        if (!this.stopped) {
            //this.deviceConnection.close();
            this.stopped = true;
        }
        this.started = false;

        this.havePermission = false;
    }

    public boolean reset() {
        SmartCardMessage.DataBlock dataBlock;
        byte[] data;
        byte[] message = this.message.getMessageSlotReset();

        if (this.havePermission) {
            if (!this.prepareConnection()) {
                Log.w(TAG, "prepareConnection() failed");
                return false;
            }

            if (!this.sendRequestMessage(message)) {
                Log.w(TAG, "sendRequestMessage() error");
                return false;
            }

            try {
                if ((data = this.receiveResponseMessage()) == null) {
                    Log.w(TAG, "receiveResponseMessage() error");
                    return false;
                }
            } catch (IOException e) {
                Log.w(TAG, "receiveResponseMessage() failed");
                return false;
            }

            dataBlock = this.message.parseDataBlock(data);
            if (dataBlock != null && dataBlock.data != null) {
                StringBuilder sb = new StringBuilder();

                for (byte b: dataBlock.data) {
                    sb.append(String.format("%02x", b));
                }

                Log.d(TAG, "Card reset response data[" + sb.toString() + "]");

                if (dataBlock.status != 0 || dataBlock.error != 0) {
                    Log.w(TAG, "Card reset return abnormal status " + dataBlock.status + ":" + dataBlock.error);
                    return false;
                }

                if (dataBlock.data[0] != (byte)0x82) {
                    Log.w(TAG, String.format("Card reset return abnormal code 0x%02x", dataBlock.data[0]));
                    return false;
                }

                return true;
            } else {
                Log.w(TAG, "Card reset return fail");
                return false;
            }
        }

        return false;
    }

    public boolean isStarted() {
        return !this.started;
    }

    //public void setMessageTypeT1(boolean isT1) {
    //    this.message.setMessageTypeT1(isT1);
    //}

    private final BroadcastReceiver mUsbPermissionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            UsbManager manager;

            if (ACTION_USB_PERMISSION.equals(action)) {
                Log.d(TAG, "USB permission broadcast received");
                synchronized (this) {
                    UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (device != null && device.getDeviceName().equals(SmartCardDevice.this.device.getDeviceName())) {
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            manager = (UsbManager)context.getSystemService(Context.USB_SERVICE);
                            if (manager == null) {
                                throw new RuntimeException("USB manager not found");
                            }

                            SmartCardDevice.this.deviceConnection = manager.openDevice(device);

                            if (SmartCardDevice.this.deviceConnection == null) {
                                throw new RuntimeException("Invalid USB device connection");
                            }

                            SmartCardDevice.this.deviceInterface = device.getInterface(infIndex);
                            SmartCardDevice.this.inputEndpoint = SmartCardDevice.this.deviceInterface.getEndpoint(endpointInputIndex);
                            SmartCardDevice.this.outputEndpoint = SmartCardDevice.this.deviceInterface.getEndpoint(endpointOutputIndex);

                            if (SmartCardDevice.this.deviceInterface == null || SmartCardDevice.this.inputEndpoint == null || SmartCardDevice.this.outputEndpoint == null) {
                                throw new RuntimeException("Invalid USB device interface or endpoint");
                            }

                            SmartCardDevice.this.havePermission = true;
                            SmartCardDevice.this.stopped = false;

                            if (SmartCardDevice.this.eventCallback != null)
                                SmartCardDevice.this.eventCallback.OnReady(SmartCardDevice.this);
                            Log.d(TAG, "Card device is ready");

                            if (!SmartCardDevice.this.deviceDetachedRegister) {
                                IntentFilter filter = new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED);
                                SmartCardDevice.this.context.registerReceiver(SmartCardDevice.this.mUsbDetachedReceiver, filter);
                                SmartCardDevice.this.deviceDetachedRegister = true;
                            }
                        } else {
                            SmartCardDevice.this.havePermission = false;
                            throw new RuntimeException("Device is not granted");
                        }
                    }
                }
            }
        }
    };

    private final BroadcastReceiver mUsbDetachedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null && action.equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                Log.d(TAG, "USB device is detached");

                if (device.getDeviceName().equals(SmartCardDevice.this.device.getDeviceName())) {
                    if (SmartCardDevice.this.eventCallback != null)
                        SmartCardDevice.this.eventCallback.OnDetached(SmartCardDevice.this);
                    SmartCardDevice.this.stop();

                    SmartCardDevice.this.context.unregisterReceiver(SmartCardDevice.this.mUsbDetachedReceiver);
                    SmartCardDevice.this.deviceDetachedRegister = false;
                }
            }
        }
    };

    public SmartCardMessage.DataBlock getATR() {
        if (!this.havePermission) {
            Log.w(TAG, "USB permission require, please call start() first");
            return null;
        }

        byte[] message = this.message.getMessageIccPowerOn();

        if (!this.prepareConnection()) {
            Log.w(TAG, "prepareConnection() failed");
            return null;
        }

        if (!this.sendRequestMessage(message)) {
            Log.w(TAG, "sendRequestMessage() failed");
            return null;
        }

        try {
            if ((message = this.receiveResponseMessage()) == null) {
                Log.w(TAG, "receiveResponseMessage() error");
                return null;
            }
        } catch (IOException e) {
            Log.w(TAG, "receiveResponseMessage() failed");
            return null;
        }

        return this.message.parseDataBlock(message);
    }

    public SmartCardMessage.DataBlock sendAPDU(byte[] dataAPDU) {
        byte[] data;

        if (!this.havePermission) {
            Log.w(TAG, "USB permission require, please call start() first");
            return null;
        }

        byte[][] messages = this.message.getMessageXfrBlock(dataAPDU);

        if (messages == null) {
            Log.w(TAG, "getMessageXfrBlock() return failed");
            return null;
        }

        if (!this.prepareConnection()) {
            Log.w(TAG, "prepareConnection() failed");
            return null;
        }

        for (byte[] message: messages) {
            if (!this.sendRequestMessage(message)) {
                Log.w(TAG, "sendRequestMessage() error");
                return null;
            }
        }

        try {
            if ((data = this.receiveResponseMessage()) == null) {
                Log.w(TAG, "receiveResponseMessage() error");
                return null;
            }
        } catch (IOException e) {
            Log.w(TAG, "receiveResponseMessage() failed");
            return null;
        }

        SmartCardMessage.DataBlock dataBlock = this.message.parseDataBlock(data);
        if (dataBlock != null && dataBlock.data != null) {
            StringBuilder sb = new StringBuilder();

            for (byte b: dataBlock.data) {
                sb.append(String.format("%02x", b));
            }

            Log.d(TAG, "Response data[" + sb.toString() + "]");
        }

        return dataBlock;
    }

    private boolean prepareConnection() {
        if (!this.havePermission) {
            Log.w(TAG, "USB permission require, please call start() first");
            return false;
        }

        this.deviceConnection.claimInterface(this.deviceInterface, true);

        return true;
    }

    private boolean sendRequestMessage(byte[] message) {
        int length;

        if (!this.havePermission) {
            Log.w(TAG, "USB permission require, please call start() first");
            return false;
        }

        if (message == null || message.length == 0) {
            Log.w(TAG, "message is null or invalid length");
            return false;
        }

        StringBuilder sb = new StringBuilder();
        for (byte b: message) {
            sb.append(String.format("%02x", (byte)b));
        }
        Log.d(TAG, "Sending message [" + message.length + "][" + sb.toString() + "]");

        length = this.deviceConnection.bulkTransfer(this.inputEndpoint, message, message.length, 0);

        if (length <= 0 || length != message.length) {
            Log.w(TAG, "message sending return invalid length " + length + "/" + message.length);
            return false;
        }

        return true;
    }

    private byte[] receiveResponseMessage() throws IOException {
        int length, totalLength;
        byte[] buffer;
        ByteArrayOutputStream dataStream = new ByteArrayOutputStream();

        if (!this.havePermission) {
            Log.w(TAG, "USB permission require, please call start() first");
            return null;
        }

        buffer = new byte[this.outputEndpoint.getMaxPacketSize()];

        length = this.deviceConnection.bulkTransfer(this.outputEndpoint, buffer, buffer.length, 0);

        if (length < 10) {
            Log.w(TAG, "receive message invalid length " + length);
            return null;
        }

        dataStream = new ByteArrayOutputStream();
        dataStream.write(buffer, 0, length);

        totalLength = (((int)buffer[4] & 0xff) << 24) | (((int)buffer[3] & 0xff) << 16) | (((int)buffer[2] & 0xff) << 8) | ((int)buffer[1] & 0xff);
        totalLength += 10;

        while (dataStream.size() < totalLength) {
            length = this.deviceConnection.bulkTransfer(this.outputEndpoint, buffer, buffer.length, 0);
            if (length < 0) {
                Log.w(TAG, "receive continues message return error");
                dataStream.close();
                return null;
            } else if (length == 0) {
                if (dataStream.size() < totalLength) {
                    Log.w(TAG, "receive continues message not success");
                    dataStream.close();
                    return null;
                } else {
                    break;
                }
            }

            dataStream.write(buffer, 0, length);
        }

        buffer = dataStream.toByteArray();
        dataStream.close();

        return buffer;
    }
}
