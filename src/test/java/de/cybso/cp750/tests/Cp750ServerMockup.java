package de.cybso.cp750.tests;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Cp750ServerMockup extends Thread implements AutoCloseable {

    private final ServerSocket serverSocket;

    private final Cp750StateEngine stateEngine;

    private final List<WorkerThread> workers = new ArrayList<>();

    public Cp750ServerMockup() throws IOException {
        this(0);
    }

    public Cp750ServerMockup(int port) throws IOException {
        this(new ServerSocket(port, 1, InetAddress.getByName("127.0.0.1")), new Cp750StateEngine());
    }

    public Cp750ServerMockup(ServerSocket serverSocket, Cp750StateEngine stateEngine) {
        this.serverSocket = serverSocket;
        this.stateEngine = stateEngine;
    }

    public int getPort() {
        return this.serverSocket.getLocalPort();
    }

    public Cp750StateEngine getStateEngine() {
        return this.stateEngine;
    }

    @Override
    public void run() {
        while (!isInterrupted()) {
            try {
                Socket client = this.serverSocket.accept();
                WorkerThread worker = new WorkerThread(client, this.stateEngine);
                worker.setName("Client-" + client.getInetAddress().toString());
                synchronized (workers) {
                    workers.add(worker);
                }
                worker.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    @Override
    public void close() throws Exception {
        interrupt();
        try {
            join(1000);
        } catch (Exception ignore) {
        }
        for (WorkerThread worker : new ArrayList<>(this.workers)) {
            worker.close();
        }
        this.serverSocket.close();
    }

    private class WorkerThread extends Thread implements AutoCloseable {

        private final Socket client;
        private final Cp750StateEngine stateEngine;
        private final BufferedReader in;
        private final PrintStream out;

        public WorkerThread(Socket client, Cp750StateEngine stateEngine) throws IOException {
            this.client = client;
            this.stateEngine = stateEngine;
            this.in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));
            this.out = new PrintStream(client.getOutputStream());
        }

        @Override
        public void run() {
            try {
                while (!isInterrupted()) {
                    String line;
                    while (null != (line = in.readLine())) {
                        processLine(line);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                close();
            }
        }

        private void processLine(String line) {
            line = line.trim();
            String key, value = "";
            int spacePos = line.indexOf(' ');
            if (spacePos <= 0) {
                key = line;
            } else {
                key = line.substring(0, spacePos);
                value = line.substring(spacePos + 1);
            }

            switch (key) {
                case "cp750.sysinfo.version":
                    if (value.equals("?")) {
                        writeln(out, key + " " + stateEngine.version);
                    }
                    break;
                case "status":
                    writeln(out, "cp750.sysinfo.version : " + stateEngine.version);
                    writeln(out, "cp750.sys.input_mode : " + stateEngine.inputMode);
                    writeln(out, "cp750.sys.fader : " + stateEngine.fader);
                    writeln(out, "cp750.sys.mute : " + (stateEngine.mute ? "1" : "0"));
                    break;
                case "cp750.sys.input_mode":
                    if (value.equals("?")) {
                        writeln(out, key + " " + stateEngine.inputMode);
                    } else if (value.equals("last")) {
                        value = stateEngine.inputMode;
                        stateEngine.inputMode = stateEngine.lastInputMode;
                        stateEngine.lastInputMode = value;
                        writeln(out, key + " " + stateEngine.inputMode);
                    } else if (List.of("analog", "dig_1", "dig_2", "dig_3", "dig_4", "mic", "non_sync").contains(value)) {
                        stateEngine.lastInputMode = stateEngine.inputMode;
                        stateEngine.inputMode = value;
                        writeln(out, key + " " + stateEngine.inputMode);
                    }
                    break;
                case "cp750.sys.fader":
                    if (value.equals("?")) {
                        writeln(out, key + " " + stateEngine.fader);
                    } else {
                        try {
                            int intVal = Integer.parseInt(value);
                            if (intVal >= 0 && intVal <= 100) {
                                stateEngine.fader = intVal;
                                writeln(out, key + " " + stateEngine.fader);
                            }
                        } catch (NumberFormatException ignore) {
                        }
                    }
                    break;
                case "cp750.ctrl.fader_delta":
                    try {
                        int intVal = Integer.parseInt(value);
                        if (intVal >= -100 && intVal <= 100) {
                            stateEngine.fader += intVal;
                            if (stateEngine.fader < 0) stateEngine.fader = 0;
                            if (stateEngine.fader > 100) stateEngine.fader = 100;
                            writeln(out, key + " " + stateEngine.fader);
                        }
                    } catch (NumberFormatException ignore) {
                    }
                    break;
                case "cp750.sys.mute":
                    switch (value) {
                        case "?":
                            writeln(out, key + " " + (stateEngine.mute ? "1" : "0"));
                            break;
                        case "0":
                            stateEngine.mute = false;
                            writeln(out, key + " 0");
                            break;
                        case "1":
                            stateEngine.mute = true;
                            writeln(out, key + " 1");
                            break;
                    }
                    break;
            }

            writeln(out, "");
        }

        private void writeln(PrintStream out, String s) {
            //System.out.println("SENT " + s);
            out.println(s);
        }

        @Override
        public void close() {
            synchronized (Cp750ServerMockup.this.workers) {
                Cp750ServerMockup.this.workers.remove(this);
            }
            interrupt();
            try {
                this.in.close();
            } catch (Exception ignore) {
            }
            this.out.close();
            try {
                this.client.close();
            } catch (Exception ignore) {
            }
        }

    }

    public static void main(String[] args) throws Throwable {
        Cp750ServerMockup mockup = new Cp750ServerMockup(1234);
        System.out.println("Listening on port " + mockup.getPort());
        mockup.start();
    }
}
