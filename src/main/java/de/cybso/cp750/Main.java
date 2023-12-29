package de.cybso.cp750;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Throwable {
        String ip = "192.168.1.136x";
        int port = 61408;
        try(CP750Client client = new CP750Client(ip, port)) {
            client.setRefreshInterval(5000);
            Scanner input = new Scanner(System.in);
            while (input.hasNextLine()) {
                String line = input.nextLine().trim();
                if (line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")) {
                    return;
                }

                if (line.equalsIgnoreCase("list") || line.equalsIgnoreCase("help") || line.equalsIgnoreCase("?")) {
                    System.out.println("Available commands: ");
                    for (CP750Field field : CP750Field.values()) {
                        System.out.print("    " + field.getKey() + " ");
                        if (field.isRange()) {
                            System.out.println("?|" + field.getRangeFrom() + ".." + field.getRangeTo());
                        } else {
                            System.out.println(String.join("|", field.getAllowedValues()));
                        }
                    }
                    System.out.println();
                    System.out.println("    get FIELD - show current cached key value");
                    System.out.println("    list|help|? - Show this page");
                    System.out.println("    exit|quit -  Quit program");
                    continue;
                }

                int pos = line.indexOf(' ');
                if (pos <= 0 || pos + 1 == line.length()) {
                    System.err.println("syntax error");
                    continue;
                }
                String key = line.substring(0, pos);
                String value = line.substring(pos + 1);

                if (key.equalsIgnoreCase("get")) {
                    CP750Field field = CP750Field.byKey(key);
                    if (field == null) {
                        System.err.println("Unknown field");
                    }
                    System.out.println(client.getCurrentValue(field));
                    continue;
                }

                CP750Field field = CP750Field.byKey(key);
                if (field == null) {
                    System.err.println("Unknown field");
                    continue;
                }

                value = client.send(field, value);
                if (!value.isEmpty()) {
                    System.out.println("< " + value);
                }
            }
        }
    }
}