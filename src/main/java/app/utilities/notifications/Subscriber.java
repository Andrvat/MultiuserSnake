package app.utilities.notifications;

public abstract class Subscriber {
    protected Publisher publisher;

    public abstract void updateState();

    public Subscriber(Publisher publisher) {
        this.publisher = publisher;
        this.publisher.addSubscriber(this);
    }
}
