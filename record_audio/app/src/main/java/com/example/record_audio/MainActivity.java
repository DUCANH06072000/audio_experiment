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
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
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
    private int selectedIndex = 0;
    private  int apiSelected =0;
    private AudioRecorder audioRecorder;
    private List<Entry> entries = new ArrayList<>();
    private  List<Entry> entriesFrequency = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        initEvent();
        initSpinnerApi();
        audioRecorder = new AudioRecorder(this, MainActivity.this);
        binding.btnRecord256kbps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isRecording) {
                    entries.clear();
                    entriesFrequency.clear();
                    audioRecorder.startStreamRecord(new AudioRecorder.ListenerEntry() {
                        @Override
                        public void onListenerEntry(double soundIntensity, double duration,double soundFrequency) {
                            Log.e("Cường độ âm thanh",String.valueOf(soundIntensity)+" Thời gian  "+String.valueOf(duration));
                            for (int i =0;i<500;i++){
                            //    entries.add(new Entry((float) duration,(float)soundIntensity));
                                entriesFrequency.add(new Entry((float) duration,(float) soundFrequency));
                            }
//                            LineDataSet dataSet = new LineDataSet(entries, "Cường độ âm thanh");
//                            LineData lineData = new LineData(dataSet);

                            LineDataSet dataSetFrequency = new LineDataSet(entriesFrequency,"Tần số âm thanh");
                            LineData lineDataFrequency = new LineData(dataSetFrequency);

//                            binding.chart.setData(lineData);
//                            binding.chart.invalidate();

                            binding.chartFrequency.setData(lineDataFrequency);
                            binding.chartFrequency.invalidate();
                        }
                    });
                    binding.btnRecord256kbps.setText("Dưng ghi âm ");
                    isRecording = true;
                } else {
                    isRecording = false;
                    binding.btnRecord256kbps.setText("Ghi âm");
                    audioRecorder.stopStreamRecord();
                }
            }
        });

        binding.btnShowChart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
             //   audioRecorder.readFileAudio();
            }
        });
        binding.btnCallApi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (binding.editDomain.getText().toString().equals("")){
                    Toast.makeText(getApplicationContext(),"Nhập domain",Toast.LENGTH_SHORT).show();
                }
                else {
                    audioRecorder.
                            callApi(new AudioRecorder.ListenerMessage() {
                        @Override
                        public void onListenerTime(String time) {
                            binding.textTime.setText(time);
                            binding.textTime.setVisibility(View.VISIBLE);
                            binding.editDomain.setVisibility(View.GONE);
                        }

                        @Override
                        public void onListenerMessage(String message) {
                            binding.textContent.setText(message);
                            binding.textContent.setVisibility(View.VISIBLE);
                            Log.e("activity", message);
                        }
                    }, binding.editDomain.getText().toString(),apiSelected);
                }
            }
        });
    }

    private void initEvent() {
        String[] options = {
                "Chọn trình phát",
                "Phát âm thanh được thu",
                "Phát âm thanh sau khi lọc cường độ âm thanh",
                "Phát âm thanh sau khi lọc tần số âm thanh"
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.item_spinner, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_item);
        binding.spnStartAudio.setAdapter(adapter);
        binding.spnStartAudio.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedIndex = position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        binding.btnStartAudio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedIndex == 0) {
                    Toast.makeText(getApplicationContext(), "Vui lòng chọn chế độ", Toast.LENGTH_SHORT).show();
                } else {
                    audioRecorder.startAudio(selectedIndex);
                }
            }
        });
    }

    private void initSpinnerApi(){
        String[] options = {
                "Chọn File API",
                "Call API gốc",
                "Call API File Cường độ âm thanh",
                "Call API File Tần số âm thanh"
        };
       ArrayAdapter<String> adapter = new ArrayAdapter<>(this,R.layout.item_api_audio,options);
       adapter.setDropDownViewResource(android.R.layout.simple_spinner_item);
       binding.spnCallApi.setAdapter(adapter);
       binding.spnCallApi.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
           @Override
           public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
               apiSelected = position;
           }

           @Override
           public void onNothingSelected(AdapterView<?> parent) {

           }
       });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

}
