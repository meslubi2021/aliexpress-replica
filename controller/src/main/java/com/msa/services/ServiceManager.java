package com.msa.services;

import com.msa.models.Machine;
import com.msa.models.RunningInstance;
import com.msa.models.ServiceType;
import com.msa.repos.MachinesRepo;
import com.msa.repos.RunningInstanceRepo;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@AllArgsConstructor
public class ServiceManager {
    private final MetricsPuller metricsPuller;
    private final HealthChecker healthChecker;
    private final RunningInstanceRepo runningInstancesRepo;

    private final RunningInstanceRepo runningInstanceRepo;
    private final MachinesRepo machinesRepo;

    @Scheduled(fixedRateString = "${health-checker.period}")
    public void collectServicesMetrics() throws UnsupportedEncodingException {
        // pull the running instances to be checked
        List<RunningInstance> allRunningInstances = runningInstancesRepo.findAll();
        ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        System.out.println("Checking " + allRunningInstances.size() + " instances");
        for (RunningInstance runningInstance: allRunningInstances){
            // create new thread to handle each instance
            executorService.execute(() -> {
                Hashtable<String, String> metricResponses = null;
                try {
                    metricResponses = metricsPuller.pullInstanceMetrics(runningInstance);
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }

                healthChecker.checkServiceHealth(metricResponses, runningInstance);
            });

        }
    }



}
