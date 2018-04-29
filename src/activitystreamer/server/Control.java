package activitystreamer.server;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import activitystreamer.util.Message;
import activitystreamer.util.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import activitystreamer.util.Settings;

public class Control extends Thread {
    private static final Logger log = LogManager.getLogger();
    private static List<Connection> clientConnections;
    private static boolean term = false;
    private static Listener listener;

    private static Control control = null;
    private static Connection parentConnection, lChildConnection, rChildConnection;
    private static Map<String, Integer> loadMap = new ConcurrentHashMap<>();
    private static List<User> userList; // the global registered users
    private static Vector<SocketAddress> loginVector = new Vector<>();
    private static Map<Connection, String[]> validateMap = new ConcurrentHashMap<>();
    private static Map<Connection, String> registerMap = new ConcurrentHashMap<>();
    private static Map<String, String[]> allowMap = new ConcurrentHashMap<>();
    // list to record if of cooperated servers;
    private String[] serverIdList = {"0", "0", "0"};

    public static Control getInstance() {
        if (control == null) {
            control = new Control();
        }
        return control;
    }

    private Control() {
        // initialize the clientConnections array
        clientConnections = Collections.synchronizedList(new ArrayList<>());
        userList = new CopyOnWriteArrayList<>();
        // userList = Collections.synchronizedList(new ArrayList<>());
        // start a listener
        try {
            listener = new Listener();
        } catch (IOException e1) {
            log.fatal("failed to startup a listening thread: " + e1);
            System.exit(-1);
        }
        start();
    }

    public void initiateConnection() {
        // make a connection to another server if remote hostname is supplied
        if (Settings.getRemoteHostname() != null) {
            try {
                Connection c = outgoingConnection(new Socket(Settings.getRemoteHostname(), Settings.getRemotePort()));
            } catch (IOException e) {
                log.error("failed to make connection to " + Settings.getRemoteHostname() + ":"
                        + Settings.getRemotePort() + " :" + e);
                System.exit(-1);
            }
        }
    }

    /**
     * Processing incoming messages from the connection. Return true if the
     * connection should close.
     *
     * @param con
     * @param msg result JSON string
     * @return
     */
    public synchronized boolean process(Connection con, String msg) {
        JSONObject request;
        try {
            request = (JSONObject) new JSONParser().parse(msg);
        } catch (Exception e) {
            return Message.invalidMsg(con, "the received message is not in valid format");
        }

        if (request.get("command") == null) {
            return Message.invalidMsg(con, "the received message did not contain a command");
        }

        String command = (String) request.get("command");
        switch (command) {
            case Message.INVALID_MESSAGE:
                return true;
            case Message.AUTHENTICATE:
                return authenticateIncomingConnection(con, request);
            case Message.AUTHENTICATION_FAIL:
                return authenticationFail();
            case Message.REGISTER:
                return register(con, request);
            case Message.LOCK_REQUEST:
                return onLockRequest(con, request);
            case Message.LOCK_DENIED:
                onLockDenied(con, request);
                return false;
            case Message.LOCK_ALLOWED:
                if (onLockAllowed(con, request)) {
                    return true;
                }
                return false;
            case Message.LOGIN:
                return login(con, request);
            case Message.LOGOUT:
                return logout(con);
            case Message.ACTIVITY_MESSAGE:
                return onReceiveActivityMessage(con, request);
            case Message.ACTIVITY_BROADCAST:
                return broadcastActivity(con, request);
            case Message.SERVER_ANNOUNCE:
                return onReceiveServerAnnounce(con, request);
            default:
                return Message.invalidMsg(con, "the received message is not in valid format");

        }

    }

    private boolean authenticateIncomingConnection(Connection con, JSONObject request) {
        if (request.get("secret") == null) {
            return Message.invalidMsg(con, "the received message did not contain a secret");
        }
        String secret = (String) request.get("secret");
        if (!secret.equals(Settings.getServerSecret())) {
            // if the secret is incorrect
            return Message.authenticationFail(con, "the supplied secret is incorrect: " + secret);
        } else if (lChildConnection == con || rChildConnection == con) {
            return Message.invalidMsg(con, "the server has already successfully authenticated");
        }
        // No reply if the authentication succeeded.
        clientConnections.remove(con);
        if (lChildConnection == null) {
            lChildConnection = con;
        } else if (rChildConnection == null) {
            rChildConnection = con;
        } else {
            // socket require closing
            con.closeCon();
            log.debug("the connection was refused");
        }
        return false;
    }

