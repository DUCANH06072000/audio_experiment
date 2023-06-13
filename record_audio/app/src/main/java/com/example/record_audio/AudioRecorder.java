package com.example.record_audio;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AudioRecorder {
    private boolean isRecording = false;
    private Context context;
    private Random random;
    private String[] permissions = {Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE};

    private double[] soundIntensity = null; /// cường độ âm thanh theo thời gian của
    private double[] soundFrequency = null; /// tần sô âm thanh theo thời gian
    double audioDuration = 0.0;

    private AudioRecord audioRecord;
    private int sampleRate = 16000; // Tần số mẫu âm thanh (Hz)
    private int channelConfig_in = AudioFormat.CHANNEL_IN_MONO;
    private int audioFormat = AudioFormat.ENCODING_PCM_16BIT; // Định dạng âm thanh PCM 16-bit

    byte[] audioData = null;
    byte[] audioDataIntensity = null;
    byte[] audioDataFrequency = null;

    byte[] audioDataMixFilter = null;
    int index = 0;

    public AudioRecorder(Context context, Activity activity) {
        this.context = context;
        if (ContextCompat.checkSelfPermission(activity, permissions[0]) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(activity, permissions[1]) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, permissions, 123);
        }
    }

    /***
     * stream record
     */
    public void startStreamRecord(ListenerEntry listenerEntry) {
        int bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig_in, audioFormat);
        if (isRecording) return;
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        int totalBufferSize = bufferSize * (sampleRate / bufferSize) * 10;
        byte[] buffer = new byte[bufferSize];
        audioData = new byte[totalBufferSize];
        audioDataIntensity = new byte[totalBufferSize];
        audioDataFrequency = new byte[totalBufferSize];
        audioDataMixFilter = new byte[totalBufferSize];
        soundIntensity = new double[bufferSize];
        soundFrequency = new double[bufferSize];
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig_in, audioFormat, bufferSize);
        audioRecord.startRecording();
        isRecording = true;
        long startTime = System.currentTimeMillis();
        new Thread(new Runnable() {
            @Override
            public void run() {
                int offset = 0;
                while (isRecording) {
                    long currentTime = System.currentTimeMillis();
                    long elapsedTime = currentTime - startTime;
                    audioDuration = (double) elapsedTime / 1000;
                    int bytesRead = audioRecord.read(buffer, 0, bufferSize);
                    readAudio(offset, bytesRead, buffer);
                    offset += bytesRead;
                    index++;
                    soundIntensity[index] = calculateFrameSoundIntensity(buffer);  // Kiểm tra cường độ âm thanh
                    soundFrequency[index] = calculateFrequency(buffer);
                    cutAudio(soundIntensity[index], soundIntensity[index - 1]);
//                    if (audioDuration >= 0.0 && audioDuration <= 0.8) {
//                        filterAudioIntensity(offset, bytesRead, buffer, audioDataMixFilter, "mix");
//                    }
//                    if (soundIntensity[index] > 60) {
//                        filterAudioIntensity(offset, bytesRead, buffer, audioDataIntensity, "cuongdo");
//                    }
//                    if (soundFrequency[index] > 100 && soundFrequency[index] < 1000) {
//                        filterAudioIntensity(offset, bytesRead, buffer, audioDataFrequency, "tanso");
//                    }
//                    if (soundIntensity[index] > 50 && (soundFrequency[index] > 100 && soundFrequency[index] < 600)) {
//                        filterAudioIntensity(offset, bytesRead, buffer, audioDataMixFilter, "mix");
//                    }
                    listenerEntry.onListenerEntry(soundIntensity[index], audioDuration, soundFrequency[index]);
                }
            }
        }).start();
    }


    private void cutAudio(double startIndex, double endIndex) {
        if (startIndex > endIndex) {
           Log.e("TAG","Dãy tăng");
        } else {
          Log.e("TAG","Dãy giảm");
        }
    }


    /**
     * dừng stream record
     */
    public void stopStreamRecord() {
        if (!isRecording) return;
        isRecording = false;
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        audioRecord.stop();
        audioRecord.release();
    }

    /**
     * đọc dữ liệu
     */
    private void readAudio(int offset, int bytesRead, byte[] buffer) {
        if (offset + bytesRead > audioData.length) {
            byte[] resizedData = new byte[offset + bytesRead];
            System.arraycopy(audioData, 0, resizedData, 0, offset);
            audioData = resizedData;
        }
        System.arraycopy(buffer, 0, audioData, offset, bytesRead);
        convertToWav(audioData, "audio");
    }

    private void filterAudioIntensity(int offset, int bytesRead, byte[] buffer,
                                      byte[] data, String filename) {
        int requiredLength = offset + bytesRead;
        if (requiredLength > data.length) {
            byte[] resizedData = new byte[requiredLength];
            System.arraycopy(data, 0, resizedData, 0, data.length);
            data = resizedData;
        }
        System.arraycopy(buffer, 0, data, offset, bytesRead);
        convertToWav(data, filename);
    }

    /**
     * Chuyển đổi từ FrameData sang audio
     */
    private void convertFrameDataToAudio(byte[] frameData) {
        // Khởi tạo AudioTrack với các tham số cần thiết
        int sampleRate = 16000; // Tốc độ mẫu âm thanh
        int channelConfig = AudioFormat.CHANNEL_OUT_MONO; // Kênh âm thanh đơn
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT; // Định dạng âm thanh PCM 16-bit
        int bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat);
        AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, channelConfig,
                audioFormat, bufferSize, AudioTrack.MODE_STREAM);
