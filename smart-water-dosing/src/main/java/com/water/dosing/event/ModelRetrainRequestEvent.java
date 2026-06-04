package com.water.dosing.event;

import org.springframework.context.ApplicationEvent;

public class ModelRetrainRequestEvent extends ApplicationEvent {

    private final String trigger;

    public ModelRetrainRequestEvent(Object source, String trigger) {
        super(source);
        this.trigger = trigger;
    }

    public String getTrigger() { return trigger; }
}
