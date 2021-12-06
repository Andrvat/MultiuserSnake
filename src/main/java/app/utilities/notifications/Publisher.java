package app.utilities.notifications;

import java.util.ArrayList;
import java.util.List;

public abstract class Publisher {
    List<Subscriber> subscribers = new ArrayList<>();

    public void informAllSubscribers() {
        for (var subscriber : subscribers) {
            subscriber.update();
        }
    }

    public void addSubscriber(Subscriber subscriber) {
        subscribers.add(subscriber);
    }
}
