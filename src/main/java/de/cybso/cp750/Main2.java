package de.cybso.cp750;

import java.lang.module.FindException;
import java.util.Scanner;

public class Main2 {
    public static void main(String[] args) throws Throwable {
        String ip = "192.168.1.136";
        int port = 61408;
        try(CP750Client client = new CP750Client(ip, port)) {
            // Update manually
            client.refresh();

            // Update every 5000ms
            client.setAutoPullInterval(5000);

            System.out.print("Current volume is: ");
            System.out.println(client.getFader());

            System.out.print("Current input mode is: ");
            System.out.println(client.getInputMode());

            client.addListener(CP750Field.SYS_FADER, (field, value) -> {
                System.out.println("Value of " + field + " has changed to " + value);
            });

            client.setFader(client.getFader() + 1);
        }
    }
}