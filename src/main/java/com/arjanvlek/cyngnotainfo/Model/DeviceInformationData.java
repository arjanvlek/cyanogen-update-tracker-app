package com.arjanvlek.cyngnotainfo.Model;

import android.os.Build;

import java.io.RandomAccessFile;
import java.math.BigDecimal;

public class DeviceInformationData {
    private String deviceManufacturer;
    private String deviceName;
    private String soc;
    private String cpuFrequency;
    private String osVersion;
    private String serialNumber;

    public static String UNKNOWN = "-";

    public DeviceInformationData() {
        this.deviceManufacturer = Build.MANUFACTURER;
        this.deviceName = Build.DEVICE;
        this.soc = Build.BOARD;
        this.osVersion = Build.VERSION.RELEASE;
        this.serialNumber = Build.SERIAL;
        this.cpuFrequency = calculateCpuFrequency();
    }

    public String getDeviceManufacturer() {
        return deviceManufacturer;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public String getSoc() {
        return soc;
    }

    public String getCpuFrequency() {
        return cpuFrequency;
    }

    public String getOsVersion() {
        return osVersion;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public String calculateCpuFrequency() {
        try {
            RandomAccessFile cpuFrequencyFileReader = new RandomAccessFile("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq", "r");
            String cpuFrequencyString = cpuFrequencyFileReader.readLine();

            cpuFrequencyFileReader.close();

            int cpuFrequency = Integer.parseInt(cpuFrequencyString);
            cpuFrequency = cpuFrequency / 1000;

            BigDecimal cpuFrequencyGhz = new BigDecimal(cpuFrequency).divide(new BigDecimal(1000), 3, BigDecimal.ROUND_DOWN);
            return cpuFrequencyGhz.toString();
        } catch (Exception e) {
            return UNKNOWN;
        }
    }
}
