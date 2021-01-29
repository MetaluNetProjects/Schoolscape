package net.metalu.tools;
/* from https://gist.github.com/muetzenflo/3e83975aba6abe63413abd98bb33c401 */

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.media.MediaExtractor;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
public class AACDecoder {
	private static final String TAG = AACDecoder.class.getSimpleName();
	private MediaExtractor extractor = new MediaExtractor();
	private MediaCodec decoder;

	private MediaFormat inputFormat;

	private ByteBuffer[] inputBuffers;
	private boolean end_of_input_file;

	private ByteBuffer[] outputBuffers;
	private int outputBufferIndex = -1;

	public AACDecoder(File inputFilename, File outputFilename) throws IOException {
		extractor.setDataSource(inputFilename.getPath());

		// Select the first audio track we find.
		/*MediaFormat informat = null;
		int numTracks = extractor.getTrackCount();
		for (int i = 0; i < numTracks; ++i) {
			informat = extractor.getTrackFormat(i);
			String mime = informat.getString(MediaFormat.KEY_MIME);
			if (mime.startsWith("audio/")) {
				extractor.selectTrack(i);
				//decoder = MediaCodec.createDecoderByType(mime);
				//decoder.configure(informat, null, null, 0);
				//inputFormat = informat;
				break;
			}
		}
		if(informat == null) return;*/
		extractor.selectTrack(0);
		MediaFormat informat = extractor.getTrackFormat(0);
		int samplerate = informat.containsKey(MediaFormat.KEY_SAMPLE_RATE) ?
				informat.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 44100;
		int channelcount = informat.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ?
				informat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 1;
		long duration = informat.containsKey(MediaFormat.KEY_DURATION) ?
				informat.getLong(MediaFormat.KEY_DURATION) : 0;
		String mime = informat.containsKey(MediaFormat.KEY_MIME) ?
				informat.getString(MediaFormat.KEY_MIME) : "";

		decoder = MediaCodec.createDecoderByType(mime);
		decoder.configure(informat, null, null, 0);

		if (decoder == null) {
			throw new IllegalArgumentException("No decoder for file format");
		}

		decoder.start();
		inputBuffers = decoder.getInputBuffers();
		outputBuffers = decoder.getOutputBuffers();
		end_of_input_file = false;

		long totalAudioLen = (long)((samplerate * duration) / 1e6 * channelcount * 2);
		long totalDataLen = totalAudioLen + 36;

		//AppLog.logString("File size: " + totalDataLen);

		byte[] header = wavFileHeader(totalAudioLen, totalDataLen,
				samplerate, channelcount, samplerate * channelcount * 2,
				(byte) 16);

		//Log.d(TAG, "decoding " + inputFilename.getName() + " numbytes=" + totalAudioLen +
		//		" channels=" + channelcount + " samplerate=" + samplerate);

		DataOutputStream output = null;
		try {
			output = new DataOutputStream(new FileOutputStream(outputFilename));
			output.write(header);

			byte[] data;
			while ((data = readData()) != null) {
				output.write(data);
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if(output != null) output.close();
			inputFilename.delete();
			extractor.release();
		}
	}

	private static byte[] shortToByteArray(short data) {
		return ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(data).array();
	}

	private byte[] wavFileHeader(long totalAudioLen, long totalDataLen, long longSampleRate,
								 int channels, long byteRate, byte bitsPerSample) {
		byte[] header = new byte[44];
		header[0] = 'R'; // RIFF/WAVE header
		header[1] = 'I';
		header[2] = 'F';
		header[3] = 'F';
		header[4] = (byte) (totalDataLen & 0xff);
		header[5] = (byte) ((totalDataLen >> 8) & 0xff);
		header[6] = (byte) ((totalDataLen >> 16) & 0xff);
		header[7] = (byte) ((totalDataLen >> 24) & 0xff);
		header[8] = 'W';
		header[9] = 'A';
		header[10] = 'V';
		header[11] = 'E';
		header[12] = 'f'; // 'fmt ' chunk
		header[13] = 'm';
		header[14] = 't';
		header[15] = ' ';
		header[16] = 16; // 4 bytes: size of 'fmt ' chunk
		header[17] = 0;
		header[18] = 0;
		header[19] = 0;
		header[20] = 1; // format = 1
		header[21] = 0;
		header[22] = (byte) channels;
		header[23] = 0;
		header[24] = (byte) (longSampleRate & 0xff);
		header[25] = (byte) ((longSampleRate >> 8) & 0xff);
		header[26] = (byte) ((longSampleRate >> 16) & 0xff);
		header[27] = (byte) ((longSampleRate >> 24) & 0xff);
		header[28] = (byte) (byteRate & 0xff);
		header[29] = (byte) ((byteRate >> 8) & 0xff);
		header[30] = (byte) ((byteRate >> 16) & 0xff);
		header[31] = (byte) ((byteRate >> 24) & 0xff);
		header[32] = (byte) (channels * (bitsPerSample / 8)); //
		// block align
		header[33] = 0;
		header[34] = bitsPerSample; // bits per sample
		header[35] = 0;
		header[36] = 'd';
		header[37] = 'a';
		header[38] = 't';
		header[39] = 'a';
		header[40] = (byte) (totalAudioLen & 0xff);
		header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
		header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
		header[43] = (byte) ((totalAudioLen >> 24) & 0xff);
		return header;
	}
	// Read the raw data from MediaCodec.
	// The caller should copy the data out of the ByteBuffer before calling this again
	// or else it may get overwritten.
	private byte[] readData(/*MediaCodec.BufferInfo info*/) {
		MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
		if (decoder == null)
			return null;

		for (;;) {
			// Read data from the file into the codec.
			if (!end_of_input_file) {
				int inputBufferIndex = decoder.dequeueInputBuffer(10000);
				if (inputBufferIndex >= 0) {
					int size = extractor.readSampleData(inputBuffers[inputBufferIndex], 0);
					if (size < 0) {
						// End Of File
						decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
						end_of_input_file = true;
					} else {
						decoder.queueInputBuffer(inputBufferIndex, 0, size, extractor.getSampleTime(), 0);
						extractor.advance();
						//Log.d(TAG, "readData got bytes from extractor: " + size);
					}
				}
			}

			/*// Read the output from the codec.
			if (outputBufferIndex >= 0)
				// Ensure that the data is placed at the start of the buffer
				outputBuffers[outputBufferIndex].position(0);*/

			outputBufferIndex = decoder.dequeueOutputBuffer(info, 10000);
			if (outputBufferIndex >= 0) {
				// Handle EOF
				/*if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
					decoder.stop();
					decoder.release();
					decoder = null;
					//Log.d(TAG, "readData EOF");
					return null;
				}*/

				//Log.d(TAG, "readData got bytes from decoder: " + info.size);

				byte[] outData = new byte[info.size];
				outputBuffers[outputBufferIndex].order(ByteOrder.LITTLE_ENDIAN).get(outData);
				decoder.releaseOutputBuffer(outputBufferIndex, false);

				if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
					decoder.stop();
					decoder.release();
					decoder = null;
					//Log.d(TAG, "readData EOF, size= " + info.size);
					//return null;
				}

				return outData; //outputBuffers[outputBufferIndex];

			} else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
				// This usually happens once at the start of the file.
				outputBuffers = decoder.getOutputBuffers();
			}
		}
	}

	// Return the Audio sample rate, in samples/sec.
	public int getSampleRate() {
		return inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
	}
}
