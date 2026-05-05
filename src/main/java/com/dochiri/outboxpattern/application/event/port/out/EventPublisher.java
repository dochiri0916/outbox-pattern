package com.dochiri.outboxpattern.application.event.port.out;

import com.dochiri.outboxpattern.application.event.ApplicationEvent;

public interface EventPublisher {

    void publish(ApplicationEvent event);

}