    private boolean authenticationFail() {
        if (parentConnection != null && parentConnection.isOpen()) {
            parentConnection.closeCon();
            parentConnection = null;
        }
        return true;
    }

    private boolean register(Connection con, JSONObject request) {
        // INVALID_MESSAGE - if anything is incorrect about the message,
        if (!request.containsKey("username") || !request.containsKey("secret")) {
            Message.invalidMsg(con, "The message is incorrect");
            return true;
        }
        // INVALID_MESSAGE - if receiving a REGISTER message from a client that has
        // already logged in on this connection.
        for (User user : userList) {
            if (isUserLoggedIn(user, con)) {
                Message.invalidMsg(con, "You have already logged in.");
                return true;
            }
        }
        String username = (String) request.get("username");
        String secret = (String) request.get("secret");
        //System.out.println("1");
        // If there's only one server in the system
        if (parentConnection == null && lChildConnection == null && rChildConnection == null) {
            if (isUserRegistered(username)) {
                return Message.registerFailed(con, username + " is already registered with the system"); // true
            } else {
                addUser(con, username, secret);
                return Message.registerSuccess(con, "register success for " + username);
            }
        } else { // If there're multiple servers in the system
            String[] validatedList = {"0", "0", "0"};
            validateMap.put(con, validatedList);
            registerMap.put(con, username);
            //allowMap.put(username, validatedList);
            addUser(con, username, secret);
            if (parentConnection != null) {
                Message.lockRequest(parentConnection, username, secret);
            }
            if (lChildConnection != null) {
                Message.lockRequest(lChildConnection, username, secret);
            }
            if (rChildConnection != null) {
                Message.lockRequest(rChildConnection, username, secret);
            }
            return false;
        }
    }

    private boolean onLockAllowed(Connection con, JSONObject request) {
        if (!con.equals(parentConnection) && !con.equals(lChildConnection) && !con.equals(rChildConnection)) {
            return Message.invalidMsg(con, "The connection has not authenticated");
        }
        String username = (String) request.get("username");
        String secret = (String) request.get("secret");
        //System.out.println("2");
        if (allowMap.containsKey(username)) {
            String[] allowList = allowMap.get(username);

            if (con.equals(parentConnection)) { // sent from parent node.
                if (lChildConnection != null) {
                    //System.out.println("8");
                    Message.lockAllowed(lChildConnection, username, secret); // send to left child
                }
                if (rChildConnection != null) {
                    //System.out.println("9");
                    Message.lockAllowed(rChildConnection, username, secret); // send to right child
                }
                allowMap.remove(username);
            }
            if (con.equals(lChildConnection)) { // sent from lChild node.
                allowList[1] = "1";
                //System.out.println(allowList[2]);
                if (allowList[2].equals("1") || rChildConnection == null) {
                    if (parentConnection != null) {
                        //System.out.println("5");
                        Message.lockAllowed(parentConnection, username, secret); // send to parent
                    }
                    allowMap.remove(username);
                }
                if (parentConnection == null || rChildConnection != null) {
                    Message.lockAllowed(rChildConnection, username, secret);
                    allowMap.remove(username);
                }
            }
            if (con.equals(rChildConnection)) { // sent from rChild node.
                allowList[2] = "1";
                if (allowList[1].equals("1") || (lChildConnection.equals(null))) {
                    if (parentConnection != null) {
                        //System.out.println("6");
                        Message.lockAllowed(parentConnection, username, secret); // send to parent
                    }
                    allowMap.remove(username);
                }
                if (parentConnection == null || lChildConnection != null) {
                    //System.out.println("7");
                    Message.lockAllowed(lChildConnection, username, secret);
                    allowMap.remove(username);
                }
            }
            if (allowMap.containsKey(username)) {
                allowMap.put(username, allowList);
            }
        }

        for (Connection temCon : clientConnections) {
            if (registerMap.containsKey(temCon)) {
                if (registerMap.get(temCon).equals(username)) {
                    String[] flags = validateMap.get(temCon);
                    if (con.equals(parentConnection)) {
                        flags[0] = "1";
                    }
                    if (con.equals(lChildConnection)) {
                        flags[1] = "1";
                    }
                    if (con.equals(rChildConnection)) {
                        flags[2] = "1";
                    }
                    validateMap.put(temCon, flags);
                    if (flags[0].equals(serverIdList[0]) & flags[1].equals(serverIdList[1])
                            & flags[2].equals(serverIdList[2])) {
                        validateMap.remove(temCon);
                        registerMap.remove(temCon);
                        Message.registerSuccess(temCon, "register success for " + username);
                    }
                    return false;
                }
            }
        }
        return false;
    }

