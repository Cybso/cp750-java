## About The Project

Small library to communicate with a *Dolby Digital Cinema Processor CP750*.

## Usage:

```java
public class Main {
    public static void main(String[] args) throws Throwable {
        String ip = "192.168.1.2";
        int port = 61408;
        try (CP750Client client = new CP750Client(ip, port)) {
            // Update manually
            client.refresh();

            // Update every 5000ms
            client.setRefreshInterval(5000);

            System.out.print("Current volume is: ");
            System.out.println(client.getFader());

            System.out.print("Current input mode is: ");
            System.out.println(client.getInputMode());

            client.addListener(CP750Field.SYS_FADER, (field, value) ->
                    System.out.println("Value of " + field + " has changed to " + value)
            );

            client.setFader(client.getFader() + 1);
        }
    }
}
```

## TODO

Until now, only commands for querying and changing mute, volume and input mode
have been implemented.
