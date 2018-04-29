package activitystreamer.util;

import activitystreamer.server.Connection;

import org.json.simple.JSONObject;

public class Message {
    public static final String AUTHENTICATE = "AUTHENTICATE";
    public static final String INVALID_MESSAGE = "INVALID_MESSAGE";
    public static final String AUTHENTICATION_FAIL = "AUTHENTICATION_FAIL";
    public static final String LOGIN = "LOGIN";
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String REDIRECT = "REDIRECT";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String LOGOUT = "LOGOUT";
    public static final String ACTIVITY_MESSAGE = "ACTIVITY_MESSAGE";
    public static final String SERVER_ANNOUNCE = "SERVER_ANNOUNCE";
    public static final String ACTIVITY_BROADCAST = "ACTIVITY_BROADCAST";
    public static final String REGISTER = "REGISTER";
    public static final String REGISTER_FAILED = "REGISTER_FAILED";
    public static final String REGISTER_SUCCESS = "REGISTER_SUCCESS";
    public static final String LOCK_REQUEST = "LOCK_REQUEST";
    public static final String LOCK_DENIED = "LOCK_DENIED";
    public static final String LOCK_ALLOWED = "LOCK_ALLOWED";

    public synchronized static boolean invalidMsg(Connection con, String info) {
        JSONObject json = new JSONObject();
        json.put("command", Message.INVALID_MESSAGE);
        json.put("info", info);
        con.writeMsg(json.toJSONString());
        System.out.println("invalid msg so I closed");
        con.closeCon();
        return true;
    }

    public synchronized static void authenticate(Connection con) {
        JSONObject json = new JSONObject();
        json.put("command", Message.AUTHENTICATE);
        json.put("secret", Settings.getServerSecret());
        con.writeMsg(json.toJSONString());
    }

    public synchronized static boolean authenticationFail(Connection con, String info) {
        JSONObject json = new JSONObject();
        json.put("command", Message.AUTHENTICATION_FAIL);
        json.put("info", info);
        con.writeMsg(json.toJSONString());
        System.out.println("authenticationFail so I closed");
        con.closeCon();
        return true;
    }

    public synchronized static void serverAnnounce(Connection con, int load) {
        JSONObject json = new JSONObject();
        json.put("command", Message.SERVER_ANNOUNCE);
        json.put("id", Settings.getServerId());
        json.put("load", load);
        json.put("hostname", Settings.getLocalHostname());
        json.put("port", Settings.getLocalPort());
        con.writeMsg(json.toJSONString());
    }

    public synchronized static boolean lockRequest(Connection con, String username, String secret) {
        JSONObject json = new JSONObject();
        json.put("command", Message.LOCK_REQUEST);
        json.put("username", username);
        json.put("secret", secret);
        con.writeMsg(json.toJSONString());
        return false;
    }

    public synchronized static boolean lockDenied(Connection con, String username, String secret) {
        JSONObject json = new JSONObject();
        json.put("command", Message.LOCK_DENIED);
        json.put("username", username);
        json.put("secret", secret);
        con.writeMsg(json.toJSONString());
        return false;
    }

    public synchronized static boolean lockAllowed(Connection con, String username, String secret) {
        JSONObject json = new JSONObject();
        json.put("command", Message.LOCK_ALLOWED);
        json.put("username", username);
        json.put("secret", secret);
        con.writeMsg(json.toJSONString());
        return false;
    }

    public synchronized static boolean registerFailed(Connection con, String info) {
        JSONObject json = new JSONObject();
        json.put("command", Message.REGISTER_FAILED);
        json.put("info", info);
        con.writeMsg(json.toJSONString());
        System.out.println("register failed so I closed");
        return true;
    }

    public synchronized static boolean registerSuccess(Connection con, String info) {
        JSONObject json = new JSONObject();
        json.put("command", Message.REGISTER_SUCCESS);
        json.put("info", info);
        con.writeMsg(json.toJSONString());
        return false;
    }

    /**
     * Client register
     *
     * @param userName
     * @param secret
     * @return
     */
    public synchronized static String register(String userName, String secret) {
        JSONObject json = new JSONObject();
        json.put("command", Message.REGISTER);
        json.put("username", userName);
        json.put("secret", secret);
        return json.toJSONString();
    }

    /**
     * Client anonymous login
     *
     * @return
     */
    public synchronized static String login() {
        JSONObject json = new JSONObject();
        json.put("command", Message.LOGIN);
        json.put("username", Settings.getUsername());
        return json.toJSONString();
    }

    /**
     * Client normal login
     *
     * @param userName
     * @return
     */
    public synchronized static String login(String userName) {
        JSONObject json = new JSONObject();
        json.put("command", Message.LOGIN);
        json.put("username", userName);
        json.put("secret", Settings.getUserSecret());
        return json.toJSONString();
    }

    public synchronized static boolean loginSuccess(Connection con, String info) {
        JSONObject json = new JSONObject();
        json.put("command", Message.LOGIN_SUCCESS);
        json.put("info", info);
        con.writeMsg(json.toJSONString());
        return false;
    }

    public synchronized static boolean loginFailed(Connection con, String info) {
        JSONObject json = new JSONObject();
        json.put("command", Message.LOGIN_FAILED);
        json.put("info", info);
        con.writeMsg(json.toJSONString());
        return true;
    }

    public synchronized static boolean redirect(Connection con, String address) {
        JSONObject json = new JSONObject();
        json.put("command", Message.REDIRECT);
        String[] stringArr = address.split(":");
        json.put("hostname", stringArr[0]);
        json.put("port", Integer.parseInt(stringArr[1]));
        con.writeMsg(json.toJSONString());
        System.out.println("redirect so I closed");
        con.closeCon();
        return true;
    }

    public synchronized static boolean activityBroadcast(Connection con, JSONObject activity) {
//        JSONObject json = new JSONObject();
//        json.put("command", Message.ACTIVITY_BROADCAST);
//        json.put("activity", activity);
        con.writeMsg(activity.toJSONString());
        return false;
    }

    public static JSONObject connCloseMsg() {
        JSONObject json = new JSONObject();
        StringBuilder sb = new StringBuilder();
        sb.append("connection closed to /");
        sb.append(Settings.getRemoteHostname() + ":" + Settings.getRemotePort());
        sb.append(", please restart new connection");
        json.put("info", sb.toString());
        return json;
    }
    
    public static JSONObject redirectMsg() {
        JSONObject json = new JSONObject();
        StringBuilder sb = new StringBuilder();
        sb.append("Start new connection to /");
        sb.append(Settings.getRemoteHostname() + ":" + Settings.getRemotePort());
        sb.append(", please wait");
        json.put("info", sb.toString());
        return json;
    }
}
