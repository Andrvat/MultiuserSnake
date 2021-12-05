package app.utilities;

public abstract class Subscriber {
    protected Publisher publisher;

    public abstract void inform();

    public Subscriber(Publisher publisher) {
        this.publisher = publisher;
        this.publisher.addSubscriber(this);
    }
}
