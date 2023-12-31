package de.cybso.cp750;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * This is the class that handles the communication with CP750.
 * Please be sure to define a reasonable timeout value (default: 10000ms)
 * and to gratefully close the connection after usage.
 *
 * If auto-refresh is activated this class initiates a background thread
 * that will trigger a "status" command.
 */
public class CP750Client implements Closeable, AutoCloseable {

    /**
     * Default socket / connect timeout in Milliseconds
     */
    public static final int DEFAULT_TIMEOUT = 10000;

    /**
     * Default port if the serial interface
     */
    public static final int DEFAULT_PORT = 61408;

    private final static Logger LOGGER = Logger.getLogger(CP750Client.class.getName());

    private final Socket socket;

    private final BufferedReader in;

    private final PrintStream out;

    private final Map<CP750Field, List<CP750Listener>> listeners = new HashMap<>();

    private final Map<CP750Field, List<CP750Listener>> onetimeListeners = new HashMap<>();

    private final Map<CP750Field, String> currentValues = new HashMap<>();

    private RefreshTimer refreshTimer;

    /**
     * Establish a connection through the given socket. The constructor
     * will query each known field from {@link CP750Client} (except CTRL fields)
     * and throw an IOException on unexpected responses.
     *
     * If this happens, you have to close the socket connection manually.
     *
     * @throws IOException If the connection fails or an unexpected response is retrieved.
     */
    public CP750Client(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintStream(socket.getOutputStream());
        testConnection();
    }

    /**
     * Creates a new Socket connection to the given server and port.
     * If the connection fails, the connection will be closed automatically.
     */
    public CP750Client(String server, int port, int timeout) throws IOException {
        this.socket = createSocket(server, port, timeout);
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintStream(socket.getOutputStream());
        try {
            testConnection();
        } catch (IOException e) {
            // We have created the socket, so make sure that it is closed.
            try {
                if (!this.socket.isClosed()) {
                    this.socket.close();
                }
            } catch (IOException ignore) {
            }
            throw e;
        }
    }


    /**
     * Creates a new Socket connection to the given server and port.
     * If the connection fails, the connection will be closed automatically.
     */
    public CP750Client(String server) throws IOException {
        this(server, DEFAULT_PORT, DEFAULT_TIMEOUT);
    }

    /**
     * Creates a new Socket connection to the given server and port.
     * If the connection fails, the connection will be closed automatically.
     */
    public CP750Client(String server, int port) throws IOException {
        this(server, port, DEFAULT_TIMEOUT);
    }

    /**
     * Creates a new Socket and makes sure that it has a timeout value
     */
    private static Socket createSocket(String server, int port, int timeout) throws IOException {
        if (timeout <= 0) {
            timeout = DEFAULT_TIMEOUT;
        }
        Socket socket = new Socket();
        socket.setSoTimeout(timeout);
        socket.connect(new InetSocketAddress(server, port), timeout);
        return socket;
    }

    /**
     * Returns the socket instance
     */
    public Socket getSocket() {
        return this.socket;
    }

    /**
     * Returns the refresh interval. A value of 0 (default) means that no
     * refresh calls are being made.
     */
    public long getRefreshInterval() {
        return refreshTimer == null ? 0 : refreshTimer.getRefreshInterval();
    }

    /**
     * Starts a worker thread that will periodically refresh the current status
     * of each field.
     *
     * @param intervalMilli Refresh period in milliseconds
     */
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

    /**
     * Returns the last known value of a given field, without
     * querying the server. The cached value is always returned
     * as String value as read from the server
     */
    public String getCurrentValue(CP750Field key) {
        synchronized (this.currentValues) {
            return this.currentValues.get(key);
        }
    }

    /**
     * Adds a listener that reacts to value changes for a given field
     */
    public void addListener(CP750Field key, CP750Listener listener) {
        synchronized (this.listeners) {
            List<CP750Listener> list = this.listeners.computeIfAbsent(key, k -> new ArrayList<>());
            LOGGER.fine("Registering listener for event key '" + key + "'");
            list.add(listener);
        }
    }

    /**
     * Adds a listeners that will be called the next time a field has been changed,
     * and removed from the queue afterward.
     */
    public void addOnetimeListener(CP750Field key, CP750Listener listener) {
        synchronized (this.onetimeListeners) {
            List<CP750Listener> list = this.onetimeListeners.computeIfAbsent(key, k -> new ArrayList<>());
            LOGGER.fine("Registering one-time listener for event key '" + key + "'");
            list.add(listener);
        }
    }

