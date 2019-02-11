package com.amazonaws.services.kinesisanalytics;

import java.util.List;
import java.util.ArrayList;

/**
 * Stats POJO class for output
 */
public class Stats
{
    // use this to avoid any serialization deserialization used within Flink
    public static final long serialVersionUID = 102L;

    public Stats() {
        buffers = new ArrayList();
    }

    public Stats(Double min, Double max, Double count, Double sum) {
        this.setMin(min);
        this.setMax(max);
        this.setSum(sum);
        this.setCount(count);
        if(count>0) {
            this.setAvg(sum / count);
        }
        else {
            // this should be NaN, but starting with 0
            this.setAvg(0.0);
        }
        this.buffers = new ArrayList();

    }

    public Stats(Double min, Double max, Double count, Double sum, List<byte[]> buffers) {
        this.setMin(min);
        this.setMax(max);
        this.setSum(sum);
        this.setCount(count);
        if(count>0) {
            this.setAvg(sum / count);
        }
        else {
            // this should be NaN, but starting with 0
            this.setAvg(0.0);
        }
        this.buffers = buffers;

    }

    public Stats(Double min, Double max, Double count, Double sum, List<byte[]> buffer1, List<byte[]> buffer2) {
        this.setMin(min);
        this.setMax(max);
        this.setSum(sum);
        this.setCount(count);
        if(count>0) {
            this.setAvg(sum / count);
        }
        else {
            // this should be NaN, but starting with 0
            this.setAvg(0.0);
        }
        this.buffers = new ArrayList(buffer1);
        this.buffers.addAll(buffer2);
    }

    private Double min;
    private Double max;
    private Double avg;
    private Double count;
    private Double sum;

    private List<byte[]> buffers;

    public void setMin(Double min) {
        this.min = min;
    }

    public Double getMin() {
        return min;
    }

    public Double getMax() {
        return max;
    }

    public void setMax(Double max) {
        this.max = max;
    }

    public Double getAvg() {
        return avg;
    }

    public void setAvg(Double avg) {
        this.avg = avg;
    }

    public Double getCount() {
        return count;
    }

    public void setCount(Double count) {
        this.count = count;
    }


    public String toString() {
        return "STATS: min: " + min + " max: " + max + " avg: " + avg + " count: " + count + " bufers appended len : " + buffers.size();
    }

    public Double getSum() {
        return sum;
    }

    public void setSum(Double sum) {
        this.sum = sum;
    }

    public List<byte[]> getBuffers() {
        return buffers;
    }

    public void setBufers(List<byte[]> buffers) {
        this.buffers = buffers;
    }
}