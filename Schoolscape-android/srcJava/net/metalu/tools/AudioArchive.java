package net.metalu.tools;

import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;
import java.io.File;
//import java.io.IoUtils;
import java.util.ArrayList;
import java.util.List;
import java.nio.channels.FileChannel;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.BufferedInputStream;
import java.lang.String;
import java.io.IOException;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipEntry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.LinkedBlockingQueue;
import java.lang.Runtime;

public class AudioArchive {

	static final String TAG = "AudioArchive";
	public File sourcedirectory, tmpdirectory, zipfile, tmpzipfile;
	public boolean failed = false;

	void deleteRecursive(File fileOrDirectory) {
		if (fileOrDirectory.isDirectory())
			for (File child : fileOrDirectory.listFiles())
				deleteRecursive(child);

		fileOrDirectory.delete();
	}

	public void copyFile(File src, File dst) throws IOException {
		new File(dst.getParent()).mkdirs();
		FileInputStream inStream = new FileInputStream(src);
		FileOutputStream outStream = new FileOutputStream(dst);
		FileChannel inChannel = inStream.getChannel();
		FileChannel outChannel = outStream.getChannel();
		inChannel.transferTo(0, inChannel.size(), outChannel);
		inStream.close();
		outStream.close();
	}

	public boolean zip(File sourceFile, File zipFile) {
		List<File> fileList = getSubFiles(sourceFile, true);
		ZipOutputStream zipOutputStream = null;
		try {
			zipOutputStream = new ZipOutputStream(new FileOutputStream(zipFile));
			int bufferSize = 1024;
			byte[] buf = new byte[bufferSize];
			ZipEntry zipEntry;
			for(int i = 0; i < fileList.size(); i++) {
				File file = fileList.get(i);
				zipEntry = new ZipEntry(sourceFile.toURI().relativize(file.toURI()).getPath());
				zipOutputStream.putNextEntry(zipEntry);
				if (!file.isDirectory()) {
					InputStream inputStream = new BufferedInputStream(new FileInputStream(file));
					int readLength;
					while ((readLength = inputStream.read(buf, 0, bufferSize)) != -1) {
						zipOutputStream.write(buf, 0, readLength);
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			failed = true;
			failure(e.toString());
			return false;
		} finally {
			try {
				zipOutputStream.close();
			} catch (Exception e) {
				e.printStackTrace();
				failed = true;
				failure(e.toString());
				return false;
			}
		}
		return true;
	}	

	public static List<File> getSubFiles(File baseDir, boolean isContainFolder) {
		List<File> fileList = new ArrayList<>();
		File[] tmpList = baseDir.listFiles();
		for (File file : tmpList) {
			if (file.isFile()) {
				fileList.add(file);
			}
			if (file.isDirectory()) {
				if (isContainFolder) {
					fileList.add(file);
				}
				fileList.addAll(getSubFiles(file, false));
			}
		}
		return fileList;
	}

	public void copyAndStoreWavs(File currentdir, List<File> wavfiles)
	{
		File[] list = currentdir.listFiles();
		if(list!=null)
		{
			for (File fil : list)
			{
				if (fil.isDirectory())
				{
					copyAndStoreWavs(fil, wavfiles);
				}
				else {
					String srcpath = fil.getAbsolutePath();
					String relsrcpath = srcpath.substring(sourcedirectory.getAbsolutePath().length() + 1);
					if (fil.getName().endsWith(".wav"))
					{
						wavfiles.add(fil);
					}
					else // non-wav file:
					{
						File destfile = new File(tmpdirectory, relsrcpath);
						//Log.i(TAG,"copy non-wav file: " + relsrcpath);

						try {
							copyFile(fil, destfile);
						} catch(Exception e){
							e.printStackTrace();
							failed = true;
							failure(e.toString());
						}
					}
				}
			}
		}
	}

	public void progress(int i) {
		Log.i(TAG,"progress " + zipfile.getName() + " : " + i + "%");
	}

	public void failure(String errstring) {
		Log.i(TAG,"failure " + zipfile.getName() + " : " + errstring);
	}

	public void success() {
		Log.i(TAG,"success " + zipfile.getName());
	}
	@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
	void compress(File wavfile)
	{
		String srcpath = wavfile.getAbsolutePath();
		String relsrcpath = srcpath.substring(sourcedirectory.getAbsolutePath().length() + 1);
		File destfile = new File(tmpdirectory, relsrcpath.substring(0, relsrcpath.lastIndexOf(".")) + ".aac");
		new AACEncoder().encode(wavfile, destfile);
	}
	
	void finishZip()
	{
		Log.i(TAG,"moving temp dir... ");
		zip(tmpdirectory, tmpzipfile);
		zipfile.delete();
		tmpzipfile.renameTo(zipfile);
		progress(100);
		deleteRecursive(tmpdirectory);
		Log.i(TAG,"done!");
		success();
	}
	
	@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
	public void archive_(File _directory, File _zipfile) {
		new AACEncoder().testEncode();
		return;
	}

	public void archive(File _directory, File _zipfile) {
		sourcedirectory = _directory;
		zipfile = _zipfile;
		tmpdirectory = new File(sourcedirectory.getParent(), "tmp/" + sourcedirectory.getName());
		tmpzipfile = new File(sourcedirectory.getParent(), "tmp/" + zipfile.getName());
		deleteRecursive(tmpdirectory);
		tmpdirectory.mkdirs();
		new File(zipfile.getParent()).mkdirs();

		Log.i(TAG,"archive : " + sourcedirectory.getName() + " " + zipfile.getName());
		if(! sourcedirectory.exists()) {
			failed = true;
			failure("source directory doesn't exist");
			return;
		}
		progress(0);
		List<File> wavs = new ArrayList<File>();
		copyAndStoreWavs(sourcedirectory, wavs);
		if(failed) return;
		progress(5);

		final int numwavs = wavs.size();
		final LinkedBlockingQueue<File> qwavs = new LinkedBlockingQueue<File>(wavs);
		final AtomicInteger areEncoding = new AtomicInteger(0);
		int numThreads = Runtime.getRuntime().availableProcessors();
		Log.i(TAG,"compressing to AAC using " + numThreads + " threads");
		while(numThreads-- != 0) new Thread(new Runnable() {
			@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
			public void run() {
				File f;
				while((f = qwavs.poll()) != null) {
					int areEncodingNow;
					areEncoding.incrementAndGet();
					compress(f);
					areEncodingNow = areEncoding.decrementAndGet();
					progress(5 + ((numwavs - qwavs.size() - areEncodingNow) * 90) / numwavs);
				}
				if(areEncoding.get() == 0) finishZip();
			}
		}).start();
	}
}
