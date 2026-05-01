package org.cloudbus.cloudsim.sdn.workload;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.UtilizationModel;
import org.cloudbus.cloudsim.UtilizationModelFull;

public class WorkloadReader {

    public static List<Workload> readWorkload(String filename, WorkloadResultWriter writer) {
        List<Workload> workloads = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            int workloadId = 0;
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",");

                // Extraire les valeurs de la ligne
                double startTime = Double.parseDouble(values[0]);
                String source = values[1];
                String dest = values[5];
                long psize = Long.parseLong(values[6]);

                // Créer un Cloudlet avec la taille spécifiée (psize)
                UtilizationModel utilizationModel = new UtilizationModelFull();
                Cloudlet cloudlet = new Cloudlet(workloadId, psize, 1, 0, 0, utilizationModel, utilizationModel, utilizationModel);

                // Créer une activité de traitement avec le Cloudlet
                Processing processing = new Processing(cloudlet);

                // Créer une requête avec les informations du fichier
                Request request = new Request(0); // userId = 0 par défaut
                request.addActivity(processing);

                // Créer un Workload
                Workload workload = new Workload(workloadId++, writer);
                workload.time = startTime;
                workload.request = request;
                workload.submitVmId = 0; // Par défaut
                workload.submitPktSize = (int) psize;

                // Ajouter le Workload à la liste
                workloads.add(workload);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return workloads;
    }
}