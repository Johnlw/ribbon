package com.netflix.loadbalancer.weightedload;

public class CpuAndRam {
    private Integer Cpu = Integer.MAX_VALUE;
    private Integer Ram =Integer.MAX_VALUE;

    public Integer getCpu() {
        return Cpu;
    }

    public void setCpu(Integer cpu) {
        Cpu = cpu;
    }

    public Integer getRam() {
        return Ram;
    }

    public void setRam(Integer ram) {
        Ram = ram;
    }

    @Override
    public String toString() {
        return "CpuAndRam{" +
                "Cpu=" + Cpu +
                ", Ram=" + Ram +
                '}';
    }
}
