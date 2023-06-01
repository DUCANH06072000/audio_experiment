package com.example.record_audio;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.audiofx.Visualizer;
import android.util.Log;
import android.widget.Advanceable;

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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
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
    private Visualizer visualizer;
    String outputfile = "";

    public AudioRecorder(Context context, Activity activity) {
        this.context = context;
        if (ContextCompat.checkSelfPermission(activity, permissions[0]) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(activity, permissions[1]) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, permissions, 123);
        }
    }

    /**
     * bắt đầu ghi âm
     */
    public void startRecording(int bitRate) {
        String outputPath = context.getExternalFilesDir(null) + "/Audio";
        File file = new File(outputPath);
        if (file.exists()) {
            file.delete();
        }
        recorder = new MediaRecorder();
        random = new Random();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        recorder.setAudioChannels(1); // chế độ mono
        recorder.setAudioEncodingBitRate(bitRate);
        recorder.setAudioSamplingRate(16000);
        if (!isRecording) {
            recorder.setOutputFile(getOutputFilePath());
            try {
                recorder.prepare();
                recorder.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
            isRecording = true;
        }
    }

    /**
     * dừng ghi âm
     * /
     */
    public void stopRecording() {
        if (isRecording) {
            recorder.stop();
            recorder.reset();
            recorder.release();
            recorder = null;
            isRecording = false;
            convertMp4ToWav();
            File file = new File(getOutputFilePath());
            file.delete();
        } else {
            Log.e("FIle rỗng", "Chưa có file ghi âm");
        }
    }

    /// đọc file thanh âm thanh
    public void readFileAudio(ListenerEntry listenerEntry) {
        byte[] audioData = null;
        File file = new File(context.getExternalFilesDir(null), "thuamjava.util.Random@299366e.wav");
        Log.e("Tag", file.getPath());
        try {
            FileInputStream fileInputStream = new FileInputStream(file);
            audioData = new byte[(int) file.length()];
            try {
                fileInputStream.read(audioData);
                long dataSize = file.length();
                double audioDuration = dataSize / (16000 * 1 * 2);
                Log.e("Audio chưa lọc âm thanh sau khi chưa lọc",Arrays.toString(audioData));
              //  convertFrameDataToAudio(audioData);
                calculateSoundIntensity(audioData, 1024, 512, audioDuration, listenerEntry);
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
    private void calculateSoundIntensity(byte[] audioData, int frameSize, int frameStride, double audioDuration, ListenerEntry listenerEntry) {
        int numFrames = (audioData.length - frameSize) / frameStride + 1;
        Log.e("tag", String.valueOf(audioData.length));
        double frameDuration = audioDuration / numFrames;
        double startTime = 0;
        double endTime = 0;
        byte[] frameData = null;
        double sum = 0;
        boolean checkWord = false;
        Log.e("Thời lượng của các Frame", String.valueOf(frameDuration));
        double[] soundIntensity = new double[numFrames];
        double[] soundFrequency = new double[numFrames];
        for (int i = 0; i < numFrames; i++) {
            int startIndex = i * frameStride;
            int endIndex = startIndex + frameSize;
            frameData = Arrays.copyOfRange(audioData, startIndex, endIndex);
            startTime = i * frameDuration;
             endTime = startTime + frameDuration;
            double intensity = calculateFrameSoundIntensity(frameData);
            double frequency = calculateFrequency(frameData);
            soundIntensity[i] = intensity;
            soundFrequency[i] = frequency;
            sum+=soundIntensity[i];
            listenerEntry.onListenerEntry(soundIntensity[i], startTime, numFrames);
        }
        Log.e("Cường độ âm thanh",Arrays.toString(soundIntensity));
        double average = sum/numFrames;
        Log.e("Trung bình công:",String.valueOf(average));
        for (int i =0;i<soundIntensity.length;i++){
            if (soundIntensity[i]>average)
            {
                System.out.println("Frame " + i + ": Start Time = " + startTime + "s, End Time = " + endTime + "s" + "  Cường độ âm thanh theo từng khoảng:" + soundIntensity[i]+" Tần số âm thanh:"+ soundFrequency[i]);
            }
        }
    }


    /**
     * Chuyển đổi từ FrameData sang audio
     */
    private void convertFrameDataToAudio(byte[] frameData) {
        int streamType = AudioManager.STREAM_MUSIC;
        int sampleRate = 16000; // Tần số mẫu âm thanh (Hz)
        int channelConfig = AudioFormat.CHANNEL_OUT_MONO; // Cấu hình âm thanh stereo
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT; // Định dạng âm thanh PCM 16-bit
        int bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat);
        AudioTrack audioTrack = new AudioTrack(streamType, sampleRate, channelConfig, audioFormat,
                bufferSize, AudioTrack.MODE_STREAM);
        audioTrack.setPlaybackRate(sampleRate);
        audioTrack.play();
        audioTrack.write(frameData, 0, frameData.length);
        // Chuẩn bị và bắt đầu phát

        Thread playbackThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    audioTrack.stop();
                    audioTrack.release();
                }
            }
        });

        playbackThread.start();

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
        double[] data = new double[frameData.length / 2];
        for (int i = 0; i < frameData.length; i += 2) {
            short sample = (short) ((frameData[i] & 0xFF) | (frameData[i + 1] << 8));
            data[i / 2] = sample / 32768.0; // Chuyển đổi giá trị mẫu sang phạm vi [-1.0, 1.0]
        }
        FastFourierTransformer transformer = new FastFourierTransformer(DftNormalization.STANDARD);
        Complex[] fftResult = transformer.transform(data, TransformType.FORWARD);
        int sampleRate = 16000;
        int numSamples = frameData.length;
        // tìm vị trí của phổ âm thanh
        int maxIndex = -1;
        double maxAmplitude = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < numSamples / 2; i++) {
            double amplitude = fftResult[i].abs();
            if (amplitude > maxAmplitude) {
                maxAmplitude = amplitude;
                maxIndex = i;
            }
        }
        double frequency = (double) maxIndex * sampleRate / numSamples;
        return frequency;
    }


    /**
     * call api chuyển đổi giọng nói thành dạng văn bản
     */
    public void callApi(ListenerMessage listenerMessage) {
        File file = new File(outputfile);
        RequestBody requestBody = RequestBody.create(MediaType.parse("multipart/form-data"), file);
        MultipartBody.Part filePart = MultipartBody.Part.createFormData("file", file.getName(), requestBody);
        Call<ResponseBody> call = ApiService.apiService.updateLoadFile(filePart);
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
        public void onListenerEntry(double soundIntensity, double duration, int numFrames);
    }
}
