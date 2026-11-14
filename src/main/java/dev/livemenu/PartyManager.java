package dev.livemenu;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.client.server.IntegratedServer;

/**
 * Peer-to-peer party. The leader runs a tiny TCP listener; members connect to it using a party code
 * ("host:port/TOKEN"). The leader then broadcasts where the party is playing:
 *  - SERVER <address>  : any multiplayer server
 *  - WORLD <port>      : the leader's singleplayer world, opened to LAN (members use the leader's host + that port)
 * Networking runs on daemon threads; everything Minecraft-related happens in {@link #onTick}.
 */
public final class PartyManager {
    public enum Role { NONE, LEADER, MEMBER }

    public static final PartyManager I = new PartyManager();
    public static final int PARTY_PORT = 25599;

    // ---- state read by the UI (volatile: written by network threads) ----
    public volatile Role role = Role.NONE;
    public volatile int version = 0;          // bumped on any change so screens can refresh
    public volatile int destVersion = 0;      // bumped when the destination changes
    public volatile String destType = "NONE"; // NONE | SERVER | WORLD
    public volatile String destValue = "";
    public volatile String status = "";
    public volatile List<String> roster = List.of();
    public volatile String leaderName = "";
    public volatile String code = "";
    public volatile boolean autoJoin = true;
    public volatile boolean hostOnLoad = false;

    private ServerSocket serverSocket;
    private final List<Conn> conns = new CopyOnWriteArrayList<>();
    private Socket memberSocket;
    private PrintWriter memberOut;
    private String token = "";
    private String hostAddr = "";
    private String myName = "Player";
    private int seenDest = 0;

    private PartyManager() {}

    private static final class Conn {
        final Socket socket;
        final PrintWriter out;
        volatile String name = "?";
        Conn(Socket s) throws IOException {
            socket = s;
            out = new PrintWriter(new java.io.OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true);
        }
        void send(String line) { out.println(line); }
    }

    private void changed() { version++; }

    private static String clean(String s) {
        String t = s == null ? "" : s.replaceAll("[^A-Za-z0-9_]", "");
        if (t.isEmpty()) t = "Player";
        return t.length() > 16 ? t.substring(0, 16) : t;
    }

    public static String guessAddress() {
        try { return InetAddress.getLocalHost().getHostAddress(); }
        catch (Exception e) { return "127.0.0.1"; }
    }

    // =====================================================================
    //  Leader
    // =====================================================================
    public synchronized void createParty(String name, String publicAddress) {
        leaveParty();
        myName = clean(name);
        try {
            serverSocket = new ServerSocket(PARTY_PORT);
        } catch (IOException e) {
            status = "Could not open port " + PARTY_PORT + " (already in use?)";
            changed();
            return;
        }
        token = randomToken();
        leaderName = myName;
        code = publicAddress.trim() + ":" + PARTY_PORT + "/" + token;
        role = Role.LEADER;
        destType = "NONE";
        destValue = "";
        status = "Party created. Share the code with your friends.";
        rebuildRoster();
        changed();
        final ServerSocket ss = serverSocket;
        Thread t = new Thread(() -> acceptLoop(ss), "party-accept");
        t.setDaemon(true);
        t.start();
    }

