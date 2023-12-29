package de.cybso.cp750;

import java.util.Scanner;

public class Main {

    public static final int DEFAULT_PORT = 61408;

    public static void main(String[] args) throws Throwable {
        if (args.length != 1) {
            usage();
            return;
        }
        int port = DEFAULT_PORT;
        String host = args[0].trim();
        int colonPos = host.indexOf(':');
        if (colonPos > 0) {
            String portStr = host.substring(colonPos + 1);
            host = host.substring(0, colonPos);
            if (portStr.isEmpty()) {
                usage();
                return;
            }
            try {
                port = Integer.parseInt(portStr);
                if (port <= 0 || port >= 65535) {
                    System.err.println("Port out of range");
                    usage();
                    return;
                }
            } catch (NumberFormatException e) {
                System.err.println(e.getMessage());
                usage();
                return;
            }
        }

        try(CP750Client client = new CP750Client(host, port)) {
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

    private static void usage() {
        System.err.println("Usage: java " + Main.class.getName() + " SERVER[:PORT]");
        System.exit(1);
    }
}