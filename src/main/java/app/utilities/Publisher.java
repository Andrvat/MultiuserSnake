package app.utilities;

import java.util.ArrayList;
import java.util.List;

public abstract class Publisher {
    List<Subscriber> subs = new ArrayList<>();
    public void NotifyAll(int x){
        for (Subscriber sub : subs){
            sub.Notify(x);
        }
    }

    public void addSub(Subscriber sub){
        subs.add(sub);
    }
}
