package pik.domain;

import com.google.common.eventbus.EventBus;
import jCommons.event.IEventBus;

public class GoogleEventBus implements IEventBus {
    private final EventBus eventBus;

    public GoogleEventBus() {
        eventBus = new EventBus();
    }

    public GoogleEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public void register(Object object) {
        eventBus.register(object);
    }

    @Override
    public void unregister(Object object) {
        eventBus.unregister(object);
    }

    @Override
    public void post(Object event) {
        eventBus.post(event);
    }
}