    private static String randomToken() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) sb.append(chars.charAt(r.nextInt(chars.length())));
        return sb.toString();
    }

    private void acceptLoop(ServerSocket ss) {
        while (!ss.isClosed()) {
            try {
                Socket s = ss.accept();
                Thread t = new Thread(() -> handleMember(s), "party-member");
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                break;
            }
        }
    }

    private void handleMember(Socket s) {
        Conn c = null;
        try {
            s.setSoTimeout(8000);
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            String hello = in.readLine();
            String[] p = hello == null ? new String[0] : hello.split(" ", 3);
            if (p.length < 3 || !p[0].equals("HELLO") || !p[1].equals(token) || role != Role.LEADER) {
                new PrintWriter(s.getOutputStream(), true).println("DENIED");
                s.close();
                return;
            }
            c = new Conn(s);
            c.name = clean(p[2]);
            s.setSoTimeout(0);
            conns.add(c);
            c.send("WELCOME " + leaderName);
            rebuildRoster();
            c.send("DEST " + destType + " " + destValue);
            while (in.readLine() != null) { /* members don't send anything else */ }
        } catch (IOException ignored) {
        } finally {
            if (c != null) { conns.remove(c); rebuildRoster(); }
            try { s.close(); } catch (IOException ignored) {}
        }
    }

    private void rebuildRoster() {
        List<String> names = new ArrayList<>();
        names.add(leaderName + " (leader)");
        for (Conn c : conns) names.add(c.name);
        roster = List.copyOf(names);
        broadcast("ROSTER " + String.join(",", names));
        changed();
    }

    private void broadcast(String line) {
        for (Conn c : conns) c.send(line);
    }

    public void setDestinationServer(String address) {
        if (role != Role.LEADER || address == null || address.isBlank()) return;
        destType = "SERVER";
        destValue = address.trim();
        pushDestination();
    }

    public void setDestinationWorld(int port) {
        if (role != Role.LEADER) return;
        destType = "WORLD";
        destValue = String.valueOf(port);
        pushDestination();
    }

    public void clearDestination() {
        if (role != Role.LEADER) return;
        destType = "NONE";
        destValue = "";
        pushDestination();
    }

    private void pushDestination() {
        broadcast("DEST " + destType + " " + destValue);
        destVersion++;
        changed();
    }

    // =====================================================================
    //  Member
    // =====================================================================
    public void joinParty(String partyCode, String name) {
        leaveParty();
        myName = clean(name);
        final String host;
        final int port;
        final String tok;
        try {
            String c = partyCode.trim();
            int slash = c.lastIndexOf('/');
            tok = c.substring(slash + 1);
            String hp = c.substring(0, slash);
            int colon = hp.lastIndexOf(':');
            host = hp.substring(0, colon);
            port = Integer.parseInt(hp.substring(colon + 1));
        } catch (Exception e) {
            status = "That doesn't look like a party code (host:port/CODE).";
            changed();
            return;
        }
        status = "Connecting to party...";
        changed();
        Thread t = new Thread(() -> memberLoop(host, port, tok), "party-join");
        t.setDaemon(true);
        t.start();
    }

    private void memberLoop(String host, int port, String tok) {
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(host, port), 6000);
            PrintWriter out = new PrintWriter(new java.io.OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            out.println("HELLO " + tok + " " + myName);
            s.setSoTimeout(8000);
            String first = in.readLine();
            if (first == null || !first.startsWith("WELCOME ")) {
                status = "The party refused the connection (wrong code?).";
                changed();
                s.close();
                return;
            }
            s.setSoTimeout(0);
            synchronized (this) {
                memberSocket = s;
                memberOut = out;
                hostAddr = host;
                leaderName = first.substring(8);
                role = Role.MEMBER;
                status = "Joined " + leaderName + "'s party.";
            }
            changed();
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("ROSTER ")) {
                    roster = List.of(line.substring(7).split(","));
                    changed();
                } else if (line.startsWith("DEST ")) {
                    String[] p = line.split(" ", 3);
                    destType = p.length > 1 ? p[1] : "NONE";
                    destValue = p.length > 2 ? p[2] : "";
                    destVersion++;
                    changed();
                } else if (line.equals("DISBAND")) {
                    break;
                }
            }
            if (role == Role.MEMBER) { leaveParty(); status = "The party ended."; changed(); }
        } catch (IOException e) {
            if (role == Role.MEMBER) { leaveParty(); }
            status = "Could not reach the party: " + e.getMessage();
            changed();
        }
    }

    public String joinAddress() {
        if (destType.equals("SERVER")) return destValue;
        if (destType.equals("WORLD")) return hostAddr + ":" + destValue;
        return "";
    }

    // =====================================================================
    //  Shared
    // =====================================================================
    public synchronized void leaveParty() {
        if (role == Role.LEADER) broadcast("DISBAND");
        role = Role.NONE;
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
        serverSocket = null;
        for (Conn c : conns) { try { c.socket.close(); } catch (IOException ignored) {} }
        conns.clear();
        try { if (memberSocket != null) memberSocket.close(); } catch (IOException ignored) {}
        memberSocket = null;
        memberOut = null;
        destType = "NONE";
        destValue = "";
        roster = List.of();
        code = "";
        hostOnLoad = false;
        status = "";
        changed();
    }

    /** Connects this client to the party's current destination. Only call while no world is loaded. */
    public static void connect(Minecraft mc, Screen parent) {
        String address = I.joinAddress();
        if (address.isEmpty() || mc.level != null) return;
        ServerAddress addr = ServerAddress.parseString(address);
        ServerData data = new ServerData("Party", address, ServerData.Type.OTHER);
        ConnectScreen.startConnecting(parent, mc, addr, data, false, null);
    }

    private static int freePort() {
        try (ServerSocket s = new ServerSocket(0)) { return s.getLocalPort(); }
        catch (IOException e) { return 25600; }
    }

    /** Called every client tick from the main thread. */
    public void onTick(Minecraft mc) {
        // Leader: once the chosen singleplayer world is loaded, open it to LAN and tell the party.
        if (role == Role.LEADER && hostOnLoad && mc.player != null) {
            IntegratedServer srv = mc.getSingleplayerServer();
            if (srv != null) {
                int port;
                if (srv.isPublished()) {
                    port = srv.getPort();
                } else {
                    port = freePort();
                    if (!srv.publishServer(null, false, port)) port = -1;
                }
                if (port > 0) {
                    setDestinationWorld(port);
                    mc.player.sendSystemMessage(Component.literal("[Party] Your world is open to the party on port " + port + "."));
                }
                hostOnLoad = false;
            }
        }

        // Member: react to the leader changing the destination.
        if (destVersion != seenDest) {
            seenDest = destVersion;
            if (role == Role.MEMBER && !destType.equals("NONE")) {
                String where = destType.equals("WORLD") ? "their world" : destValue;
                if (mc.player != null) {
                    mc.player.sendSystemMessage(Component.literal(
                            "[Party] " + leaderName + " is playing on " + where + ". Open the Party screen to follow."));
                } else if (autoJoin && (mc.gui.screen() instanceof LiveTitleScreen || mc.gui.screen() instanceof PartyScreen)) {
                    connect(mc, mc.gui.screen());
                }
            }
        }
    }
}
