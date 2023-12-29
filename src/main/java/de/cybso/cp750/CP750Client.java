package de.cybso.cp750;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CP750Client implements Closeable, AutoCloseable {

    private final static Logger LOGGER = Logger.getLogger(CP750Client.class.getName());

    private final Socket socket;
    private final BufferedReader in;
    private final PrintStream out;

    private final Map<CP750Field, List<CP750Listener>> listeners = new HashMap<>();

    private final Map<CP750Field, List<CP750Listener>> onetimeListeners = new HashMap<>();

    private final Map<CP750Field, String> currentValues = new HashMap<>();

    private RefreshTimer refreshTimer;

    public CP750Client(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintStream(socket.getOutputStream());

        // Test connection
        for (CP750Field field : CP750Field.values()) {
            this.out.println(field.getKey() + " ?");
            String line = this.in.readLine();
            if (!line.startsWith(field.getKey())) {
                throw new IOException("Unexpected response from server: " + line);
            }

            do {
                processInputLine(line);
            } while (!(line = in.readLine()).isEmpty());
        }
    }

    public CP750Client(String server, int port) throws IOException {
        this(new Socket(server, port));
    }

    public long getRefreshInterval() {
        return refreshTimer == null ? 0 : refreshTimer.getRefreshInterval();
    }

    public synchronized void setRefreshInterval(long intervalMilli) {
        if (intervalMilli <= 0) {
            if (refreshTimer != null) {
                refreshTimer.setRefreshInterval(intervalMilli);
            }
        } else {
            if (refreshTimer != null) {
                refreshTimer.setRefreshInterval(intervalMilli);
            } else {
                refreshTimer = new RefreshTimer(intervalMilli);
                refreshTimer.setDaemon(true);
                refreshTimer.start();
            }
        }
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

    @Override
    public void close() {
        try {
            this.in.close();
            this.out.close();
            this.socket.close();
        } catch (IOException ignore) {
        }
    }


    public String getVersion() {
        return getCurrentValue(CP750Field.SYSINFO_VERSION);
    }

    public int getFader() {
        String result = query(CP750Field.SYS_FADER);
        if (result.isEmpty()) {
            return 0;
        } else {
            return Integer.parseInt(result);
        }
    }

    public void setFader(int value) {
        send(CP750Field.SYS_FADER, String.valueOf(value));
    }

    public boolean isMuted() {
        return query(CP750Field.SYS_MUTE).equals("1");
    }

    public void setMuted(boolean mute) {
        send(CP750Field.SYS_MUTE, mute ? "1" : "0");
    }

    public CP750InputMode getInputMode() {
        return CP750InputMode.byValue(query(CP750Field.SYS_INPUT_MODE));
    }

    public void setInputMode(CP750InputMode mode) {
        send(CP750Field.SYS_INPUT_MODE, mode.value);
    }

    public void refresh() {
        send("status");
    }

    protected String send(CP750Field field, String value) {
        if (!field.isAllowedValue(value)) {
            LOGGER.warning("Ignoring unallowed value for field " + field + ": " + value);
        }

        return send(field.getKey() + " " + value);
    }

    protected String query(CP750Field field) {
        return send(field, "?");
    }

    protected String send(String raw) {
        synchronized (this.socket) {
            out.println(raw);
            try {
                String lastValue = "";
                String line;
                while ((line = in.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) {
                        break;
                    }

                    lastValue = processInputLine(line);
                }
                return lastValue;
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to write to socket", e);
                return "";
            }
        }
    }

    protected String processInputLine(String line) {
        if (line == null) {
            return "";
        }
        line = line.trim();
        if (line.isEmpty()) {
            return "";
        }
        LOGGER.fine("Processing input line '" + line + "'");

        String key, value;
        int colonPos = line.indexOf(" : ");
        if (colonPos > 0) {
            // Diese Variante sendet der Server beim Befehl "status"
            key = line.substring(0, colonPos);
            value = line.substring(colonPos + 3);
            if (value.isEmpty()) {
                return "";
            }
        } else {
            // Und diese Variante sonst
            int spacePos = line.indexOf(' ');
            if (spacePos <= 0 || spacePos + 1 == line.length()) {
                return "";
            }
            key = line.substring(0, spacePos);
            value = line.substring(spacePos + 1);
        }

        CP750Field field = CP750Field.byKey(key);
        if (field == null) {
            LOGGER.fine("Ignoring unknown key " + key);
            return "";
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

        return value;
    }

    private class RefreshTimer extends Thread {

        private long nextAutoRefresh;
        private long refreshInterval;

        RefreshTimer(long refreshInterval) {
            this.refreshInterval = refreshInterval;
            this.nextAutoRefresh = System.currentTimeMillis() + refreshInterval;
        }

        long getRefreshInterval() {
            return this.refreshInterval;
        }

        void setRefreshInterval(long value) {
            synchronized (this) {
                this.refreshInterval = value;
                this.nextAutoRefresh = System.currentTimeMillis() + refreshInterval;
                notify();
            }
        }

        @Override
        public void run() {
            while (!isInterrupted()) {
                try {
                    synchronized (this) {
                        if (this.refreshInterval > 0) {
                            long timeToWait = System.currentTimeMillis() - this.nextAutoRefresh;
                            if (timeToWait < 0) {
                                CP750Client.this.refresh();
                                this.nextAutoRefresh = System.currentTimeMillis() + this.refreshInterval;
                                wait(this.refreshInterval);
                            } else {
                                wait(timeToWait);
                            }
                        } else {
                            // Wait until value has been changed
                            wait();
                        }
                    }
                } catch (InterruptedException ignore) {
                }
            }
        }

    }
}
