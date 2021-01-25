package net.metalu.Schoolscape;

import android.util.Log;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.metalu.tools.AudioArchive;
import net.metalu.tools.AudioExtract;

import cc.openframeworks.OFAndroid;

@RequiresApi(api = Build.VERSION_CODES.GINGERBREAD)
public class OFActivity extends cc.openframeworks.OFActivity{

	static final String TAG = "Schoolscape";

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		//String packageName = getPackageName();

		//ofApp = new OFAndroid(packageName,this);
	}
	
	@Override
	public void onDetachedFromWindow() {
	}
	
	/*@Override
	protected void onPause() {
		super.onPause();
		ofApp.pause();
	}

	@Override
	protected void onResume() {
		super.onResume();
		ofApp.resume();
	}*/

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
	if (OFAndroid.keyDown(keyCode, event)) {
		return true;
	} else {
		return super.onKeyDown(keyCode, event);
	}
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
	if (OFAndroid.keyUp(keyCode, event)) {
		return true;
	} else {
		return super.onKeyUp(keyCode, event);
	}
	}

	/*@Override
	public void onBackPressed(){
		
	}*/
	//OFAndroid ofApp;

	
	
	// Menus
	// http://developer.android.com/guide/topics/ui/menus.html
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Create settings menu options from here, one by one or infalting an xml
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// This passes the menu option string to OF
		// you can add additional behavior from java modifying this method
		// but keep the call to OFAndroid so OF is notified of menu events
		if(OFAndroid.menuItemSelected(item.getItemId())){

			return true;
		}
		return super.onOptionsItemSelected(item);
	}


	@Override
	public boolean onPrepareOptionsMenu (Menu menu){
		// This method is called every time the menu is opened
		//  you can add or remove menu options from here
		return  super.onPrepareOptionsMenu(menu);
	}

	public void openWeb(Object... args) {
		if((args.length>0) && args[0].getClass().equals(String.class)) {
			Log.i(TAG,"rcvd openWeb message.");
			/*WebView myWebView = (WebView) findViewById(R.id.webview);
			String Url = args[0].toString();
			setupWebViewClient(myWebView);
			myWebView.loadUrl(Url);*/
		}
	}

	public void findByExtension(String ext, File file, List<File> result)
	{
		File[] list = file.listFiles();
		if(list!=null)
			for (File fil : list)
			{
				if (fil.isDirectory())
				{
					findByExtension(ext, fil, result);
				}
				else if (fil.getName().endsWith(ext))
				{
					result.add(fil);
				}
			}
	}

	public void archive(Object... args) {
		if((args.length>1) && args[0].getClass().equals(String.class) && args[1].getClass().equals(String.class)) {
			File directory = new File(args[0].toString());
			File zipfile = new File(args[1].toString());
			new AudioArchive(){
				public void progress(int i) {
					//Log.i(TAG,"archive " + zipfile.getName() + " progress " + i + "%");
                    sendToPdAsync("archive", zipfile.getName(), "progress", (float)i);
				}
				public void failure(String errstring) {
					//Log.i(TAG,"archive " + zipfile.getName() + " failure: " + errstring);
					sendToPdAsync("archive", zipfile.getName(), "failure", errstring);
				}
				public void success() {
					//Log.i(TAG,"archive " + zipfile.getName() + " success");
					sendToPdAsync("archive", zipfile.getName(), "success");
				}
			}.archive(directory, zipfile);
		}
	}

	public void extract(Object... args) {
		if((args.length>1) && args[0].getClass().equals(String.class) && args[1].getClass().equals(String.class)) {
			File zipfile = new File(args[0].toString());
			File directory = new File(args[1].toString());
			new AudioExtract(){
				public void progress(int i) {
					//Log.i(TAG,"extract " + zipfile.getName() + " progress " + i + "%");
                    sendToPdAsync("extract", zipfile.getName(), "progress", (float)i);
				}
				public void failure(String errstring) {
					//Log.i(TAG,"extract " + zipfile.getName() + " failure " + errstring);
					sendToPdAsync("extract", zipfile.getName(), "failure", errstring);
				}
				public void success() {
					//Log.i(TAG,"extract " + zipfile.getName() + " success");
					sendToPdAsync("extract", zipfile.getName(), "success");
				}

			}.extract(zipfile, directory);
		}
	}
	public void quit(){
		finish();
		System.exit(0);

		OFAndroid.exit();
		finish();
		System.exit(0);
	}
	// receive messages from Pd

	public void systemMessage(Object... args) {
		if((args.length>1) && args[0].getClass().equals(String.class) && args[0].equals("openWeb")) {
			openWeb(java.util.Arrays.copyOfRange(args, 1, args.length));
		}
		/*if((args.length>1) && args[0].getClass().equals(String.class) && args[0].equals("setWeb")) {
			setWeb(java.util.Arrays.copyOfRange(args, 1, args.length));
		}*/
		/*else if((args.length>0) && args[0].getClass().equals(String.class) && args[0].equals("loaded")) {
			loaded = true;
		}*/
		else if((args.length>0) && args[0].getClass().equals(String.class) && args[0].equals("quit")) {
			quit();
		}
		else if((args.length >= 3) && args[0].getClass().equals(String.class) && args[0].equals("archive")) {
			archive(java.util.Arrays.copyOfRange(args, 1, args.length));
		}
		else if((args.length >= 3) && args[0].getClass().equals(String.class) && args[0].equals("extract")) {
			extract(java.util.Arrays.copyOfRange(args, 1, args.length));
		}
		/*else if((args.length>0) && args[0].getClass().equals(String.class) && args[0].equals("closeSplash")) {
			Log.i(TAG,"rcvd closeSplash message.");
			findViewById(R.id.imgSplash).setVisibility(View.GONE);
		}
		else if((args.length>=3) && args[0].getClass().equals(String.class) && args[0].equals("aacConvert")) {
			aacConvert(java.util.Arrays.copyOfRange(args, 1, args.length));
		}
		else if((args.length>=2) && args[0].getClass().equals(String.class) && args[0].equals("share")) {
			share(java.util.Arrays.copyOfRange(args, 1, args.length));
		}
		else if((args.length>0) && args[0].getClass().equals(String.class) && args[0].equals("getMusicStorageDir")) {
			getMusicStorageDir();
		}
		else if((args.length>1) && args[0].getClass().equals(String.class) && args[0].equals("scanMediaFile")) {
			scanMediaFile(java.util.Arrays.copyOfRange(args, 1, args.length));
		}
		else if((args.length>0) && args[0].getClass().equals(String.class) && args[0].equals("iabSetup")) {
			iabSetup(true);
		}
		else if((args.length>1) && args[0].getClass().equals(String.class) && args[0].equals("iabQueryInventory")) {
			iabQueryInventory(java.util.Arrays.copyOfRange(args, 1, args.length));
		}
		else if((args.length>1) && args[0].getClass().equals(String.class)
				&& args[1].getClass().equals(String.class)
				&& args[0].equals("iabPurchase")) {
			iabPurchase(args[1].toString());
		}
		else if((args.length>0) && args[0].getClass().equals(String.class) && args[0].equals("getStatus")) {
			sendToPd("pushReceived", (float)(pushReceived ? 1.0 : 0.0));
			//Log.i(TAG,"sendStatus to pd: pushReceived=" + pushReceived + ":" + (pushReceived ? 1.0 : 0.0));
			boolean msl = getBooleanPref("share", false);
			sendToPd("shareEnabled", (float)(msl ? 1.0 : 0.0));

			boolean testflight = getBooleanPref("testflight", false);
			if(testflight) ((App) getApplication()).ParseSubscribe("TestFlight");
			else ((App) getApplication()).ParseUnsubscribe("TestFlight");

			boolean tutoDone = getBooleanPref("tutodone", false);
			sendToPd("tutoDone", (float)(tutoDone ? 1.0 : 0.0));

			float syncJamsNudge = getFloatPref("syncJamsNudge", (float) 0.0);
			sendToPd("syncJamsNudge", syncJamsNudge);
			Log.i(TAG,"syncJamsNudge from prefs: "+syncJamsNudge);

			sendToPd("os","android");

		}
		else if((args.length>1) && args[0].getClass().equals(String.class) && args[0].equals("setPref")) {
			setPref(java.util.Arrays.copyOfRange(args, 1, args.length));
		}
		else if((args.length>1) && args[0].getClass().equals(String.class) && args[0].equals("log")) {
			sysLog(false, java.util.Arrays.copyOfRange(args, 1, args.length));
		}
		else if((args.length>1) && args[0].getClass().equals(String.class) && args[0].equals("logTimed")) {
			sysLog(true, java.util.Arrays.copyOfRange(args, 1, args.length));
		}
		else if((args.length>1) && args[0].getClass().equals(String.class) && args[0].equals("logEnd")) {
			sysLogEnd(java.util.Arrays.copyOfRange(args, 1, args.length));
		}*/


	}

	public void receiveMessage(final Object... args) {
		this.runOnUiThread(new Runnable(){
			@Override
			public void run() {
				if((args.length>1) && args[0].getClass().equals(String.class) && args[0].equals("toSYSTEM")) {
					systemMessage(java.util.Arrays.copyOfRange(args, 1 , args.length));
				}
			}
		});
	}

	// send to Pd:
	public static native void sendToPd(Object...args);

	public void sendToPdAsync(final Object...args) {
		this.runOnUiThread(new Runnable(){
			@Override
			public void run() {
				sendToPd(args);
			}
		});
	}
}



