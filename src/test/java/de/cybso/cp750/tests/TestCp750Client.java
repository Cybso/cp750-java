package de.cybso.cp750.tests;

import de.cybso.cp750.CP750Client;
import de.cybso.cp750.CP750Field;
import de.cybso.cp750.CP750InputMode;
import de.cybso.cp750.CP750Listener;
import junit.framework.TestCase;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;

public class TestCp750Client extends TestCase {

    private Cp750ServerMockup server;
    private CP750Client client;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.server = new Cp750ServerMockup();
        this.server.start();
        this.client = new CP750Client("127.0.0.1", this.server.getPort());
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        this.client.close();
        this.server.close();
    }

    public void testVersion() {
        assertEquals(Cp750StateEngine.VERSION, this.client.getVersion());
    }

    public void testMute() throws Throwable {
        assertFalse(this.client.isMuted());
        this.client.setMuted(true);
        assertTrue(this.client.isMuted());
        this.client.refresh();
        assertTrue(this.client.isMuted());
        this.client.setMuted(false);
        assertFalse(this.client.isMuted());
    }

    public void testFader() throws Throwable {
        assertEquals(35, this.client.getFader());
        this.client.setFader(70);
        assertEquals(70, this.client.getFader());
        this.client.refresh();
        assertEquals(70, this.client.getFader());
        this.client.setFader(0);
        assertEquals(0, this.client.getFader());
        this.client.setFader(-1);
        assertEquals(0, this.client.getFader());
        this.client.setFader(100);
        assertEquals(100, this.client.getFader());
        this.client.setFader(101);
        assertEquals(100, this.client.getFader());
    }

    public void testFaderDelta() throws Throwable {
        assertEquals(35, this.client.getFader());
        this.client.setFaderDelta(10);
        assertEquals(45, this.client.getFader());
        this.client.setFaderDelta(-10);
        assertEquals(35, this.client.getFader());
        this.client.setFaderDelta(100);
        assertEquals(100, this.client.getFader());
        this.client.setFaderDelta(-100);
        assertEquals(0, this.client.getFader());
        this.client.setFaderDelta(-10);
        assertEquals(0, this.client.getFader());
    }

    public void testInputMode() throws Throwable {
        assertEquals(CP750InputMode.NON_SYNC, this.client.getInputMode());
        this.client.setInputMode(CP750InputMode.DIG_1);
        assertEquals(CP750InputMode.DIG_1, this.client.getInputMode());
        this.client.setInputMode(CP750InputMode.DIG_2);
        assertEquals(CP750InputMode.DIG_2, this.client.getInputMode());
        this.client.setInputMode(CP750InputMode.DIG_3);
        assertEquals(CP750InputMode.DIG_3, this.client.getInputMode());
        this.client.setInputMode(CP750InputMode.DIG_4);
        assertEquals(CP750InputMode.DIG_4, this.client.getInputMode());
        this.client.setInputMode(CP750InputMode.ANALOG);
        assertEquals(CP750InputMode.ANALOG, this.client.getInputMode());
        this.client.setInputMode(CP750InputMode.MIC);
        assertEquals(CP750InputMode.MIC, this.client.getInputMode());
        this.client.setInputMode(CP750InputMode.NON_SYNC);
        assertEquals(CP750InputMode.NON_SYNC, this.client.getInputMode());
        this.client.setInputMode(CP750InputMode.LAST);
        assertEquals(CP750InputMode.MIC, this.client.getInputMode());
        this.client.setInputMode(CP750InputMode.LAST);
        assertEquals(CP750InputMode.NON_SYNC, this.client.getInputMode());
    }

    public void testRefreshTimer() throws Throwable {
        assertEquals(0, this.client.getRefreshInterval());
        assertEquals(35, this.client.getFader());
        assertEquals("35", this.client.getCurrentValue(CP750Field.SYS_FADER));
        this.client.setRefreshInterval(2000);
        this.server.getStateEngine().fader = 40;
        assertEquals("35", this.client.getCurrentValue(CP750Field.SYS_FADER));
        Thread.sleep(2500);
        assertEquals("40", this.client.getCurrentValue(CP750Field.SYS_FADER));
        assertEquals(2000, this.client.getRefreshInterval());
    }

    public void testListener() throws Throwable {
        final ArrayList<String> container = new ArrayList<>();
        CP750Listener listener = (field, value) -> container.add(value);
        this.client.addListener(CP750Field.SYS_FADER, listener);
        assertTrue(container.isEmpty());
        this.client.setInputMode(CP750InputMode.DIG_1);
        assertTrue(container.isEmpty());
        this.client.setFader(10);
        assertEquals(1, container.size());
        assertEquals("10", container.get(0));
        this.client.setFader(20);
        assertEquals(2, container.size());
        assertEquals("20", container.get(1));
        this.client.removeListener(listener);
        this.client.setFader(30);
        assertEquals(2, container.size());
    }

    public void testOnetimeListener() throws Throwable {
        final ArrayList<String> container = new ArrayList<>();
        CP750Listener listener = (field, value) -> container.add(value);
        this.client.addOnetimeListener(CP750Field.SYS_FADER, listener);
        assertTrue(container.isEmpty());
        this.client.setInputMode(CP750InputMode.DIG_1);
        assertTrue(container.isEmpty());
        this.client.setFader(10);
        assertEquals(1, container.size());
        assertEquals("10", container.get(0));
        this.client.setFader(20);
        assertEquals(1, container.size());
        this.client.addOnetimeListener(CP750Field.SYS_FADER, listener);
        this.client.removeListener(listener);
        this.client.setFader(30);
        assertEquals(1, container.size());
    }

    public void testUnresponsiveConnection() throws Throwable {
        // normal connection test
        this.client.setInputMode(CP750InputMode.DIG_1);
        assertEquals(CP750InputMode.DIG_1, this.client.getInputMode());

        try {
            // now test with server paused.
            this.client.getSocket().setSoTimeout(1000);
            this.server.setPaused(true);
            this.client.setInputMode(CP750InputMode.DIG_2);
            fail("Expected SocketTimeoutException");
        } catch (SocketTimeoutException ignore) {
            // This is fine
        } catch (IOException e) {
            e.printStackTrace();
            fail("Unexpected IOException");
        }
    }

    public void testClosedConnection() throws Throwable {
        this.server.close();
        this.client.getSocket().setSoTimeout(1000);
        try {
            this.client.setInputMode(CP750InputMode.DIG_1);
            fail("SocketException expteced");
        } catch (SocketException ignore) {
            // This is fine
        }
    }

    public void testFailedServerConnection() throws Throwable {
        this.server.close();
        try {
            this.client = new CP750Client("127.0.0.1", this.server.getPort());
            fail("SocketException expected");
        } catch (SocketException ignore) {
            // THis is fine
        }
    }

    public void testFailedClientConnection() throws Throwable {
        this.client.getSocket().close();
        try {
            this.client.setInputMode(CP750InputMode.DIG_1);
            fail("SocketException expected");
        } catch (SocketException ignore) {
            // This is fine
        }
    }

    public void testClientClose() throws Throwable {
        assertEquals(1, this.server.getActiveConnections());
        this.client.close();
        assertEquals(0, this.server.getActiveConnections());
    }
}
