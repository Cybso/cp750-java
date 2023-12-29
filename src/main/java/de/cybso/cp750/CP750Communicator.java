package de.cybso.cp750;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

class CP750Communicator extends Thread {

    private final static Logger LOGGER = Logger.getLogger(CP750Communicator.class.getName());

    private final Socket socket;
    private final BufferedReader in;
    private final PrintStream out;

    private final Map<CP750Field, List<CP750Listener>> listeners = new HashMap<>();

    private final Map<CP750Field, List<CP750Listener>> onetimeListeners = new HashMap<>();

    private final List<Command> commands = new ArrayList<>();

    private final Map<CP750Field, String> currentValues = new HashMap<>();

    private long autoPullInterval = 0L;

    private long nextAutoPull = 0L;

    CP750Communicator(Socket s) throws IOException {
        this.socket = s;
        this.in = new BufferedReader(new InputStreamReader(s.getInputStream()));
        this.out = new PrintStream(s.getOutputStream());

        // Test connection
        for (CP750Field field : CP750Field.values()) {
            this.out.println(field.getKey() + " ?");
            String line = this.in.readLine();
            while (line.isEmpty()) {
                line = this.in.readLine();
            }

            if (line.startsWith(field.getKey())) {
                processInputLine(line);
            } else {
                throw new IOException("Unexpected response from server: " + line);
            }
        }
    }

    public long getAutoPullInterval() {
        return this.autoPullInterval;
    }

    public void setAutoPullInterval(long intervalMilli) {
        this.autoPullInterval = intervalMilli;
    }

    public String getCurrentValue(CP750Field key) {
        synchronized (this.currentValues) {
            return this.currentValues.get(key);
        }
    }

    public void addListener(CP750Field key, CP750Listener listener) {
        synchronized (this.listeners) {
            List<CP750Listener> list = this.listeners.computeIfAbsent(key, k -> new ArrayList<>());
            LOGGER.fine("Registering listener for event key '" + key + "'");
            list.add(listener);
        }
    }

    public void addOnetimeListener(CP750Field key, CP750Listener listener) {
        synchronized (this.onetimeListeners) {
            List<CP750Listener> list = this.onetimeListeners.computeIfAbsent(key, k -> new ArrayList<>());
            LOGGER.fine("Registering one-time listener for event key '" + key + "'");
            list.add(listener);
        }
    }

    public void removeListener(CP750Listener listener) {
        synchronized (this.onetimeListeners) {
            for (List<CP750Listener> list : this.onetimeListeners.values()) {
                list.remove(listener);
            }
        }
        synchronized (this.listeners) {
            for (List<CP750Listener> list : this.listeners.values()) {
                list.remove(listener);
            }
        }
    }

    public void send(CP750Field field, String value, CP750Listener listener) {
        synchronized (this.commands) {
            if (!field.isAllowedValue(value)) {
                LOGGER.warning("Ignoring unallowed value for " + field + ": " + value);
                return;
            }

            LOGGER.info("Adding command '" + field.getKey() + " " + value + "' to queue.");
            this.commands.add(new Command(field, value, listener));
        }

        synchronized (this) {
            notifyAll();
            try {
                wait();
            } catch (InterruptedException ignore) {
            }
        }
    }

    public void send(CP750Field key, String value) {
        send(key, value, null);
    }

    public void refresh() {
        synchronized (this) {
            synchronized (this.commands) {
                this.commands.add(new RefreshCommand());
            }
            notifyAll();
            try {
                wait();
            } catch (InterruptedException ignore) {
            }
        }
    }


    @Override
    public void run() {
        try {
            while (!isInterrupted() && socket.isConnected()) {
                synchronized (this) {
                    if (this.autoPullInterval > 0 && this.nextAutoPull < System.currentTimeMillis()) {
                        this.nextAutoPull = System.currentTimeMillis() + autoPullInterval;
                        out.println("status");
                    }

                    while (this.in.ready()) {
                        processInputLine(in.readLine());
                    }

                    synchronized (this.commands) {
                        for (Command cmd : commands) {
                            if (cmd instanceof RefreshCommand) {
                                out.println("status");
                            } else {
                                String str = cmd.field.getKey() + " " + cmd.value;
                                LOGGER.info("Sending '" + str + "'");
                                out.println(str);
                                if (cmd.listener != null) {
                                    addOnetimeListener(cmd.field, cmd.listener);
                                }
                            }
                            String line;
                            while (!(line = in.readLine()).trim().isEmpty()) {
                                processInputLine(line);
                            }
                        }
                        commands.clear();
                    }

                    try {
                        notifyAll();
                        wait(100);
                    } catch (InterruptedException ignore) {
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                in.close();
            } catch (IOException ignore) {
            }
            out.close();
        }
    }

    protected void processInputLine(String line) {
        if (line == null) {
            return;
        }
        line = line.trim();
        if (line.isEmpty()) {
            return;
        }
        LOGGER.fine("Processing input line '" + line + "'");

        String key, value;
        int colonPos = line.indexOf(" : ");
        if (colonPos > 0) {
            // Diese Variante sendet der Server beim Befehl "status"
            key = line.substring(0, colonPos);
            value = line.substring(colonPos + 3);
            if (value.isEmpty()) {
                return;
            }
        } else {
            // Und diese Variante sonst
            int spacePos = line.indexOf(' ');
            if (spacePos <= 0 || spacePos + 1 == line.length()) {
                return;
            }
            key = line.substring(0, spacePos);
            value = line.substring(spacePos + 1);
        }

        CP750Field field = CP750Field.byKey(key);
        if (field == null) {
            LOGGER.fine("Ignoring unknown key " + key);
            return;
        }

        synchronized (this.currentValues) {
            if (!value.equals(this.currentValues.get(field))) {
                LOGGER.info("Updating value of " + field + " to " + value);
                this.currentValues.put(field, value);
            }
        }

        // Process One-Time-Events first
        List<CP750Listener> list;
        synchronized (this.onetimeListeners) {
            list = this.onetimeListeners.remove(field);
        }
        if (list != null) {
            for (CP750Listener response : list) {
                try {
                    response.receive(field, value);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        synchronized (this.listeners) {
            list = this.listeners.get(field);
            if (list != null) {
                // Create a copy so we can safely iterate over this list w/o locking
                list = new ArrayList<>(list);
            }
        }

        if (list != null) {
            for (CP750Listener response : list) {
                try {
                    response.receive(field, value);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static class Command {
        final CP750Field field;
        final String value;
        final CP750Listener listener;

        Command(CP750Field field, String value, CP750Listener listener) {
            this.field = field;
            this.value = value;
            this.listener = listener;
        }
    }

    private static class RefreshCommand extends Command {
        RefreshCommand() {
            super(null, null, null);
        }
    }

}