    private void onLockDenied(Connection con, JSONObject request) {
        if (!con.equals(parentConnection) && !con.equals(lChildConnection) && !con.equals(rChildConnection)) {
            Message.invalidMsg(con, "The connection has not authenticated");
        }
        String username = (String) request.get("username");
        String secret = (String) request.get("secret");
        //System.out.println("3");
        if (con.equals(parentConnection)) {
            if (lChildConnection != null) {
                Message.lockDenied(lChildConnection, username, secret);
            }
            if (rChildConnection != null) {
                Message.lockDenied(rChildConnection, username, secret);
            }
        } else {
            if (parentConnection != null) {
                Message.lockDenied(parentConnection, username, secret);
            }
            if (con.equals(lChildConnection)) {
                if (rChildConnection != null) {
                    Message.lockDenied(rChildConnection, username, secret);
                }
            }
            if (con.equals(rChildConnection)) {
                if (lChildConnection != null) {
                    Message.lockDenied(lChildConnection, username, secret);
                }
            }
        }

        for (Connection temCon : clientConnections) {
            if (registerMap.containsKey(temCon)) {
                if (registerMap.get(temCon).equals(username)) {
                    Message.registerFailed(temCon, username + " is already registered with the system");
                    temCon.closeCon();
                }
            }
        }

        for (User user : userList) {
            if (user.getUserName().equals(username) & user.getPassword().equals(secret)) {
                userList.remove(user);
            }
        }
    }

    private boolean onLockRequest(Connection con, JSONObject request) {
        if (!con.equals(parentConnection) && !con.equals(lChildConnection) && !con.equals(rChildConnection)) {
            return Message.invalidMsg(con, "The connection has not authenticated");
        }
        String username = (String) request.get("username");
        String secret = (String) request.get("secret");
        //System.out.println("4");
        String[] validatedList = {"0", "0", "0"};
        allowMap.put(username, validatedList);
        if (isUserRegistered(username)) { // almost useless
            for (User user : userList) {
                if (user.getUserName().equals(username) & user.getPassword().equals(secret)) {
                    userList.remove(user);
                }
            }
            if (lChildConnection != null) {
                Message.lockDenied(lChildConnection, username, secret);
            }
            if (rChildConnection != null) {
                Message.lockDenied(rChildConnection, username, secret);
            }
            if (parentConnection != null) {
                Message.lockDenied(parentConnection, username, secret);
            }
        } else { // if the username is not already known to the server
            addUser(con, username, secret); // record this username and secret pair in its local storage.
            if (con.equals(parentConnection)) { // if from parent
                if (lChildConnection == null & rChildConnection == null) {
                    Message.lockAllowed(parentConnection, username, secret);
                    return false;
                }
                if (lChildConnection != null) {
                    Message.lockRequest(lChildConnection, username, secret);
                }
                if (rChildConnection != null) {
                    Message.lockRequest(rChildConnection, username, secret);
                }
            } else { // if from child
                if (parentConnection != null) {
                    Message.lockRequest(parentConnection, username, secret);
                } else {
                    if (lChildConnection != null) {
                        Message.lockAllowed(lChildConnection, username, secret);
                    }
                    if (rChildConnection != null) {
                        Message.lockAllowed(rChildConnection, username, secret);
                    }
                }
            }
        }
        return false;
    }

