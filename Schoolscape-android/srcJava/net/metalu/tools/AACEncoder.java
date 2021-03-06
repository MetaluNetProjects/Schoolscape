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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;


/**
 * This class is used to encode a WAV file into an playable AAC file.
 * The WAV file should be 2 channel, 48000 Hz, 44-byte header.
 * The result is NOT a raw AAC file, but every AAC packet is prefixed with an ADTS header.
 * That way the file is playable on most devices/players. To increase compatibility, the
 * result should also be wrapped in an M4A container, but this is not done here.
 * This code works on Android from API 16+
 */

public class AACEncoder {

	private static final String TAG = AACEncoder.class.getSimpleName();

	private static final long QUEUE_TIMEOUT = 5000;

	//private static final int CHANNEL_COUNT = 1;
	private static final int AAC_PROFILE = MediaCodecInfo.CodecProfileLevel.AACObjectLC;
	//private static final int AAC_PROFILE = MediaCodecInfo.CodecProfileLevel.AACObjectHE;
	private static final int ADTS_SIZE = 7;
	private static final int WAV_HEADER_SIZE = 44;

	int ChannelCount = 2;
	int SampleRate = 44100;


	@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
	public void encode(File inputFile, File outputFile) {

		MediaCodec codec = null;
		MediaFormat format;
		FileOutputStream outputStream;

		try {

			MediaExtractor extractor = new MediaExtractor();
			extractor.setDataSource(inputFile.getPath());
			//where path is a String variable and points to the data source
			MediaFormat srcformat = extractor.getTrackFormat(0);
			//where i is an int variable and denotes the index value of a track.
			//For the first track: i = 0; 
			ChannelCount = srcformat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
			SampleRate = srcformat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
			//Log.d(TAG, "encode file: " + inputFile.getPath()
			//	+ " channels count: " + ChannelCount + " samplerate: " + SampleRate);

			// create input stream
			//File file = new File(inputFilePath);
			FileInputStream inputStream = new FileInputStream(inputFile);
			inputStream.skip(WAV_HEADER_SIZE);

			// create output stream
			//final String outputFilePath = inputFilePath.substring(0, inputFilePath.lastIndexOf(".")) + ".aac";
			outputStream = new FileOutputStream(outputFile);

			// set ouput mime type
			final String outputMimeType = "audio/mp4a-latm";

			// set output format
			format = new MediaFormat();
			format.setString(MediaFormat.KEY_MIME, outputMimeType);
			format.setInteger(MediaFormat.KEY_AAC_PROFILE, AAC_PROFILE);
			format.setInteger(MediaFormat.KEY_SAMPLE_RATE, SampleRate);
			format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, ChannelCount);
			format.setInteger(MediaFormat.KEY_BIT_RATE, 64 * 1024); // desired output(!) rate for encoder
			//Log.d(TAG, "format created");

			// get and configure encoding codec
			codec = MediaCodec.createEncoderByType(outputMimeType);
			codec.configure(format, null /* surface */, null /* crypto */, MediaCodec.CONFIGURE_FLAG_ENCODE);

			// encode wav file
			encodeSong(inputStream, outputStream, codec);

			// close input and output streams
			outputStream.close();
			inputStream.close();

			//Log.d(TAG, "encoded song written to " + outputFile.getPath());

		} catch (Exception e) {
			Log.e(TAG, "error during encoding: " + e);

		} finally {
			if (codec != null) {
				codec.flush();
				codec.stop();
				codec.release();
			}
		}
	}

	@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
	public void testEncode() {

		MediaCodec codec = null;
		MediaFormat format;
		//FileOutputStream outputStream;
		byte[] sintab = new byte[4096];
		for (int i = 0; i < 2048; i++) {
			short s = (short)(32768.0 * Math.sin(i / 2048.0 * 6.28 * 10.0)); // 220Hz sin
			sintab[2 * i] = (byte) s;
			sintab[2 * i] = (byte) (s >> 8);
		}

		try {

			ChannelCount = 1;
			SampleRate = 44100;
			// set ouput mime type
			final String outputMimeType = "audio/mp4a-latm";

			// set output format
			format = new MediaFormat();
			format.setString(MediaFormat.KEY_MIME, outputMimeType);
			format.setInteger(MediaFormat.KEY_AAC_PROFILE, AAC_PROFILE);
			format.setInteger(MediaFormat.KEY_SAMPLE_RATE, SampleRate);
			format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, ChannelCount);
			format.setInteger(MediaFormat.KEY_BIT_RATE, 64 * 1024); // desired output(!) rate for encoder
			//Log.d(TAG, "format created");

			// get and configure encoding codec
			codec = MediaCodec.createEncoderByType(outputMimeType);
			codec.configure(format, null /* surface */, null /* crypto */, MediaCodec.CONFIGURE_FLAG_ENCODE);
			MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
			boolean sawInputEOS = false;
			boolean sawOutputEOS = false;
			int noOutputCounter = 0;

			codec.start();
			int inputCount = 0;
			int maxcount = 1;
			while(inputCount < maxcount) {
				sawInputEOS = (inputCount == (maxcount - 1));
				int inputBufferIndex = codec.dequeueInputBuffer(QUEUE_TIMEOUT);
				if(inputBufferIndex >= 0) {
					ByteBuffer inputBuffer = codec.getInputBuffer(inputBufferIndex);
					if (inputBuffer != null) {
						long presentationTimeUs = ((long)1e6 * inputCount * 2048) / 44100; //System.nanoTime() / 1000;
						// put wav data into inputBuffer for encoding
						inputBuffer.put(sintab);
						// queue new input buffer to encode it
						codec.queueInputBuffer(
								inputBufferIndex,
								0,
								4096,
								presentationTimeUs,
								sawInputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0
						);
						Log.d(TAG, "encode:: queueInputBuffer ; inputCount = " + inputCount);
						inputCount++;
					} else Log.d(TAG, "encode:: inputBuffer is null!");
				}  else Log.d(TAG, "encode:: inputBufferIndex < 0");
			}

			while(true) {
				int outputBufferIndex = codec.dequeueOutputBuffer(info, QUEUE_TIMEOUT);
				//Log.d(TAG, "encode:: dequeueOutputBuffer outputBufferIndex : " + outputBufferIndex);
				if (outputBufferIndex >= 0) {
					if (info.size > 0) {
						//noOutputCounter = 0;
						//int outBitsSize = info.size;
						//int outPacketSize = outBitsSize + ADTS_SIZE;
						//Log.d(TAG, "encode:: getOutputBuffer");
						ByteBuffer outputBuffer = codec.getOutputBuffer(outputBufferIndex);

						if (outputBuffer != null) {
							byte[] data = new byte[info.size];
							outputBuffer.get(data);
							Log.d(TAG, "encode:: dequeueOutputBuffer: " + info.size + " : " + Arrays.toString(data));
						} else Log.d(TAG, "encode:: outputBuffer is null");
					} else Log.d(TAG, "encode:: dequeueOutputBuffer info.size <= 0 : " + info.size);

					codec.releaseOutputBuffer(outputBufferIndex, false /* render */);

					if (isEndOfStream(info)) {
						Log.d(TAG, "saw output EOS.");
						sawOutputEOS = true;
						break;
					}
				} else Log.d(TAG, "encode:: outputBufferIndex < 0: " + outputBufferIndex);
			}



		} catch (Exception e) {
			Log.e(TAG, "error during encoding: " + e);

		} finally {
			if (codec != null) {
				codec.flush();
				codec.stop();
				codec.release();
			}
		}
	}


	private void encodeSong(InputStream inputStream, FileOutputStream outputStream, MediaCodec codec) {
		if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT_WATCH) {
			encodeLegacyStyle(inputStream, outputStream, codec);
		} else {
			encodeLollipopStyle(inputStream, outputStream, codec);
		}
	}

	@SuppressWarnings("deprecation")
	@TargetApi(Build.VERSION_CODES.KITKAT_WATCH)
	private void encodeLegacyStyle(InputStream inputStream, FileOutputStream outputStream, MediaCodec codec) {
		//Log.d(TAG, "encodeLegacyStyle started");

		MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

		boolean sawInputEOS = false;
		boolean sawOutputEOS = false;
		int noOutputCounter = 0;

		codec.start();
		ByteBuffer[] inputBuffers = codec.getInputBuffers();
		ByteBuffer[] outputBuffers = codec.getOutputBuffers();

		try {
			while (!sawOutputEOS && noOutputCounter < 50) {

				noOutputCounter++;

				// fill codec input buffers with wav data
				if (!sawInputEOS) {

					// get index of free input buffer from codec
					int inputBufferIndex = codec.dequeueInputBuffer(QUEUE_TIMEOUT);

					if (inputBufferIndex >= 0) {

						// get free input buffer from codec
						byte[] inputBuffer = inputBuffers[inputBufferIndex].array();

						// read wav data into byte array
						final int bufferSize = inputBuffer.length;
						int sampleSize = inputStream.read(inputBuffer, 0, bufferSize);

						long presentationTimeUs = System.nanoTime();

						if (sampleSize < 0) {
							//Log.d(TAG, "saw input EOS.");
							sawInputEOS = true;
							sampleSize = 0;
						}

						// queue new input buffer to encode it
						codec.queueInputBuffer(
								inputBufferIndex,
								0,
								sampleSize,
								presentationTimeUs,
								sawInputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0
						);
					}
				}

				// see if codec has encoded data in a new output buffer
				int outputBufferIndex = codec.dequeueOutputBuffer(info, QUEUE_TIMEOUT);
				if (outputBufferIndex >= 0) {

					if (info.size > 0) {
						noOutputCounter = 0;
					}

					// prepare output buffer including ADTS header
					int outBitsSize = info.size;
					int outPacketSize = outBitsSize + ADTS_SIZE;
					ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];

					if (outputBuffer != null) {
						// add encoded data to file
						drainOutputBuffer(outputStream, info, outBitsSize, outPacketSize, outputBuffer);
					}

					codec.releaseOutputBuffer(outputBufferIndex, false);

					if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
						//Log.d(TAG, "saw output EOS.");
						sawOutputEOS = true;
					}

				} else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
					outputBuffers = codec.getOutputBuffers();

				} else {
					//Log.d(TAG, "dequeueOutputBuffer returned " + outputBufferIndex);

				}
			}
		} catch (IOException e) {
			Log.e(TAG, "Error while encoding: " + e);
		}
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private void encodeLollipopStyle(InputStream inputStream, FileOutputStream outputStream, MediaCodec codec) {
		//Log.d(TAG, "encodeLollipopStyle started");

		MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

		boolean sawInputEOS = false;
		boolean sawOutputEOS = false;
		int noOutputCounter = 0;
		int numOutBuf = 0;

		codec.start();

		try {
			while (!sawOutputEOS && noOutputCounter < 50) {

				noOutputCounter++;

				// fill codec input buffers with wav data
				if (!sawInputEOS) {

					// get index of free input buffer from codec
					int inputBufferIndex = codec.dequeueInputBuffer(QUEUE_TIMEOUT);

					if (inputBufferIndex >= 0) {

						// get free input buffer from codec
						ByteBuffer inputBuffer = codec.getInputBuffer(inputBufferIndex);

						if (inputBuffer != null) {

							// read wav data into byte array
							final int bufferSize = inputBuffer.capacity();
							byte[] buffer = new byte[bufferSize];
							int bytesRead = inputStream.read(buffer, 0, bufferSize);

							long presentationTimeUs = System.nanoTime();

							if (bytesRead < 0) {
								//Log.d(TAG, "saw input EOS.");
								sawInputEOS = true;
								byte[] zerobuf = new byte[2048];
								Arrays.fill(zerobuf, (byte)0);
								inputBuffer.put(zerobuf);
								bytesRead = 2048;
							} else {
								// put wav data into inputBuffer for encoding
								ByteArrayOutputStream outStream = new ByteArrayOutputStream(bufferSize);
								outStream.write(buffer, 0, bytesRead);
								inputBuffer.put(outStream.toByteArray());
								//Log.d(TAG, "encodeLollipopStyle:: bytes got from input buffer: " + bytesRead /*+ " : " + Arrays.toString(outStream.toByteArray())*/);

							}

							// queue new input buffer to encode it
							codec.queueInputBuffer(
									inputBufferIndex,
									0,
									bytesRead,
									/*presentationTimeUs*/0,
									sawInputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0
							);
							//Log.d(TAG, "encodeLollipopStyle:: bytes to queueInputBuffer: " + bytesRead);
						}
					}
				}


				// see if codec has encoded data in a new output buffer
				int outputBufferIndex = codec.dequeueOutputBuffer(info, QUEUE_TIMEOUT);
				if (outputBufferIndex >= 0) {

					if ((info.size > 0) && !isCodecInfo(info)) {
						noOutputCounter = 0;
					//}
						// prepare output buffer including ADTS header
						int outBitsSize = info.size;
						int outPacketSize = outBitsSize + ADTS_SIZE;
						ByteBuffer outputBuffer = codec.getOutputBuffer(outputBufferIndex);

						if ((outputBuffer != null) && (numOutBuf >= 2)) {
							// add encoded data to file
							drainOutputBuffer(outputStream, info, outBitsSize, outPacketSize, outputBuffer);
						}
						numOutBuf++;
					}

					codec.releaseOutputBuffer(outputBufferIndex, false /* render */);

					if (isEndOfStream(info)) {
						//Log.d(TAG, "saw output EOS.");
						sawOutputEOS = true;
					}
				}
			}

		} catch (FileNotFoundException e) {
			Log.e(TAG, "FilePath not found: " + e);
		} catch (IOException e) {
			Log.e(TAG, "Could not close FileInputStream: " + e);
		}
	}

	/**
	 * extracts the packet from the outputBuffer, adds an ADTS header and appends the encoded data to
	 * the outputStream (i.e. the encoded aac file).
	 */
	@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
	private void drainOutputBuffer(FileOutputStream outputStream, MediaCodec.BufferInfo info, int outBitsSize, int outPacketSize, ByteBuffer outputBuffer) {
		// set position and limit of outputBuffer
		outputBuffer.position(info.offset);
		outputBuffer.limit(info.offset + outBitsSize);
		//Log.d(TAG, "drainOutputBuffer outBitsSize: " + outBitsSize + " outPacketSize: " + outPacketSize + " info.flags: " + info.flags + " info.size: " + info.size);
		try {
			// prepare byte array containing encoded data
			byte[] data = new byte[outPacketSize];

			// add ADTS header to data packet
			addADTStoPacket(data, outPacketSize);

			// place encoded audio + ADTS header into data array
			outputBuffer.get(data, ADTS_SIZE, outBitsSize);

			// update outputBuffer position
			//outputBuffer.position(info.offset);

			// only write real audio data (exclude codec info and EOS info)
			if (!isCodecInfo(info) /*&& !isEndOfStream(info)*/) {
			//if (info.flags == 0) {
				outputStream.write(data, 0, outPacketSize);
				//Log.d(TAG, "drainOutputBuffer outputStream.write  outPacketSize: " + outPacketSize + " : " + Arrays.toString(data));
			}

		} catch (IOException e) {
			Log.e(TAG, "failed writing bit stream data to file");
			e.printStackTrace();

		}

		outputBuffer.clear();
	}

	@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
	private boolean isEndOfStream(MediaCodec.BufferInfo info) {
		return (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
	}

	@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
	private boolean isCodecInfo(MediaCodec.BufferInfo info) {
		return (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
	}

	/**
	 * Add ADTS header at the beginning of each and every AAC packet.
	 * This is needed as MediaCodec encoder generates a packet of raw
	 * AAC data.<br/>
	 * Note the packetLength must count in the ADTS header itself.
	 **/
	private void addADTStoPacket(byte[] packet, int packetLength) {
		int profile = AAC_PROFILE;
		int chanCfg = ChannelCount;

		// 0: 96000 Hz
		// 1: 88200 Hz
		// 2: 64000 Hz
		// 3: 48000 Hz
		// 4: 44100 Hz
		// 5: 32000 Hz
		int freqIdx = 4;
		if(SampleRate == 96000) 		freqIdx = 0;
		else  if(SampleRate == 88200) 	freqIdx = 1;
		else  if(SampleRate == 64000) 	freqIdx = 2;
		else  if(SampleRate == 48000) 	freqIdx = 3;
		else  if(SampleRate == 32000) 	freqIdx = 5;

		// fill in ADTS data
		packet[0] = (byte) 0xFF;
		packet[1] = (byte) 0xF9;
		packet[2] = (byte) (((profile - 1) << 6) + (freqIdx << 2) + (chanCfg >> 2));
		packet[3] = (byte) (((chanCfg & 3) << 6) + (packetLength >> 11));
		packet[4] = (byte) ((packetLength & 0x7FF) >> 3);
		packet[5] = (byte) (((packetLength & 7) << 5) + 0x1F);
		packet[6] = (byte) 0xFC;
	}
}

