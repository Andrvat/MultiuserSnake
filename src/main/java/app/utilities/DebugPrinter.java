package app.utilities;

import lombok.Builder;

import java.util.Date;

public class DebugPrinter {
    public static void printWithSpecifiedDateAndName(String ownerName, String message) {
        System.out.println(new Date() + " | " + ownerName + " | " + message);
    }
}
