/**
 * SleepFragment.java
 * Wellness-App-MQP
 *
 * @version 1.0.0
 *
 * @author Jake Haas
 * @author Evan Safford
 * @author Nate Ford
 * @author Haley Andrews
 *
 * Copyright (c) 2014, 2015. Wellness-App-MQP. All Rights Reserved.
 *
 * THIS CODE AND INFORMATION ARE PROVIDED "AS IS" WITHOUT WARRANTY OF ANY 
 * KIND, EITHER EXPRESSED OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS FOR A
 * PARTICULAR PURPOSE.
 */

package edu.wpi.wellnessapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import java.util.Calendar;

public class SleepFragment extends Fragment {
    // UI Elements
    private Button calibrateButton;
    private Button stopStartButton;
    private Button moreSessionInfo;
    private TextView trackingStatus;
    private TextView lightSensorValue;
    private TextView audioValue;
    private TextView sleepTime;
    private TextView wakeTime;
    private TextView sleepEfficiency;
    private TextView duration;

    // Raw Sensor Data
    private int audioAmplitude;
    private float lightIntensity;

    // Calibrated Sensor Data (defaults set if left uncalibrated)
    private float calibratedLight = 14;
    private int calibratedAmplitude = 200;
    private int calibratedSleepHour = 8;
    private int calibratedWakeHour = 11;

    // Final Sleep Times
    private String fallAsleepTime = "";
    private String wakeUpTime = "";
    private int sleepHour;
    private int sleepMin;
    private int sleepSec;
    private String sleepAmPm;
    private int wakeHour;
    private int wakeMin;
    private int wakeSec;
    private String wakeAmPm;
    private int durationHours = 0;
    private int durationMins = 0;
    private int durationSec = 0;

    // Calibration
    private final int CALIBRATE_TIME = 10;
    private boolean isCalibrated = false;
    private CountDownTimer calibrateTimer;
    private float avgBy = 0;

    // Tracking Statuses
    private boolean isTracking = true;
    private boolean isAsleep = false;
    private int numWakeups = 0;
    private int efficiency;

