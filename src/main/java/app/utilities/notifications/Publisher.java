package app.utilities.notifications;

import java.util.ArrayList;
import java.util.List;

public abstract class Publisher {
    List<Subscriber> subscribers = new ArrayList<>();

    public void informAllSubscribers() {
        for (Subscriber subscriber : subscribers) {
            subscriber.inform();
        }
    }

    public void addSubscriber(Subscriber subscriber) {
        subscribers.add(subscriber);
    }
}
