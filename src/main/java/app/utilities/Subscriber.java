package app.utilities;

public abstract class Subscriber {
    protected Publisher publisher;

    public abstract void Notify(int x);

    public Subscriber(Publisher publisher){
        this.publisher = publisher;
        publisher.addSub(this);
    }
}
