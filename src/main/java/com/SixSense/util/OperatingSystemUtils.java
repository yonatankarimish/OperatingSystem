package com.SixSense.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;

public class OperatingSystemUtils {
    private static final Logger logger = LogManager.getLogger(OperatingSystemUtils.class);
    static String localPartition;
    static int localPartitionBlockSize;

    //obtain block size for local installation partition to optimize buffered reads
    static{
        LocalShell localShell = new LocalShell();
        try {
            LocalShellResult result = localShell.runCommand("df -h | grep sixsense | awk '{print $1}'");
            if(result.getExitCode() == 0){
                localPartition = result.getOutput().get(0);
            }else{
                throw new Exception("Failed to obtain local partition from local shell. Caused by: " + result.getErrors().get(0));
            }

            result = localShell.runCommand("blockdev --getbsz " + localPartition);
            if(result.getExitCode() == 0){
                localPartitionBlockSize = Integer.parseInt(result.getOutput().get(0));
            }else{
                throw new Exception("Failed to obtain local partition block size from local shell. Caused by: " + result.getErrors().get(0));
            }
        } catch (Exception e) {
            logger.error("Failed to obtain block size for local partition name", e);
        }
    }
}