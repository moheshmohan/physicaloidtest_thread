package com.example.physicaloidtest;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.physicaloid.lib.Physicaloid;
import com.physicaloid.lib.usb.driver.uart.ReadLisener;
import com.physicaloid.lib.usb.driver.uart.UartConfig;

public class MainActivity extends Activity {
	// Linefeed Code Settings
	private static final int LINEFEED_CODE_CR = 0;
	private static final int LINEFEED_CODE_CRLF = 1;
	private static final int LINEFEED_CODE_LF = 2;
	private static final int DISP_CHAR = 0;

	private int mBaudrate = 9600;
	private int mDataBits = UartConfig.DATA_BITS8;
	private int mParity = UartConfig.PARITY_NONE;
	private int mStopBits = UartConfig.STOP_BITS1;
	private static final int TEXT_MAX_SIZE = 8192;

	Handler mHandler = new Handler();
	private TextView mTvSerial;
	private ScrollView mSvText;
	private final static String BR = "\n";

	int yellowPhysicaloid;
	int redPhysicaloid;
	private int mDisplayType = DISP_CHAR;
	private int mReadLinefeedCode = LINEFEED_CODE_LF;
	private int mWriteLinefeedCode = LINEFEED_CODE_LF;
	int count = 0, dcount = 0;

	Physicaloid[] temp;
	String[] channels;

	Thread[] threads;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// ****************************************************************
		// TODO : register intent filtered actions for device being attached or
		// detached
		IntentFilter filter = new IntentFilter();
		filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
		filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
		registerReceiver(mUsbReceiver, filter);
		// ****************************************************************

		mSvText = (ScrollView) findViewById(R.id.scrollView1);
		mTvSerial = (TextView) findViewById(R.id.tvSerial);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	public void openall(View view) throws InterruptedException {

		dcount = 0;
		count = 0;

		UsbManager mManager = (UsbManager) getSystemService(Context.USB_SERVICE);

		for (UsbDevice device : mManager.getDeviceList().values()) {
			dcount++;
		}

		temp = new Physicaloid[dcount];
		channels = new String[dcount];
		threads = new Thread[dcount];

		mTvSerial.append("starting \n");

		for (UsbDevice device : mManager.getDeviceList().values()) {
			temp[count] = new Physicaloid(this);
			mTvSerial.append(device.getDeviceName() + " is being tried\n");
			if (temp[count].open(new UartConfig(), device)) {
				mTvSerial.append(device.getDeviceName() + " Opened\n");

				threads[count] = new Thread(new readThread(count));
				threads[count].start();
				count++;
			}
		}

		try {
			for (int i = 0; i < count; i++) {
				byte[] buf = "HELLO\r".getBytes();
				temp[i].write(buf, buf.length);
			}
		} catch (Exception e) {

			return;
		}

		Thread.sleep(500);
		for (int i = 0; i < count; i++) {
			mTvSerial.append("channel "+ i + " is "+channels[i] + "\n");
			if (channels[i].startsWith("RED"))
				redPhysicaloid = i;
			if (channels[i].startsWith("YELLOW"))
				yellowPhysicaloid = i;
		}

	}

	public class readThread implements Runnable {

		int device;
		private StringBuilder mText;

		public readThread(int dev) {
			// store parameter for later user
			device = dev;
			mText = new StringBuilder();
		}

		public void run() {
			int len;
			byte[] rbuf = new byte[4096];

			for (;;) {// this is the main loop for transferring

				// ////////////////////////////////////////////////////////
				// Read and Display to Terminal
				// ////////////////////////////////////////////////////////
				if (temp != null) {
					if (temp[device] != null) {
						len = temp[device].read(rbuf);
						rbuf[len] = 0;
						boolean lastDataIs0x0D = false;
						if (len > 0) {
							for (int i = 0; i < len; ++i) {

								// "\r":CR(0x0D) "\n":LF(0x0A)
								if ((mReadLinefeedCode == LINEFEED_CODE_CR)
										&& (rbuf[i] == 0x0D)) {
									mText.append("");
									mText.append(BR);
								} else if ((mReadLinefeedCode == LINEFEED_CODE_LF)
										&& (rbuf[i] == 0x0A)) {
									mText.append("");
									mText.append(BR);
								} else if ((mReadLinefeedCode == LINEFEED_CODE_CRLF)
										&& (rbuf[i] == 0x0D)
										&& (rbuf[i + 1] == 0x0A)) {
									mText.append("");

									mText.append("");
									mText.append(BR);
									++i;
								} else if ((mReadLinefeedCode == LINEFEED_CODE_CRLF)
										&& (rbuf[i] == 0x0D)) {
									// case of rbuf[last] == 0x0D and rbuf[0] ==
									// 0x0A
									mText.append("");
									lastDataIs0x0D = true;
								} else if (lastDataIs0x0D && (rbuf[0] == 0x0A)) {

									mText.append("");
									mText.append(BR);
									lastDataIs0x0D = false;
								} else if (lastDataIs0x0D && (i != 0)) {
									// only disable flag
									lastDataIs0x0D = false;
									--i;
								} else {

									mText.append((char) rbuf[i]);
								}
							}
							if (mText.toString().contains("\r")) {
								channels[device] = "";
								channels[device] = mText.toString();
								mText.setLength(0);

							}
						}
					}
				}
				try {
					Thread.sleep(50);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public void sendRed(View view) {

		// Do something in response to button
		TextView TextView1 = (TextView) findViewById(R.id.textView1);// Android
		try {
			byte[] buf = "LED\r".getBytes();
			temp[redPhysicaloid].write(buf, buf.length);
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if (channels[redPhysicaloid].startsWith("ON"))
				buf = "OFF\r".getBytes();
			else
				buf = "ON\r".getBytes();

			temp[redPhysicaloid].write(buf, buf.length);
			TextView1.setText("Toggle Red");
		} catch (Exception e) {
			return;
		}
	}

	/** Called when the user clicks the Send button */
	public void sendYellow(View view) {
		// Do something in response to button
		TextView TextView1 = (TextView) findViewById(R.id.textView1);// Android
		try {
			byte[] buf = "LED\r".getBytes();
			temp[yellowPhysicaloid].write(buf, buf.length);
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if (channels[yellowPhysicaloid].startsWith("ON"))
				buf = "OFF\r".getBytes();
			else
				buf = "ON\r".getBytes();

			temp[yellowPhysicaloid].write(buf, buf.length);
			TextView1.setText("Toggle yellow");
		} catch (Exception e) {
			return;
		}
	}

	public void closeAll(View view) {

		for (int i = 0; i < count; i++) {
			threads[i].interrupt();
			threads[i] = null;
			mTvSerial.append("closing dev "+ i + " result is "+temp[i].close()+ "\n");
			
			temp[i] = null;
		}
		temp = null;
		channels = null;
		threads = null;
		count = 0;
		dcount = 0;
		redPhysicaloid = -1;
		yellowPhysicaloid = -1;
	}

	// ****************************************************************
	// TODO : get intent when a USB device attached
	protected void onNewIntent(Intent intent) {
		String action = intent.getAction();

		if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {

		}
	};

	// ****************************************************************

	// ****************************************************************
	// TODO : get intent when a USB device detached
	BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();

			if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {

			}
		}
	};
	// ****************************************************************

}
