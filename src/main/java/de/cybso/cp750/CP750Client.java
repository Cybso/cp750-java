package de.cybso.cp750;

import java.io.*;
import java.net.Socket;

public class CP750Client implements Closeable, AutoCloseable {

    private final Socket socket;

    private final CP750Communicator communicator;

    public CP750Client(Socket socket) throws IOException {
        this.socket = socket;
        this.communicator = new CP750Communicator(socket);
        this.communicator.start();
    }

    public CP750Client(String server, int port) throws IOException {
        this(new Socket(server, port));
    }


    @Override
    public void close() throws IOException {
        this.socket.close();
        this.communicator.interrupt();
    }

    public CP750Communicator getCommunicator() {
        return communicator;
    }

    public long getAutoPullInterval() {
        return communicator.getAutoPullInterval();
    }

    public void setAutoPullInterval(long intervalMilli) {
        communicator.setAutoPullInterval(intervalMilli);
    }

    public void addListener(CP750Field key, CP750Listener listener) {
        communicator.addListener(key, listener);
    }

    public String getVersion() {
        return communicator.getCurrentValue(CP750Field.SYSINFO_VERSION);
    }

    public int getFader() {
        String value = communicator.getCurrentValue(CP750Field.SYS_FADER);
        if (value == null) {
            value = "0";
        }
        return Integer.valueOf(value);
    }

    public void setFader(int value) {
        communicator.send(CP750Field.SYS_FADER, "" + value);
    }

    public boolean isMuted() {
        return communicator.getCurrentValue(CP750Field.SYS_MUTE).equals("1");
    }

    public void setMuted(boolean mute) {
        communicator.send(CP750Field.SYS_MUTE, mute ? "0" : "1");
    }

    public CP750InputMode getInputMode() {
        return CP750InputMode.byValue(communicator.getCurrentValue(CP750Field.SYS_INPUT_MODE));
    }

    public void setInputMode(CP750InputMode mode) {
        communicator.send(CP750Field.SYS_INPUT_MODE, mode.value);
    }

    public void refresh() {
        communicator.refresh();
    }
}