    private void addUser(Connection con, String username, String secret) {
        if (con.equals(null)) {
            User user = new User(null, username, secret);
        }
        User user = new User(con.getSocket().getRemoteSocketAddress(), username, secret);
        userList.add(user);
    }

    private boolean isUserRegistered(String username) {
        boolean flag = false;
        for (User user : userList) {
            if (user.getUserName().equals(username)) {
                flag = true;
            }
        }
        return flag;
    }

    private boolean isUserLoggedIn(User user, Connection con) {
        return user.getLocalSocketAddress().equals(con.getSocket().getRemoteSocketAddress())
                && loginVector.contains(user.getLocalSocketAddress());
    }

    private boolean onReceiveServerAnnounce(Connection con, JSONObject request) {
        // loadMap.put(con, ((Long) request.get("load")).intValue());
        loadMap.put(request.get("hostname") + ":" + request.get("port"), ((Long) request.get("load")).intValue());
        if (parentConnection != null && con != parentConnection) {
            parentConnection.writeMsg(request.toJSONString());
        }
        if (lChildConnection != null && con != lChildConnection) {
            lChildConnection.writeMsg(request.toJSONString());
        }
        if (rChildConnection != null && con != rChildConnection) {
            rChildConnection.writeMsg(request.toJSONString());
        }

        if (con.equals(parentConnection)) {
            serverIdList[0] = "1";
        }
        if (con.equals(lChildConnection)) {
            serverIdList[1] = "1";
        }
        if (con.equals(rChildConnection)) {
            serverIdList[2] = "1";
        }
        return false;

    }

    private String checkOtherLoads() {
        for (Map.Entry<String, Integer> entry : loadMap.entrySet()) {
            if (clientConnections.size() - entry.getValue() >= 2) {
                return entry.getKey();
            }
        }
        return null;
    }

    private boolean login(Connection con, JSONObject request) {
        if (request.containsKey("username") && request.get("username").equals("anonymous")) { // anonymous login
            Message.loginSuccess(con, "logged in as user " + request.get("username"));
            loginVector.add(con.getSocket().getRemoteSocketAddress());
            if (checkOtherLoads() != null) {
                return Message.redirect(con, checkOtherLoads());
            }
            return false;
        } else if (request.containsKey("username") && request.containsKey("secret")) { // username login
            String username = (String) request.get("username");
            String secret = (String) request.get("secret");
            boolean foundUser = false;
            for (User user : userList) {
                if (user.getUserName().equals(username)) {
                    foundUser = true;
                    if (user.getPassword().equals(secret)) {
                        Message.loginSuccess(con, "logged in as user " + username);
                        // Here's a bug.
                        loginVector.add(user.getLocalSocketAddress());
                        if (checkOtherLoads() != null) {
                            return Message.redirect(con, Objects.requireNonNull(checkOtherLoads()));
                        }
                        return false;
                    } else {
                        return Message.loginFailed(con, "attempt to login with wrong secret");
                    }
                }
            }
            if (!foundUser) {
                return Message.loginFailed(con, "attempt to login with wrong username");
            }
        } else {
            return Message.invalidMsg(con, "missed username or secret");
        }
        loginVector.add(con.getSocket().getRemoteSocketAddress());
        return false;
    }

    private boolean logout(Connection con) {
        boolean logout = false;
        for (User user : userList) {
            if (user.getLocalSocketAddress().equals(con.getSocket().getRemoteSocketAddress())) {
                loginVector.remove(con.getSocket().getRemoteSocketAddress());
                logout = true;
            }
        }
        if (logout) {
            con.closeCon();
        }
        return logout;
    }

    private boolean isUserLoggedInLocally(String username, String secret) {
        boolean flag = false;
        for (User user : userList) {
            if (user.getUserName().equals(username) && user.getPassword().equals(secret)
                    && loginVector.contains(user.getLocalSocketAddress())) {
                flag = true;
            }
        }
        return flag;
    }