    /**
     * Removes a listener
     */
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

    /**
     * Sends an "exit" command to the server and closes the connection
     */
    @Override
    public void close() {
        try { send("exit"); } catch (IOException ignore) {}
        try { this.in.close(); } catch (IOException ignore) {}
        try { this.out.close(); } catch (Exception ignore) {}
        try { this.socket.close(); } catch (IOException ignore) {}
    }

    /**
     * Returns the CP750's version string. Since that value is not expected
     * to change it will always be read from the cache.
     */
    public String getVersion() {
        return getCurrentValue(CP750Field.SYSINFO_VERSION);
    }

    /**
     * Returns the faders value, or -1 if unknown
     */
    public int getFader() throws IOException {
        String result = query(CP750Field.SYS_FADER);
        if (result.isEmpty()) {
            return -1;
        } else {
            return Integer.parseInt(result);
        }
    }

    /**
     * Sets the faders value. Allowed values are in range from 0 to 100.
     */
    public void setFader(int value) throws IOException {
        send(CP750Field.SYS_FADER, String.valueOf(value));
    }

    /**
     * Increments or decrements the fader by the given value. The value's
     * range is from -100 to 100, but the fader will never go down below
     * 0 or above 100.
     *
     * This method also queries the fader afterward to ensure that the
     * cached value is updated and the listeners are notified about the change.
     */
    public void setFaderDelta(int value) throws IOException {
        send(CP750Field.CTRL_FADER_DELTA, String.valueOf(value));
        query(CP750Field.SYS_FADER);
    }

    /**
     * Returns true if the CP750 is muted
     */
    public boolean isMuted() throws IOException {
        return query(CP750Field.SYS_MUTE).equals("1");
    }

    /**
     * Changes the muted state
     */
    public void setMuted(boolean mute) throws IOException {
        send(CP750Field.SYS_MUTE, mute ? "1" : "0");
    }

    /**
     * Returns the input mode
     */
    public CP750InputMode getInputMode() throws IOException {
        return CP750InputMode.byValue(query(CP750Field.SYS_INPUT_MODE));
    }

    /**
     * Changes the input mode. Since each input mode is bound to an internal
     * default fader value, the fader state will be queried afterward.
     */
    public void setInputMode(CP750InputMode mode) throws IOException {
        send(CP750Field.SYS_INPUT_MODE, mode.value);
        query(CP750Field.SYS_FADER);
    }

    /**
     * Refreshes all internal field values using the "status" command
     */
    public void refresh() throws IOException {
        send("status");
    }

    /**
     * Sends the given value (if allowed) to the server and returns the
     * last response line.
     */
    protected String send(CP750Field field, String value) throws IOException {
        if (!field.isAllowedValue(value)) {
            LOGGER.warning("Ignoring un-allowed value for field " + field + ": " + value);
            return "";
        }

        return send(field.getKey() + " " + value);
    }

    /**
     * Queries the status of a given field from the server
     */
    protected String query(CP750Field field) throws IOException {
        return send(field, "?");
    }

    /**
     * Send a raw string to the server and returns the last response line
     */
    protected String send(String raw) throws IOException {
        synchronized (this.socket) {
            out.println(raw);
            String lastValue = "";
            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    break;
                }

                lastValue = processInputLine(line);
            }
            if (line == null) {
                throw new SocketException("Connection closed unexpectedly");
            }
            return lastValue;
        }
    }

    /**
     * Processes an input line, updates the cache and calls the listeners.
     */
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
                // Create a copy, so we can safely iterate over this list w/o locking
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

    private void testConnection() throws IOException {
        // Test connection
        for (CP750Field field : CP750Field.values()) {
            if (field.getKey().startsWith("cp750.ctrl.")) {
                // ctrl-Fields do not have a status value
                continue;
            }

            this.out.println(field.getKey() + " ?");
            String line = this.in.readLine();
            if (line == null) {
                throw new SocketException("Connection closed unexpectedly");
            }
            if (!line.startsWith(field.getKey())) {
                throw new IOException("Unexpected response from server: " + line);
            }

            while (!line.isEmpty()) {
                processInputLine(line);
                line = in.readLine();
                if (line == null) {
                    throw new SocketException("Connection closed unexpectedly");
                }
            }
        }
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
                                try {
                                    CP750Client.this.refresh();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                    continue;
                                }
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
