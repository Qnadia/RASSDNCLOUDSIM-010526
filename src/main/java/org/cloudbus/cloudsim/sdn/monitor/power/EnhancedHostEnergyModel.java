package org.cloudbus.cloudsim.sdn.monitor.power;

import java.util.Locale;

/**
 * Modèle énergétique paramétrable pour un hôte CloudSimSDN, prenant en compte CPU, RAM et bande-passante.
 */
public class EnhancedHostEnergyModel implements PowerUtilizationEnergyModel {

    // Paramètres rendus configurables
    private final double idleWatt;
    private final double wattPerCpuUtil;      // W par %CPU
    private final double wattPerRamUtil;      // W par %RAM
    private final double wattPerBwUtil;       // W par %BW
    private final double powerOffDuration;    // durée pour mise hors-tension (s)

    public EnhancedHostEnergyModel(double idleWatt,
                                   double wattPerCpuUtil,
                                   double wattPerRamUtil,
                                   double wattPerBwUtil,
                                   double powerOffDuration) {
        this.idleWatt = idleWatt;
        this.wattPerCpuUtil = wattPerCpuUtil;
        this.wattPerRamUtil = wattPerRamUtil;
        this.wattPerBwUtil  = wattPerBwUtil;
        this.powerOffDuration = powerOffDuration;
    }

    private double computeInstantPower(double cpuUtil, double ramUtil, double bwUtil) {
        return idleWatt
             + wattPerCpuUtil * cpuUtil
             + wattPerRamUtil * ramUtil
             + wattPerBwUtil  * bwUtil;
    }

    @Override
    public double calculateEnergyConsumption(double duration, double utilizationMetrics) {
        // ici, utilizationMetrics sera un triple encodé, à parser par l'appelant si on veut CPU/RAM/BW
        // Pour simplifier : on lève une erreur si mal utilisé
        throw new UnsupportedOperationException("Use calculateEnergyConsumption(duration, cpuUtil, ramUtil, bwUtil)");
    }

    public double calculateEnergyConsumption(double duration,
                                             double cpuUtil,
                                             double ramUtil,
                                             double bwUtil) {
        // power instantané
        // double power = computeInstantPower(cpuUtil, ramUtil, bwUtil);

        // // si idle assez longtemps et aucune charge, on éteint
        // if (duration > powerOffDuration && cpuUtil == 0 && ramUtil == 0 && bwUtil == 0) {
        //     power = 0;
        // }

        // 1. Si complètement inactif pendant plus de powerOffDuration
   // Si complètement inactif
   if (cpuUtil == 0 && ramUtil == 0 && bwUtil == 0) {
    // Mode veille profonde après powerOffDuration
    return duration >= powerOffDuration ? 0 : (idleWatt * duration / 3600.0 * 0.1);
}

        // Calcule la puissance instantanée totale
        double power = idleWatt
                     + wattPerCpuUtil * cpuUtil/100.0
                     + wattPerRamUtil * ramUtil/100.0
                     + wattPerBwUtil  * bwUtil/100.0;
        
         // Éviter les valeurs négatives
        power = Math.max(0, power);

        // convertit Wh → kWh (optionnel) ou Wh si on divise par 3600
        return (power * duration) / 3600.0;
    }
}

