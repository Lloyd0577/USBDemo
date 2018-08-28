package simplae.usbdemo;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private UsbManager mUsbmanager;
    private PendingIntent mPendingIntent;
    private Button btn_search;
    private UsbDevice localUsbDevice;
    private UsbPermissionReceiver usbPermissionReceiver;
    public static String ACTION_DEVICE_PERMISSION = "actionDevicePermission";
    private UsbInterface usbInterface;
    private UsbEndpoint usbEndpointIn;
    private UsbEndpoint usbEndpointOut;
    private UsbDeviceConnection usbDeviceConnection;
    private TextView tv_log;
    private UsbEndpoint point;
    private int readcount;
    private String data = "";
    private String measureType;
    StringBuilder sb = new StringBuilder();
    private String result;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btn_search = (Button) findViewById(R.id.btn_search);
        tv_log = (TextView) findViewById(R.id.tv_log);
        btn_search.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enumerateDevice();
            }
        });
    }

    public UsbDevice enumerateDevice() {
        mUsbmanager = (UsbManager) getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = mUsbmanager.getDeviceList();
        if (deviceList.isEmpty()) {
            Toast.makeText(this, "No Device Or Device Not Match", Toast.LENGTH_LONG).show();
            return null;
        }
        Iterator<UsbDevice> localIterator = deviceList.values().iterator();
        while (localIterator.hasNext()) {
            localUsbDevice = localIterator.next();
            Toast.makeText(this, "vendorID is " + Integer.valueOf(localUsbDevice.getVendorId()) + "product id is " + Integer.valueOf(localUsbDevice.getProductId()), Toast.LENGTH_LONG).show();
            try {
                if (!mUsbmanager.hasPermission(localUsbDevice)) {
                    usbPermissionReceiver = new UsbPermissionReceiver();
                    //申请权限
                    Intent intent = new Intent(ACTION_DEVICE_PERMISSION);
                    PendingIntent mPermissionIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
                    IntentFilter permissionFilter = new IntentFilter(ACTION_DEVICE_PERMISSION);
                    this.registerReceiver(usbPermissionReceiver, permissionFilter);
                    mUsbmanager.requestPermission(localUsbDevice, mPermissionIntent);
                } else {
                    findDeviceAndReceiveData();
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.d("####", "请求授权失败：" + e.toString() + "\n");
                tv_log.append("请求授权失败：" + e.toString() + "\n");
            }

        }
        return null;
    }

    private class UsbPermissionReceiver extends BroadcastReceiver {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_DEVICE_PERMISSION.equals(action)) {
                findDeviceAndReceiveData();
            }
        }
    }

    public void findDeviceAndReceiveData() {
        try {
            usbInterface = localUsbDevice.getInterface(0);
            if (usbInterface != null) {
                tv_log.append("usbInterface is " + usbInterface + "::endpoint count is " + usbInterface.getEndpointCount() + "\n");
                for (int index = 0; index < usbInterface.getEndpointCount(); index++) {
                    point = usbInterface.getEndpoint(index);
                    tv_log.append("point is " + point + "\n");
                    if (point.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                        if (point.getDirection() == UsbConstants.USB_DIR_IN) {
                            usbEndpointIn = point;
                        } else if (point.getDirection() == UsbConstants.USB_DIR_OUT) {
                            usbEndpointOut = point;
                        }
                    }
                }
                tv_log.append("获取point 成功：in is " + usbEndpointIn + ",out is " + usbEndpointOut + "\n");
                usbDeviceConnection = mUsbmanager.openDevice(localUsbDevice);
                usbDeviceConnection.claimInterface(usbInterface, true);
                try {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            while (true) {
                                synchronized (this) {
                                    try {
                                        byte[] bytes = new byte[point.getMaxPacketSize()];
                                        int ret = usbDeviceConnection.bulkTransfer(point, bytes, bytes.length, 0);
                                        if (ret > 0) {
                                            final StringBuilder stringbuilder = new StringBuilder(bytes.length);
                                            for (byte b : bytes) {
                                                if (b != 0) {
                                                    if (b == 2) {
                                                        stringbuilder.append("da");
                                                    }
                                                    stringbuilder.append(Integer.toHexString(b));
                                                }
                                            }
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    try {
                                                        data = data + stringbuilder.toString();
                                                        if (data.substring(data.length() - 1, data.length()).equals("d")) {
                                                            tv_log.append("读取数据：" + data + "\n");
                                                            String assicStr = "";
                                                            for (int i = 0; i < data.length() - 1; i += 2) {
                                                                //grab the hex in pairs
                                                                String output = data.substring(i, (i + 2));
                                                                //convert hex to decimal
                                                                int decimal = Integer.parseInt(output, 16);
                                                                //convert the decimal to character
                                                                assicStr = assicStr + (char) decimal;
                                                            }
                                                            tv_log.append("assic is：" + assicStr + "\n");
                                                            if (assicStr.length() == 10) {
                                                                if (assicStr.charAt(3) == '0') {
                                                                    result = "当前体温：" + assicStr.substring(5, 9);
                                                                } else if (assicStr.charAt(3) == '1') {
                                                                    result = "当前表面温度：" + assicStr.substring(5, 9);
                                                                } else if (assicStr.charAt(3) == '2') {
                                                                    result = "历史记录" + assicStr.substring(1,3) + "体温" + assicStr.substring(5, 9);
                                                                } else if (assicStr.charAt(3) == '3') {
                                                                    result = "历史记录" + assicStr.substring(1,3) + "表面温度" + assicStr.substring(5, 9);
                                                                }
                                                            } else {
                                                                if (assicStr.length() < 4) {
                                                                    return;
                                                                }
                                                                result = assicStr.substring(assicStr.length() - 4, assicStr.length());
                                                                switch (result) {
                                                                    case "TaLo":
                                                                        result = result + "(环境温度低于0.1℃)";
                                                                        break;
                                                                    case "TaHi":
                                                                        result = result + "(环境温度高于40℃)";
                                                                        break;
                                                                    case "TbLo":
                                                                        result = result + "(目标温度低于0.1℃)";
                                                                        break;
                                                                    case "TbHi":
                                                                        result = result + "(目标温度高于100℃)";
                                                                        break;
                                                                    case "Er.r":
                                                                        result = result + "(系统自检错误)";
                                                                        break;
                                                                    case "Er.E":
                                                                        result = result + "(读EPPOM错误)";
                                                                        break;
                                                                }
                                                            }
                                                            tv_log.append(result + "\n");
                                                            data = "";
                                                        }
                                                    } catch (Exception e) {
                                                        tv_log.append("数据解析出错：" + e.toString() + "\n");
                                                    }
                                                }
                                            });
                                        }
                                    } catch (Exception e) {
                                        tv_log.append("读取数据异常：" + e.toString() + "\n");
                                    }
                                }
                            }
                        }
                    }).start();
                } catch (Exception e) {
                    tv_log.append("打开设备失败：" + e.toString() + "\n");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.d("####", "广播接受失败：" + e.toString());
            tv_log.append("广播接受失败：" + e.toString());
        }
    }


}
