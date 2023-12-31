## About The Project

Simple Java library to communicate with a *Dolby Digital Cinema Processor CP750*.

It uses ASCII commands send over a TCP connection on port 61408. Please note that
the CP750 only accepts up to 20 simultaneous connection and will discard
the oldest connection if a 21st connection is established. So be sure to grateful
shutdown each connection using the client's `close()` method, or it's
`AutoCloseable` functionality.

This project is NOT affiliated with, funded, or in any way associafunctionalityted
with *Dolby Laboratories, Inc*.

## Usage

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

## Implemented commands

* Version check
* Input Mode control
* Fader control (absolute and delta changes)
* Mute control
