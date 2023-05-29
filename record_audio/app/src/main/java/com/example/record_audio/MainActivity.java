package com.example.record_audio;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest.permission;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;

import com.example.record_audio.databinding.ActivityMainBinding;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;

import android.Manifest;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private boolean isRecording = false;
    private MediaRecorder recorder;
    private AudioRecorder audioRecorder;
    private List<Entry> entries = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        audioRecorder = new AudioRecorder(this, MainActivity.this);
        binding.btnRecord256kbps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                audioRecorder.startRecording(256);
            }
        });
//        binding.chart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
//            @Override
//            public void onValueSelected(Entry e, Highlight h) {
//                float x = e.getX();
//                float y = e.getY();
//                Toast.makeText(MainActivity.this, "X: " + x + "\nY: " + y, Toast.LENGTH_SHORT).show();
//            }
//
//            @Override
//            public void onNothingSelected() {
//
//            }
//        });
        binding.btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                entries.clear();
                audioRecorder.stopRecording();
                audioRecorder.readFileAudio(new AudioRecorder.ListenerEntry() {
                    @Override
                    public void onListenerEntry(double soundIntensity, double duration,int numFrame) {
                        for (int i =0;i<numFrame;i++){
                            entries.add(new Entry((float) duration,(float) soundIntensity));
                        }
                        LineDataSet dataSet = new LineDataSet(entries, "Cường độ âm thanh");
                        LineData lineData = new LineData(dataSet);
                        binding.chart.setData(lineData);
                        binding.chart.invalidate();
                    }
                });
            }
        });
        binding.btnCallApi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                audioRecorder.callApi(new AudioRecorder.ListenerMessage() {
                    @Override
                    public void onListenerTime(String time) {
                        binding.textTime.setText(time);
                    }

                    @Override
                    public void onListenerMessage(String message) {
                        binding.textContent.setText(message);
                        Log.e("activity", message);
                    }
                });
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

}
