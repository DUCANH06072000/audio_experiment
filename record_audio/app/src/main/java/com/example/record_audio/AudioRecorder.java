package com.example.record_audio;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.audiofx.Visualizer;
import android.util.Log;
import android.widget.Advanceable;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.arthenica.mobileffmpeg.FFmpeg;
import com.github.mikephil.charting.data.Entry;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;
import org.jtransforms.fft.DoubleFFT_1D;

import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import kotlin.jvm.internal.markers.KMutableMap;
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
    private MediaRecorder recorder;
    private Random random;
    private String[] permissions = {Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE};

    private double[] soundIntensity = null; /// cường độ âm thanh theo thời gian của
    private double[] soundFrequency = null; /// tần sô âm thanh theo thời gian
    private double frameDuration = 0; /// thời gian của từng Frame

    private double average = 0; /// trung bình cường độ âm thanh
    String outputfile = "";
    double audioDuration = 0.0;

    private AudioRecord audioRecord;

    private int streamType = AudioManager.STREAM_MUSIC;
    private int sampleRate = 16000; // Tần số mẫu âm thanh (Hz)
    private int channelConfig_in = AudioFormat.CHANNEL_IN_MONO;
    private int audioFormat = AudioFormat.ENCODING_PCM_16BIT; // Định dạng âm thanh PCM 16-bit

    byte[] audioData = null;


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
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig_in, audioFormat, bufferSize);
        audioRecord.startRecording();
        isRecording = true;
        long startTime = System.currentTimeMillis();
        new Thread(new Runnable() {
            @Override
            public void run() {
                int offset = 0;
                while (isRecording) {
                    int bytesRead = audioRecord.read(buffer, 0, bufferSize);
                    if (offset + bytesRead > audioData.length) {
                        // Resize audioData if necessary
                        byte[] resizedData = new byte[offset + bytesRead];
                        System.arraycopy(audioData, 0, resizedData, 0, offset);
                        audioData = resizedData;
                    }
                    System.arraycopy(buffer, 0, audioData, offset, bytesRead);
                    offset += bytesRead;
                    long currentTime = System.currentTimeMillis();
                    long elapsedTime = currentTime - startTime;
                    audioDuration = (double) elapsedTime / 1000;
                    listenerEntry.onListenerEntry(calculateFrameSoundIntensity(buffer), audioDuration);
                }
            }
        }).start();
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
        Log.e("audioData",Arrays.toString(audioData));
        audioRecord.release();
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
    Log.e("FrameData",Arrays.toString(frameData));
