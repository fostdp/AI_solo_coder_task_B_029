package com.water.dosing.event;

import org.springframework.context.ApplicationEvent;

public class AlarmEvent extends ApplicationEvent {

    private final int level;
    private final String type;
    private final String message;

    public AlarmEvent(Object source, int level, String type, String message) {
        super(source);
        this.level = level;
        this.type = type;
        this.message = message;
    }

    public int getLevel() { return level; }
    public String getType() { return type; }
    public String getMessage() { return message; }
}