    private boolean onReceiveActivityMessage(Connection con, JSONObject request) {
        if (!request.containsKey("username")) {
            return Message.invalidMsg(con, "the message did not contain a username");
        }
        if (!request.containsKey("secret")) {
            return Message.invalidMsg(con, "the message did not contain a secret");
        }
        if (!request.containsKey("activity")) {
            return Message.invalidMsg(con, "the message did not contain an activity");
        }
        String username = (String) request.get("username");
        String secret = (String) request.get("secret");
        JSONObject activity = (JSONObject) request.get("activity");
        activity.put("authenticated_user", username);

        JSONObject broadcastAct = new JSONObject();
        broadcastAct.put("activity", activity);
        broadcastAct.put("command", Message.ACTIVITY_BROADCAST);

        if (!username.equals("anonymous") && !isUserLoggedInLocally(username, secret)) {
            return Message.authenticationFail(con, "the username and secret do not match the logged in the user, "
                    + "or the user has not logged in yet");
        }
        return broadcastActivity(con, broadcastAct);
    }

    private boolean broadcastActivity(Connection sourceConnection, JSONObject activity) {
        for (Connection c : clientConnections) {
            Message.activityBroadcast(c, activity);
        }
        // broadcast activity to other servers except the one it comes from
        if (parentConnection != null && parentConnection != sourceConnection) {
            Message.activityBroadcast(parentConnection, activity);
        }
        if (lChildConnection != null && lChildConnection != sourceConnection) {
            Message.activityBroadcast(lChildConnection, activity);
        }
        if (rChildConnection != null && rChildConnection != sourceConnection) {
            Message.activityBroadcast(rChildConnection, activity);
        }
        return false;
    }

    /**
     * The connection has been closed by the other party.
     *
     * @param con
     */
    public synchronized void connectionClosed(Connection con) {
        if (term) {
            return;
        }
        if (clientConnections.contains(con)) {
            clientConnections.remove(con);
        }
        if (parentConnection == con) {
            parentConnection = null;
        }
        if (lChildConnection == con) {
            lChildConnection = null;
        }
        if (rChildConnection == con) {
            rChildConnection = null;
        }

    }

    /**
     * A new incoming connection has been established, and a reference is returned
     * to it. 1. remote server -> local server 2. client -> local server
     *
     * @param s
     * @return
     * @throws IOException
     */
    public synchronized Connection incomingConnection(Socket s) throws IOException {
        log.debug("incoming connection: " + Settings.socketAddress(s));
        Connection c = new Connection(s);
        clientConnections.add(c);
        return c;
    }

    /**
     * A new outgoing connection has been established, and a reference is returned
     * to it. Only local server -> remote server remote server will be the parent of
     * local server
     *
     * @param s
     * @return
     * @throws IOException
     */
    public synchronized Connection outgoingConnection(Socket s) throws IOException {
        log.debug("outgoing connection: " + Settings.socketAddress(s));
        Connection c = new Connection(s);
        parentConnection = c;
        Message.authenticate(c);
        return c;
    }

    @Override
    public void run() {
        log.info("using activity interval of " + Settings.getActivityInterval() + " milliseconds");
        while (!term) {
            // do something with 5 second intervals in between
            if (parentConnection != null) {
                Message.serverAnnounce(parentConnection, clientConnections.size());
            }
            if (lChildConnection != null) {
                Message.serverAnnounce(lChildConnection, clientConnections.size());
            }
            if (rChildConnection != null) {
                Message.serverAnnounce(rChildConnection, clientConnections.size());
            }
            try {
                Thread.sleep(Settings.getActivityInterval());
            } catch (InterruptedException e) {
                log.info("received an interrupt, system is shutting down");
                break;
            }
        }
        log.info("closing " + clientConnections.size() + " client connections");
        // clean up
        for (Connection connection : clientConnections) {
            connection.closeCon();
        }

        listener.setTerm(true);
    }

    public final void setTerm(boolean t) {
        term = t;
    }
}