// Ghi dữ liệu âm thanh vào AudioTrack từ mảng byte
        audioTrack.write(audioData, 0, audioData.length);

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
                    filterIntensity(audioData, soundIntensity, average, 512, 1024, frameDuration);
                    break;
                case 3:
                    filterFrequency(audioData, soundFrequency, 512, 1024, frameDuration);
                    break;
            }
        } else {
            Toast.makeText(context, "Chưa có âm thanh", Toast.LENGTH_SHORT).show();
        }
    }

    /// đọc file thanh âm thanh
    public void readFileAudio() {
        File file = new File(context.getExternalFilesDir(null), "thuamjava.util.Random@6739123.wav");
        Log.e("Tag", file.getPath());
        try {
            FileInputStream fileInputStream = new FileInputStream(file);
            audioData = new byte[(int) file.length()];
            try {
                fileInputStream.read(audioData);
                Log.e("TEST", Arrays.toString(audioData));
                long dataSize = file.length();
                convertFrameDataToAudio(audioData);
                Log.e("Tag", Arrays.toString(audioData));
                audioDuration = dataSize / (16000 * 1 * 2);
                fileInputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }


    /**
     * chia nhỏ âm thanh thnahf các Frame nhỏ mỗi frame có độ lớn là 1024 byte và có FraneSize 512
     */
    public void calculateSound(ListenerEntry listenerEntry) {
        int numFrames = (audioData.length - 1024) / 512 + 1;
        Log.e("tag", String.valueOf(audioData.length));
        frameDuration = audioDuration / numFrames;
        double startTime = 0;
        double endTime = 0;
        byte[] frameData = null;
        double sum = 0;
        Log.e("Thời lượng của các Frame", String.valueOf(frameDuration));
        soundIntensity = new double[numFrames];
        soundFrequency = new double[numFrames];
        for (int i = 0; i < numFrames; i++) {
            int startIndex = i * 512;
            int endIndex = startIndex + 1024;
            frameData = Arrays.copyOfRange(audioData, startIndex, endIndex);
            startTime = i * frameDuration;
            endTime = startTime + frameDuration;
            double intensity = calculateFrameSoundIntensity(frameData);
            double frequency = calculateFrequency(frameData);
            soundIntensity[i] = intensity;
            soundFrequency[i] = frequency;
            sum += soundIntensity[i];
            //   listenerEntry.onListenerEntry(soundIntensity[i], startTime, numFrames);
            System.out.println("Frame " + i + ": Start Time = " + startTime + "s, End Time = " + endTime + "s" + "  Cường độ âm thanh theo từng khoảng:" + soundIntensity[i] + " Tần số âm thanh:" + soundFrequency[i]);
        }
        average = sum / numFrames;
    }


    /**
     * Lọc tầm số âm thanh
     */
    private void filterFrequency(byte[] audioData, double[] soundFrequency, int frameStride, int frameSize, double frameDuration) {
        int count = 0;
        if (soundFrequency != null) {
            for (int j = 0; j < soundFrequency.length; j++)
                if ((soundFrequency[j] >= 80 && soundFrequency[j] <= 450) || soundFrequency[j] == 0) {
                    count++;
                }
            byte[] mergedFrame = new byte[count * frameSize];
            byte[] voiceFrame = null;
            int mergedIndex = 0;
            for (int j = 0; j < soundFrequency.length; j++)
                if ((soundFrequency[j] >= 80 && soundFrequency[j] <= 450) || soundFrequency[j] == 0) {
                    int startIndexFrame = j * frameStride;
                    int endIndexFrame = startIndexFrame + frameSize;
                    double startTime = j * frameDuration;
                    double endTime = startTime + frameDuration;
                    voiceFrame = Arrays.copyOfRange(audioData, startIndexFrame, endIndexFrame);
                    System.arraycopy(voiceFrame, 0, mergedFrame, mergedIndex * frameSize, frameSize);
                    mergedIndex++;
                }
            convertFrameDataToAudio(mergedFrame);
            saveToFile("tan_so_am_thanh.wav", mergedFrame);
        } else {
            Toast.makeText(context, "Tần số rỗng", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * lọc cường độ âm thanh
     */
    private void filterIntensity(byte[] audioData, double[] soundIntensity, double average, int frameStride, int frameSize, double frameDuration) {
        if (soundIntensity != null) {
            int count = 0;
            for (int j = 0; j < soundIntensity.length; j++)
                if (soundIntensity[j] >= average) {
                    count++;
                }
            byte[] mergedFrame = new byte[count * frameSize];
            byte[] voiceFrame = null;
            int mergedIndex = 0;
            int totalFrames = audioData.length / frameStride;
            Log.e(" Size AudioData", String.valueOf(audioData.length));
            Log.e("tổng số khung", String.valueOf(totalFrames));
            for (int j = 0; j < soundIntensity.length; j++)
                if (soundIntensity[j] >= average) {
                    int startIndexFrame = j * frameStride;
                    int endIndexFrame = startIndexFrame + frameSize;
                    double startTime = j * frameDuration;
                    double endTime = startTime + frameDuration;
                    voiceFrame = Arrays.copyOfRange(audioData, startIndexFrame, endIndexFrame);
                    System.arraycopy(voiceFrame, 0, mergedFrame, mergedIndex * frameSize, frameSize);
                    mergedIndex++;
                    System.out.println("Frame " + j + ": Start Time = " + startTime + "s, End Time = " + endTime + "s" + "  Cường độ âm thanh theo từng khoảng:" + soundIntensity[j]);
                } else {
                }
            convertFrameDataToAudio(mergedFrame);
            saveToFile("cuong_do_am_thanh.wav", mergedFrame);
        } else {
            Toast.makeText(context, "Cường độ âm thanh rỗng", Toast.LENGTH_SHORT).show();
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
        double[] audioData = new double[numSamples];

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
        for (int i = 0; i < numSamples / 2; i++) {
            double amplitude = fftResult[i].abs();
            if (amplitude > maxAmplitude) {
                maxAmplitude = amplitude;
                maxIndex = i;
            }
        }
        // Tính tần số tương ứng với vị trí của phổ âm thanh tối đa
        double frequency = (double) maxIndex * 16000 / numSamples;
        return frequency;
    }


    /**
     * call api chuyển đổi giọng nói thành dạng văn bản
     */
    public void callApi(ListenerMessage listenerMessage, String domain, int callApiSelected) {
        File file = null;
        switch (callApiSelected) {
            case 1:
                file = new File(outputfile);
                break;
            case 2:
                file = new File(context.getExternalFilesDir(null), "cuong_do_am_thanh.wav");
                break;
            case 3:
                file = new File(context.getExternalFilesDir(null), "tan_so_am_thanh.wav");
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
                    String content = "";
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
    private void saveToFile(String nameFile, byte[] frameData) {
        try {
            FileOutputStream dataOutputStream = new FileOutputStream(new File(context.getExternalFilesDir(null), nameFile));
            try {
                dataOutputStream.write(frameData, 0, frameData.length);
                dataOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }


    /**
     * chuyển đổi từ mp4 sang wav
     */
    private void convertMp4ToWav() {
        String inputPath = getOutputFilePath();
        outputfile = inputPath.replace(".mp4", ".wav");
        String[] ffmpegCommand = {
                "-i",
                inputPath,
                "-acodec",
                "pcm_s16le",
                "-ac",
                "1",
                "-ar",
                "16000",
                outputfile
        };
        FFmpeg.execute(ffmpegCommand);
    }

    /**
     * lấy file path
     */
    private String getOutputFilePath() {
        String fileName = "thuam" + String.valueOf(random) + ".mp4";
        File file = new File(context.getExternalFilesDir(null), fileName);
        String filePath = file.getAbsolutePath();
        Log.e("tag", filePath);
        return filePath;
    }


    public interface ListenerMessage {
        public void onListenerTime(String time);

        public void onListenerMessage(String message);
    }

    public interface ListenerEntry {
        public void onListenerEntry(double soundIntensity, double duration);
    }
}
