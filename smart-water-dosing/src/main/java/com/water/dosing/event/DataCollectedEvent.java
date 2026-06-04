package com.water.dosing.event;

import com.water.dosing.entity.WaterQuality;
import org.springframework.context.ApplicationEvent;

import java.util.List;

public class DataCollectedEvent extends ApplicationEvent {

    private final List<WaterQuality> waterQualityList;
    private final java.util.Map<String, float[]> rawRegisterData;

    public DataCollectedEvent(Object source, List<WaterQuality> waterQualityList,
                              java.util.Map<String, float[]> rawRegisterData) {
        super(source);
        this.waterQualityList = waterQualityList;
        this.rawRegisterData = rawRegisterData;
    }

    public List<WaterQuality> getWaterQualityList() {
        return waterQualityList;
    }

    public java.util.Map<String, float[]> getRawRegisterData() {
        return rawRegisterData;
    }
}