    private final BroadcastReceiver recieveFromSleepService = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals("SensorData")) {
                Bundle extras = intent.getExtras();

                String maxAmplitudeIn = extras.getString("maxAmplitude");
                String lightIntensityIn = extras.getString("lightIntensity");

                audioValue.setText("Audio Value: " + maxAmplitudeIn);
                lightSensorValue.setText("Light Sensor Value: " + lightIntensityIn);

                audioAmplitude = Integer.parseInt(maxAmplitudeIn);
                lightIntensity = Float.parseFloat(lightIntensityIn);

                checkSleepStatus();
            }
        }
    };


    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_sleep, container, false);

        IntentFilter intentFilter = new IntentFilter("SensorData");
        getActivity().getApplicationContext().registerReceiver(recieveFromSleepService, intentFilter);

        isTracking = Utils.isServiceRunning(getActivity(), SleepService.class);

        // Initialize UI elements
        trackingStatus = (TextView) view.findViewById(R.id.textViewTrackingStatus);
        lightSensorValue = (TextView) view.findViewById(R.id.textViewLightSensorValue);
        audioValue = (TextView) view.findViewById(R.id.textViewAudioValue);
        sleepTime = (TextView) view.findViewById(R.id.textViewSleepTime);
        wakeTime = (TextView) view.findViewById(R.id.textViewWakeTime);
        duration = (TextView) view.findViewById(R.id.textViewDuration);
        sleepEfficiency = (TextView) view.findViewById(R.id.textViewEfficiency);

        // Start Button
        this.calibrateButton = (Button) view.findViewById(R.id.calibrateButton);
        this.calibrateButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isTracking) {
                    Utils.displayDialog(getActivity(), "Cannot Calibrate...", "You must have sleep tracking enabled to calibrate the sensors.",
                            null, "OK", Utils.emptyRunnable(), null);
                } else {
                    calibrateSensors();
                }
            }
        });

        // Stop Button
        this.stopStartButton = (Button) view.findViewById(R.id.stopStartSleepButton);

        if (isTracking) {
            trackingStatus.setText("Tracking...");
            stopStartButton.setText("Stop Sleep Tracking");
            calibrateButton.setEnabled(true);
        } else {
            trackingStatus.setText("Not Tracking...");
            stopStartButton.setText("Start Sleep Tracking");
            calibrateButton.setEnabled(false);
        }

        this.stopStartButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isTracking) {
                    Utils.displayDialog(getActivity(), "Stop Sleep Tracking?", "Are you SURE you want to stop sleep tracking?",
                            "Cancel", "OK", stopSleepRunnable(), Utils.emptyRunnable());
                } else {
                    startSleepTracking();
                    stopStartButton.setText("Stop Sleep Tracking");
                }
            }
        });

        this.moreSessionInfo = (Button) view.findViewById(R.id.moreSleepSessionInfo);
        this.moreSessionInfo.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Utils.displayDialog(getActivity(), "More Sleep Session Info: During your previous session...",
                        "Calibrated Audio Level: " + Integer.toString(calibratedAmplitude)
                                + "\nCalibrated Light Level: " + Float.toString(calibratedLight)
                                + "\nLast Fell Asleep: " + fallAsleepTime
                                + "\nLast Woke Up: " + wakeUpTime
                                + "\nTotal Time Asleep: " + Integer.toString(durationHours) + ":" + Integer.toString(durationMins) + ":" + Integer.toString(durationSec)
                                + "\nNumber of Wake Ups: " + Integer.toString(numWakeups),
                        null, "OK", Utils.emptyRunnable(), null);
            }
        });

        return view;
    }


    private Runnable stopSleepRunnable() {
        return new Runnable() {
            public void run() {
                stopSleepTracking();
                stopStartButton.setText("Start Sleep Tracking");
            }
        };
    }

    private void startSleepTracking() {
        // Start service
        getActivity().startService(new Intent(getActivity(), SleepService.class));

        isTracking = true;

        Log.d("SleepFragment", "Starting sleep service");

        Utils.displayDialog(getActivity(), "Sleep Tracking Started!", "This app uses the light sensor and microphone to track your sleeping " +
                        "patterns. Be sure to keep your device close to you while you sleep for best results.",
                null, "OK", Utils.emptyRunnable(), null);

        trackingStatus.setText("Tracking...");
        calibrateButton.setEnabled(true);
        sleepTime.setText("Fell Asleep: ");
        wakeTime.setText("Woke Up: ");

        isCalibrated = false;

        if (isTracking) {
            calibrateSensors();
        } else {
            checkSleepStatus();
        }
    }

    private void stopSleepTracking() {
        getActivity().stopService(new Intent(getActivity(), SleepService.class));
        Log.d("SleepFragment", "Stopping sleep service");

        isTracking = false;

        trackingStatus.setText("Not Tracking...");
        calibrateButton.setEnabled(false);
        lightSensorValue.setText("Light Sensor Value: ");
        audioValue.setText("Audio Value: ");

        sleepEfficiency.setText("Efficiency: " + Integer.toString(getEfficiency()));
    }

    //calibrate the light/sound levels by getting avgs (get values every 1sec for 10sec period)
    private void calibrateSensors() {

        trackingStatus.setText("Calibrating...");
        calibratedLight = 0;
        calibratedAmplitude = 0;
        avgBy = -1; //first light value is 0 and needs to be disregarded

        calibrateTimer = new CountDownTimer((CALIBRATE_TIME * 1000), 1000) {
            public void onTick(long millisUntilFinished) {
                //add up total values for averaging
                calibratedLight += lightIntensity;
                calibratedAmplitude += audioAmplitude;
                avgBy++;
            }

            public void onFinish() {
                //calculate avgs
                calibratedLight = Math.round(calibratedLight / avgBy);
                calibratedAmplitude = calibratedAmplitude / Math.round(avgBy);

                //after averages are calculated, add in some margin of noise/light
                calibratedLight += 15;
                calibratedAmplitude += 200;

                Log.d("SleepMonitor", "Calibrated sensors");
                isCalibrated = true;
                checkSleepStatus();

                if (isTracking) {
                    trackingStatus.setText("Tracking...");
                    stopStartButton.setText("Stop Sleep Tracking");
                    calibrateButton.setEnabled(true);
                } else {
                    trackingStatus.setText("Not Tracking...");
                    stopStartButton.setText("Start Sleep Tracking");
                    calibrateButton.setEnabled(false);
                }

                calibrateTimer.cancel();
            }
        }.start();
    }

    private void checkSleepStatus() {

        // Check all conditions to see if user fell asleep
        if (!isAsleep && SleepHourCheck() && SleepLightCheck() && SleepAudioCheck()) {
            Log.d("SleepMonitor", "Fell Asleep:" + fallAsleepTime);

            isAsleep = true;

            fallAsleepTime = getTime('S');
            sleepTime.setText("Fell Asleep: " + fallAsleepTime);
        }

        // Check to see if user woke up
        if (isAsleep && (!SleepHourCheck() || !SleepLightCheck() || !SleepAudioCheck())) {
            Log.d("SleepMonitor", "Woke Up:" + fallAsleepTime);
            numWakeups++;

            isAsleep = false;

            wakeUpTime = getTime('W');
            wakeTime.setText("Woke Up: " + wakeUpTime);

            duration.setText("Duration: " + getDuration());

            sleepEfficiency.setText("Efficiency: " + getEfficiency());
        }
    }

    //get the time in HH:MM:SS format, takes in a char if the sleep/wake variables need to be reset
    private String getTime(char set) {
        Log.d("getTime", "Getting current time");
        Calendar c = Calendar.getInstance();
        int hour = c.get(Calendar.HOUR);
        int minute = c.get(Calendar.MINUTE);
        int seconds = c.get(Calendar.SECOND);

        //12:00 AM is treated as hour 0
        if (hour == 0) {
            hour = 12;
        }

        Log.d("CurrentTime", Integer.toString(hour) + ":" + Integer.toString(minute) + ":" + Integer.toString(seconds) + getAmPm());

        //if the new sleep time is gotten, set it globally
        if (set == 'S') {
            sleepHour = hour;
            sleepMin = minute;
            sleepSec = seconds;
            sleepAmPm = getAmPm();
            Log.d("SetSleepTime", Integer.toString(sleepHour) + ":" + Integer.toString(sleepMin) + ":" + Integer.toString(sleepSec) + sleepAmPm);
        }

        //if the new wake time is gotten, set it globally
        if (set == 'W') {
            wakeHour = hour;
            wakeMin = minute;
            wakeSec = seconds;
            wakeAmPm = getAmPm();
            Log.d("SetWakeTime", Integer.toString(wakeHour) + ":" + Integer.toString(wakeMin) + ":" + Integer.toString(wakeSec) + wakeAmPm);
        }

        return Integer.toString(hour) + ":" + Integer.toString(minute) + ":" + Integer.toString(seconds) + getAmPm();
    }

    //Check to see if time of day AM or PM
    private String getAmPm() {
        Calendar c = Calendar.getInstance();
        int am_pm = c.get(Calendar.AM_PM);
        String amPm;

        if (am_pm == 0)
            amPm = "AM";
        else
            amPm = "PM";

        return amPm;
    }

    //Check to see if hour is between valid sleeping hours
    private boolean SleepHourCheck() {
        Calendar c = Calendar.getInstance();
        int hour = c.get(Calendar.HOUR);
        String amPm = getAmPm();

        if(hour == 0){
            hour = 12;
            amPm = "AM";
        }

        if(hour == 12 && amPm.equals("PM")){
            return false;
        }

        if(hour == 12 && amPm.equals("AM")){
            return true;
        }

        if ((hour >= calibratedSleepHour && amPm.equals("PM")) || (hour <= calibratedWakeHour && amPm.equals("AM"))) {
            return true;
        }
        return false;
    }

    //check to see if light is below valid level
    private boolean SleepLightCheck() {
        if (lightIntensity < calibratedLight) {
            return true;
        }
        return false;
    }

    //check to see if audio is below valid level
    private boolean SleepAudioCheck() {
        if (audioAmplitude < calibratedAmplitude) {
            return true;
        }
        return false;
    }

    //gets duration of sleep to wake time
    private String getDuration() {
        int hourDuration;
        int minDuration;
        int secDuration;

        //both AM or both PM: simply subtract
        if ((sleepAmPm.equals("PM") && wakeAmPm.equals("PM")) || (sleepAmPm.equals("AM") && wakeAmPm.equals("AM"))) {
            hourDuration = Math.abs(sleepHour - wakeHour);
            minDuration = Math.abs(sleepMin - wakeMin);
            secDuration = Math.abs(sleepSec - wakeSec);
        }
        //crossed over midnight: have to take day change into account
        else {
            hourDuration = (12 - sleepHour) + wakeHour;
            minDuration = (60 - sleepMin) + wakeMin;
            secDuration = (60 - sleepSec) + wakeSec;

            //add appropriate minute/second conversions
            if (secDuration >= 60) {
                secDuration -= 60;
                minDuration += 1;
            }

            if (minDuration >= 60) {
                minDuration -= 60;
                hourDuration += 1;
            }
        }

        //make sure a full hour/minute has changed
        if ((sleepSec >= wakeSec) && minDuration == 1) {
            minDuration--;
            secDuration = (60 - sleepSec) + wakeSec;
        }
        if ((sleepMin >= wakeMin) && hourDuration == 1) {
            hourDuration--;
            minDuration = (60 - sleepMin) + wakeMin;
        }

        getTotalDuration(hourDuration, minDuration, secDuration);
        return Integer.toString(durationHours) + ":" + Integer.toString(durationMins) + ":" + Integer.toString(durationSec);
    }

    //add new sleep duration to existing sleep duration
    private void getTotalDuration(int newHours, int newMins, int newSecs) {
        Log.d("AddToDuration", "Added " + Integer.toString(newHours) + ":" + Integer.toString(newMins) + ":" + Integer.toString(newSecs) + "to"
                + Integer.toString(durationHours) + ":" + Integer.toString(durationMins) + ":" + Integer.toString(durationSec));
        durationHours += newHours;
        durationMins += newMins;
        durationSec += newSecs;

        //add appropriate minute/second conversions
        if (durationSec >= 60) {
            durationSec -= 60;
            durationMins += 1;
        }

        if (durationMins >= 60) {
            durationMins -= 60;
            durationHours += 1;
        }
    }

    // Get the efficiency of the current sleep session
    private int getEfficiency() {
        //based on avg 11 wakeups per 8 hours, each wakeup resulting in a -0.625% efficiency (Source: FitBit)
        double sleepFactor = ((durationHours * 60 * 60) + (durationMins * 60) + durationSec) / 28800;
        double expectedWakeups = sleepFactor * 11;
        double extraWakeups = numWakeups - expectedWakeups;

        if (extraWakeups <= 0) {
            extraWakeups = 1;
        }

        efficiency = 100 - (int) Math.floor(extraWakeups * 0.625);

        if (efficiency < 0) {
            efficiency = 0;
        }

        Log.d("GetEfficiency", "Efficiency: " + Integer.toString(efficiency));

        return efficiency;
    }
}