// Chuẩn bị và phát âm thanh
        audioTrack.play();
        Log.e("FrameData", Arrays.toString(frameData));
// Ghi dữ liệu âm thanh vào AudioTrack từ mảng byte
        audioTrack.write(frameData, 0, frameData.length);
// Dừng và giải phóng tài nguyên
        audioTrack.stop();
        audioTrack.release();
    }

    /**
     * phát âm thanh được thu
     */
    public void startAudio(int position) {
        if (audioData != null) {
            switch (position) {
                case 1:
                    convertFrameDataToAudio(audioData);
                    break;
                case 2:
                    convertFrameDataToAudio(audioDataIntensity);
                    break;
                case 3:
                    convertFrameDataToAudio(audioDataFrequency);
                    break;
            }
        } else {
            Toast.makeText(context, "Chưa có âm thanh", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * tính cường độ âm thanh của từng frame
     */
    private double calculateFrameSoundIntensity(byte[] frameData) {
        double[] data = new double[frameData.length / 2];
        for (int i = 0; i < frameData.length; i += 2) {
            short sample = (short) ((frameData[i] & 0xFF) | (frameData[i + 1] << 8));
            data[i / 2] = sample / 32768.0; // Chuyển đổi giá trị mẫu sang phạm vi [-1.0, 1.0]
        }
        double sumOfSquares = 0.0;
        for (double sample : data) {
            sumOfSquares += sample * sample;
        }
        double rms = Math.sqrt(sumOfSquares / data.length);
        double soundIntensity = 20 * Math.log10(rms / 0.00002);
        return soundIntensity;
    }


    /**
     * tính tần số âm thanh của từng Frame
     */
    private double calculateFrequency(byte[] frameData) {
        int numSamples = frameData.length / 2;
        int paddedLength = 1 << (32 - Integer.numberOfLeadingZeros(numSamples - 1));
        double[] audioData = new double[paddedLength];

// Chuyển đổi dữ liệu mẫu âm thanh từ byte thành double
        for (int i = 0; i < numSamples; i++) {
            short sample = (short) ((frameData[i * 2] & 0xFF) | (frameData[i * 2 + 1] << 8));
            audioData[i] = sample / 32768.0; // Chuyển đổi giá trị mẫu sang phạm vi [-1.0, 1.0]
        }
        FastFourierTransformer transformer = new FastFourierTransformer(DftNormalization.STANDARD);
        Complex[] fftResult = transformer.transform(audioData, TransformType.FORWARD);

// Tìm vị trí và amplitudes của phổ âm thanh
        int maxIndex = -1;
        double maxAmplitude = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < paddedLength / 2; i++) {
            double amplitude = fftResult[i].abs();
            if (amplitude > maxAmplitude) {
                maxAmplitude = amplitude;
                maxIndex = i;
            }
        }
        double frequency = (double) maxIndex * 16000 / paddedLength; // Tính tần số tương ứng với vị trí của phổ âm thanh tối đa
        return frequency;
    }


    /**
     * call api chuyển đổi giọng nói thành dạng văn bản
     */
    public void callApi(ListenerMessage listenerMessage, String domain, int callApiSelected) {
        File file = null;
        switch (callApiSelected) {
            case 1:
                file = new File(context.getExternalFilesDir(null), "audio.wav");
                break;
            case 2:
                file = new File(context.getExternalFilesDir(null), "cuongdo.wav");
                break;
            case 3:
                file = new File(context.getExternalFilesDir(null), "tanso.wav");
                break;
        }
        RequestBody requestBody = RequestBody.create(MediaType.parse("multipart/form-data"), file);
        MultipartBody.Part filePart = MultipartBody.Part.createFormData("file", file.getName(), requestBody);
        Call<ResponseBody> call = ApiService.getApiService(domain).updateLoadFile(filePart);
        long start = System.currentTimeMillis();
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    long end = System.currentTimeMillis();
                    long duration = end - start;
                    Log.e("Thời gian call API", String.valueOf(duration) + "ms");
                    try {
                        listenerMessage.onListenerMessage(response.body().string());
                        listenerMessage.onListenerTime("Thời gian call API:" + duration + "ms");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    Log.e("Nội dung", "lỗi");
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
            }
        });
    }

    /**
     * lưu File âm thanh sau khi được xử lý
     */
    public void convertToWav(byte[] audioData, String filename) {
        File file = new File(context.getExternalFilesDir(null), filename + ".wav");
        FileOutputStream os = null;

        try {
            os = new FileOutputStream(file);

            // Header for WAV file
            writeWavHeader(os, audioData.length);

            // Write audio data to file
            os.write(audioData);

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (os != null) {
                    os.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void writeWavHeader(FileOutputStream os, int audioDataSize) throws
            IOException {
        // WAV file format header
        int channels = 1;  // Mono
        int bitsPerSample = 16;
        int sampleRate = 16000;
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        short blockAlign = (short) (channels * bitsPerSample / 8);

        byte[] header = new byte[44];
        // ChunkID (RIFF)
        header[0] = 'R';
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        // ChunkSize
        header[4] = (byte) (audioDataSize + 36);
        header[5] = (byte) ((audioDataSize + 36) >> 8);
        header[6] = (byte) ((audioDataSize + 36) >> 16);
        header[7] = (byte) ((audioDataSize + 36) >> 24);
        // Format (WAVE)
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        // Subchunk1ID (fmt)
        header[12] = 'f';
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        // Subchunk1Size
        header[16] = 16;
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        // AudioFormat
        header[20] = 1;
        header[21] = 0;
        // NumChannels
        header[22] = (byte) channels;
        header[23] = 0;
        // SampleRate
        header[24] = (byte) sampleRate;
        header[25] = (byte) (sampleRate >> 8);
        header[26] = (byte) (sampleRate >> 16);
        header[27] = (byte) (sampleRate >> 24);
        // ByteRate
        header[28] = (byte) byteRate;
        header[29] = (byte) (byteRate >> 8);
        header[30] = (byte) (byteRate >> 16);
        header[31] = (byte) (byteRate >> 24);
        // BlockAlign
        header[32] = (byte) blockAlign;
        header[33] = (byte) (blockAlign >> 8);
        // BitsPerSample
        header[34] = (byte) bitsPerSample;
        header[35] = 0;
        // Subchunk2ID (data)
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        // Subchunk2Size
        header[40] = (byte) audioDataSize;
        header[41] = (byte) (audioDataSize >> 8);
        header[42] = (byte) (audioDataSize >> 16);
        header[43] = (byte) (audioDataSize >> 24);

        os.write(header, 0, 44);
    }

    public interface ListenerMessage {
        public void onListenerTime(String time);

        public void onListenerMessage(String message);
    }

    public interface ListenerEntry {
        public void onListenerEntry(double soundIntensity, double duration, double soundFrequency);
    }
}
