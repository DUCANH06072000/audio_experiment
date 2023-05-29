package com.example.record_audio;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
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
        if (!isRecording) {///bitrate 16kb
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

    /**
     * phát âm thanh thu được
     */
    public void startSound(ListenerEntry listenerEntry) {
//        File file = new File(context.getExternalFilesDir(null), "thuamjava.util.Random@3be8940.wav");
//        MediaPlayer mediaPlayer = new MediaPlayer();
//        visualizer = new Visualizer(mediaPlayer.getAudioSessionId());
//        try {
//            mediaPlayer.setDataSource(file.getPath());
//            Log.e("Cường độ âm thanh",String.valueOf(mediaPlayer.getDuration()));
//            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
//                @Override
//                public void onPrepared(MediaPlayer mp) {
//                    visualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);
//                    visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
//                        long startTime = System.currentTimeMillis();
//
//                        @Override
//                        public void onWaveFormDataCapture(Visualizer visualizer, byte[] waveform, int samplingRate) {
//                            // Xử lý dữ liệu waveform (dạng sóng hình)
//                        }
//
//                        @Override
//                        public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
//
//
//                        }
//                    }, Visualizer.getMaxCaptureRate(), false, true);
//
//                    visualizer.setEnabled(true);
//                    mediaPlayer.start();
//                }
//            });
//            mediaPlayer.prepareAsync();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
//            @Override
//            public void onCompletion(MediaPlayer mp) {
////                visualizer.setEnabled(false);
////                visualizer.release();
//            }
//        });
    }

    private double calculateFrameSoundIntensity(byte[] frameData){
        double[] data = new double[frameData.length/2];
        for (int i =0;i<frameData.length;i+=2){
            short sample = (short) ((frameData[i] & 0xFF) | (frameData[i + 1] << 8));
            data[i / 2] = sample / 32768.0; // Chuyển đổi giá trị mẫu sang phạm vi [-1.0, 1.0]
        }

        double sumOfSquares  = 0.0;
        for (double sample: data){
            sumOfSquares += sample*sample;
        }
        double rms = Math.sqrt(sumOfSquares/ data.length);
        double soundIntensity = 20*Math.log10(rms/0.00002);
        return  soundIntensity;
    }

    private void calculateSoundIntensity(byte[] audioData,int frameSize,int frameStride,double audioDuration, ListenerEntry listenerEntry){
        int numFrames = (audioData.length - frameSize) / frameStride + 1;
        double frameDuration = audioDuration/numFrames;
        double startTime = 0;
        Log.e("Thời lượng của các Frame",String.valueOf(frameDuration));
        double[] soundIntensity = new double[numFrames];
        for (int i = 0; i < numFrames; i++) {
            int startIndex = i * frameStride;
            int endIndex = startIndex + frameSize;
            byte[] frameData = Arrays.copyOfRange(audioData, startIndex, endIndex);
             startTime= i*frameDuration;
            double endTime = startTime+frameDuration;
            double intensity = calculateFrameSoundIntensity(frameData);
            soundIntensity[i] = intensity;
            System.out.println("Frame " + i + ": Start Time = " + startTime + "s, End Time = " + endTime + "s"+ "  Cường độ âm thanh theo từng khoảng:"+soundIntensity[i]);
            listenerEntry.onListenerEntry(soundIntensity[i],startTime,numFrames);
        }
    }
    public void readFileAudio(ListenerEntry listenerEntry){
        byte[] audioData =null;
        File file = new File(outputfile);
        Log.e("Tag",String.valueOf(file.length()));
        try {
            FileInputStream fileInputStream = new FileInputStream(file);
            audioData = new byte[(int) file.length()];
            try {
                fileInputStream.read(audioData);
                long dataSize = file.length();
                double audioDuration = dataSize/(16000*1*2);
               calculateSoundIntensity(audioData,1024,512,audioDuration,listenerEntry);
                fileInputStream.close();
            } catch (IOException e) {
               e.printStackTrace();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

    }

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
        public void onListenerEntry(double soundIntensity, double duration,int numFrames);
    }
}
