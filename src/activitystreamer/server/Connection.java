package activitystreamer.server;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import activitystreamer.util.Settings;

public class Connection extends Thread {
    private static final Logger log = LogManager.getLogger();
    private DataInputStream dis;
    private DataOutputStream dos;
    private BufferedReader br;
    private PrintWriter pw;
    private boolean open = false;
    private Socket socket;
    private boolean term = false;

    Connection(Socket socket) throws IOException {
        dis = new DataInputStream(socket.getInputStream());
        dos = new DataOutputStream(socket.getOutputStream());
        br = new BufferedReader(new InputStreamReader(dis));
        pw = new PrintWriter(dos, true);
        this.socket = socket;
        open = true;
        start();
    }

    /*
     * returns true if the message was written, otherwise false
     */
    public boolean writeMsg(String msg) {
        if (open) {
            pw.println(msg);
            pw.flush();
            return true;
        }
        return false;
    }

    public void closeCon() {
        if (open) {
            log.info("closing connection by closeCon" + Settings.socketAddress(socket));
            try {
                term = true;
                br.close();
                dos.close();
            } catch (IOException e) {
                // already closed?
                log.error("received exception closing the connection " + Settings.socketAddress(socket) + ": " + e);
            }
        }
    }

    public void run() {
        try {
            String data;
            while (!term && (data = br.readLine()) != null) {
                term = Control.getInstance().process(this, data);
            }
            log.debug("connection closed to " + Settings.socketAddress(socket));
            Control.getInstance().connectionClosed(this);
            dis.close();
        } catch (IOException e) {
            log.error("connection " + Settings.socketAddress(socket) + " closed with exception: " + e);
            Control.getInstance().connectionClosed(this);
        }
        open = false;
    }

    public Socket getSocket() {
        return socket;
    }

    public boolean isOpen() {
        return open;
    }

}
