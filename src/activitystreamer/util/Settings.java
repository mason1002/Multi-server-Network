package activitystreamer.util;

import java.math.BigInteger;
import java.net.Socket;
import java.security.SecureRandom;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Random; //ms

public class Settings {
	private static final Logger log = LogManager.getLogger();
	private static SecureRandom random = new SecureRandom();
	private static int localPort = 3780;
	private static String localHostname = "localhost";
	private static String remoteHostname = null;
	private static int remotePort = 3780;
	private static int activityInterval = 5000; // milliseconds
	// server id.
	private static String serverId;
	// serverIdLength
	private static int serverIdLength = 26;
	private static boolean remoteAuthenticated = false;
	private static String serverSecret = "1";

	// for client
	private static String userSecret = null;
	private static String username = "anonymous";

	// set server id.
	public static void setServerId() {
		serverId = genRandomString();
	}

	// generate random String
	public static String genRandomString() {
		String range = "0123456789abcdefghijklmnopqrstuvwxyz";
		Random rd = new Random();
		StringBuffer randomId = new StringBuffer();
		// randomId.length = 26
		for (int i = 0; i < serverIdLength; i++) {
			randomId.append(range.charAt(rd.nextInt(range.length())));
		}
		return randomId.toString();
	}

	// get server id.
	public static String getServerId() {
		return serverId;
	}

	public static int getLocalPort() {
		return localPort;
	}

	public static void setLocalPort(int localPort) {
		if (localPort < 0 || localPort > 65535) {
			log.error("supplied port " + localPort + " is out of range, using " + getLocalPort());
		} else {
			Settings.localPort = localPort;
		}
	}

	public static int getRemotePort() {
		return remotePort;
	}

	public static void setRemotePort(int remotePort) {
		if (remotePort < 0 || remotePort > 65535) {
			log.error("supplied port " + remotePort + " is out of range, using " + getRemotePort());
		} else {
			Settings.remotePort = remotePort;
		}
	}

	public static String getRemoteHostname() {
		return remoteHostname;
	}

	public static void setRemoteHostname(String remoteHostname) {
		Settings.remoteHostname = remoteHostname;
	}

	public static int getActivityInterval() {
		return activityInterval;
	}

	public static void setActivityInterval(int activityInterval) {
		Settings.activityInterval = activityInterval;
	}

	public static String getUserSecret() {
		return userSecret;
	}

	public static void setUserSecret(String s) {
		userSecret = s;
	}

	public static String getUsername() {
		return username;
	}

	public static void setUsername(String username) {
		Settings.username = username;
	}

	public static String getLocalHostname() {
		return localHostname;
	}

	public static void setLocalHostname(String localHostname) {
		Settings.localHostname = localHostname;
	}

	public static String getServerSecret() {
		return serverSecret;
	}

	public static void setServerSecret(String serverSecret) {
		Settings.serverSecret = serverSecret;
	}

	public static boolean isRemoteAuthenticated() {
		return remoteAuthenticated;
	}

	public static void setRemoteAuthenticated(boolean remoteAuthenticated) {
		Settings.remoteAuthenticated = remoteAuthenticated;
	}

	/*
	 * some general helper functions
	 */

	public static String socketAddress(Socket socket) {
		return socket.getInetAddress() + ":" + socket.getPort();
	}

	public static String nextSecret() {
		return new BigInteger(130, random).toString(32);
	}

}
