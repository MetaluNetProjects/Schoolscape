package net.metalu.tools;

import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
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
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipEntry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.LinkedBlockingQueue;
import java.lang.Runtime;

public class AudioExtract {

	static final String TAG = "AudioExtract";
	public File destdirectory, tmpdirectory, zipfile;
	
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

	// unzipping utility borrowed from https://gist.github.com/Antarix/a4e70a3c743e03d760bd
	private static final int BUFFER_SIZE = 4096;
	/**
	 * Extracts a zip file specified by the zipFilePath to a directory specified by
	 * destDirectory (will be created if does not exists)
	 * @param zipFilePath
	 * @param destDir
	 * @throws IOException
	 */
	public void unzip(File zipFilePath, File destDir) throws IOException {
		//File destDir = new File(destDirectory);
		if (!destDir.exists()) {
			destDir.mkdir();
		}
		ZipInputStream zipIn = new ZipInputStream(new FileInputStream(zipFilePath));
		ZipEntry entry = zipIn.getNextEntry();
		// iterates over entries in the zip file
		while (entry != null) {
			String filePath = destDir + File.separator + entry.getName();
			if (!entry.isDirectory()) {
				// if the entry is a file, extracts it
				extractFile(zipIn, filePath);
			} else {
				// if the entry is a directory, make the directory
				File dir = new File(filePath);
				dir.mkdir();
			}
			zipIn.closeEntry();
			entry = zipIn.getNextEntry();
		}
		zipIn.close();
	}
	/**
	 * Extracts a zip entry (file entry)
	 * @param zipIn
	 * @param filePath
	 * @throws IOException
	 */
	private void extractFile(ZipInputStream zipIn, String filePath) throws IOException {
		BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath));
		byte[] bytesIn = new byte[BUFFER_SIZE];
		int read = 0;
		while ((read = zipIn.read(bytesIn)) != -1) {
			bos.write(bytesIn, 0, read);
		}
		bos.close();
	}

	public void storeAACs(File currentdir, List<File> aacfiles)
	{
		File[] list = currentdir.listFiles();
		if(list!=null)
		{
			for (File fil : list)
			{
				if (fil.isDirectory())
				{
					storeAACs(fil, aacfiles);
				}
				else {
					if (fil.getName().endsWith(".aac")) {
						aacfiles.add(fil);
					}
				}
			}
		}
	}

	public void progress(int i) {
		Log.i(TAG,"progress " + zipfile.getName() + " :" + i + "%");
	}

	public void failure(String errstring) {
		Log.i(TAG,"failure " + zipfile.getName() + " : " + errstring);
	}

	public void success() {
		Log.i(TAG,"success " + zipfile.getName());
	}


	@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
	void decompress(File aacfile) throws IOException {
		String srcpath = aacfile.getAbsolutePath();
		File destfile = new File(srcpath.substring(0, srcpath.lastIndexOf(".")) + ".wav");
		new AACDecoder(aacfile, destfile);//.decode();
	}
	
	void finish()
	{
		Log.i(TAG,"unzipped!");
		//zip(tmpdirectory, tmpzipfile);
		deleteRecursive(destdirectory);
		tmpdirectory.renameTo(destdirectory);
		progress(100);
		//Log.i(TAG,"done!");
		success();
	}
	
	public void extract(File _zipfile, File _directory) {
		destdirectory = _directory;
		zipfile = _zipfile;
		tmpdirectory = new File(destdirectory.getParent(), "tmp/" + destdirectory.getName());
		deleteRecursive(tmpdirectory);
		tmpdirectory.mkdirs();

		//Log.i(TAG,"extract : " + zipfile.getName() + " " + destdirectory.getName());
		progress(0);

		try {
			unzip(zipfile, tmpdirectory);
		} catch (Exception e) {
			Log.e(TAG, "error during unzipping: " + e);
			failure(e.toString());
			return;
		}

		List<File> aacs = new ArrayList<File>();
		storeAACs(tmpdirectory, aacs);
		progress(5);

		final int numaacs = aacs.size();
		final LinkedBlockingQueue<File> qwavs = new LinkedBlockingQueue<File>(aacs);
		final AtomicInteger areDecoding = new AtomicInteger(0);
		int numThreads = Runtime.getRuntime().availableProcessors();
		//int numThreads = 1;
		//Log.i(TAG,"compressing to AAC using " + numThreads + " threads");
		while(numThreads-- != 0) new Thread(new Runnable() {
			@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
			public void run() {
				File f;
				while((f = qwavs.poll()) != null) {
					int areDecodingNow;
					areDecoding.incrementAndGet();
					try {
						decompress(f);
					} catch (IOException e) {
						e.printStackTrace();
					}
					areDecodingNow = areDecoding.decrementAndGet();
					progress(5 + ((numaacs - qwavs.size() - areDecodingNow) * 90) / numaacs);
				}
				if(areDecoding.get() == 0) finish();
			}
		}).start();
	}
}
