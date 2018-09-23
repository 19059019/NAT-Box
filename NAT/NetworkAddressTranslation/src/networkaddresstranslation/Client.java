package networkaddresstranslation;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.io.IOException;
import java.io.PrintStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Random;

@SuppressWarnings("deprecation")
public class Client implements Runnable {

    private static Socket socket = null;
    private static DataInputStream in = null;
    private static DataInputStream reciever = null;
    private static DataOutputStream out = null;
    private static PrintStream print = null;
    private static String IP;
    private static String newIP;
    private static String MAC;
    private static String host;
    private static int port;

    /**
     * @param args the command line arguments args[0] : host args[1] : port
     * args[2] : internal/external (0/1)
     */
    public static void main(String[] args) {
        // Parse args
        host = args[0];
        port = Integer.parseInt(args[1]);
        String external = args[2];

        genMac();
        IP = genIP();
        if (external.equals("0")) {
            // request IP (I.E. Execute DHCP)
            dhcpClient();
            System.out.println("IP = " + IP + "; newIP = " + newIP);
        } else if (external.equals("1")) {
            //send something to make dhcpServer() add to NAT
            DatagramSocket socket = null;
            int count = 0;
            IP = genIP();
            newIP = IP;
            try {
                socket = new DatagramSocket();
                byte[] payload = new byte[100];
                payload[0] = 'e';
                byte[] byteIP = IP.getBytes();

                for (int i = 0; i < byteIP.length; i++) {
                    payload[i + 1] = byteIP[i];
                    count++;
                }
                payload[count + 1] = '%';
                DatagramPacket p = new DatagramPacket(payload, payload.length, InetAddress.getByName(host), 8000);
                socket.send(p);
            } catch (SocketException e) {
                System.err.println(e);
            } catch (IOException e) {
                System.err.println(e);
            }
            System.out.println("IP: " + IP);
        }
        System.out.println("MAC: " + MAC);
        // Connect to NAT-Box
        try {
            socket = new Socket(host, port);
            reciever = new DataInputStream(socket.getInputStream());
            in = new DataInputStream(new BufferedInputStream(System.in));
            out = new DataOutputStream(socket.getOutputStream());
            print = new PrintStream(socket.getOutputStream());
        } catch (UnknownHostException e) {
            System.err.println(e);
        } catch (IOException e) {
            System.err.println(e);
        }
        if (socket != null && reciever != null && out != null) {
            try {
                new Thread(new Client()).start();
                while (true) {
                    String message = in.readLine().trim();
                    if (message.startsWith("send")) {
                        String[] details = message.split(" ");
                        byte[] p = buildPacket(newIP, details[1], "payload".getBytes());
                        print.println(p.length);
                        out.write(p);
                    }
                    if (message.startsWith("EXIT")) {
                        System.out.println("Cheerio!");
                        print.println("e");
                        break;
                    }
                }
                out.close();
                in.close();
                reciever.close();
                socket.close();
                System.exit(0);
            } catch (IOException e) {
                System.err.println(e);
            }
        }
    }

    private static void dhcpClient() {
        DatagramSocket socket = null;
        String tempIP = "";
        int pos = 0;
        int count = 0;
        try {
            socket = new DatagramSocket();
            byte[] payload = new byte[100];
            payload[0] = 'd';
            //send IP as well
            for (int i = 0; i < IP.length(); i++) {
                payload[i + 1] = (byte) IP.charAt(i);
                pos = i + 1;
            }
            payload[pos + 1] = '%';
            for (int i = 1; i < payload.length; i++) {
                count++;
                if (payload[i] == '%') {
                    break;
                }
            }
            DatagramPacket p = new DatagramPacket(payload, payload.length, InetAddress.getByName(host), 8000);
            socket.send(p);
            System.out.println("\tsend discover");
            boolean listen = true;

            while (listen) {
                socket.receive(p);
                byte[] buff = p.getData();
                if (buff[0] == 'o') {
                    //send ip address you want
                    payload = new byte[100];
                    payload[0] = 'r';
                    count = 0;
                    for (int i = 1; i < buff.length; i++) {
                        payload[i] = buff[i];
                        count++;
                        if (buff[i] == '%') {
                            payload[i] = '%';
                            break;
                        }
                    }
                    tempIP = new String(buff, 1, count - 1);
                    p = new DatagramPacket(payload, payload.length, InetAddress.getByName(host), 8000);
                    socket.send(p);
                    System.out.println("\tsend request");
                }
                if (buff[0] == 'a') {
                    newIP = tempIP;
                    listen = false;
                    socket.close();
                    System.out.println("\tack recieved");
                }
            }
        } catch (SocketException e) {
            System.err.println(e);
        } catch (IOException e) {
            System.err.println(e);
        }
    }

    @Override
    public void run() {
        messageListener();
    }

    // Listens for packets from the connection
    public void messageListener() {
        byte[] packet = null;
        String message;

        try {
            while (true) {
                if ((message = reciever.readLine()) != null) {
                    // Recieve packet
                    packet = new byte[Integer.parseInt(message)];
                    reciever.read(packet);
                    String[] p = parsePacket(packet);
                    System.out.println(Arrays.toString(p));
                    if (p[2].equals("payload") && p.length <= 3) {
                        // Send Acknowledgement packet
                        String ack = newIP + "#" + p[0] + "#recieved";
                        packet = ack.getBytes();
                        print.println(packet.length);
                        out.write(packet);
                    }
                } else {
                    System.out.println("Lost connection with NATBox");
                    System.exit(0);
                }
            }
        } catch (IOException e) {
            System.out.println("DISCONNECTED");
        }
    }

    // Mac address generator found on stackoverflow (https://stackoverflow.com/a/24262057)
    private static void genMac() {
        Random rand = new Random();
        byte[] macAddr = new byte[6];
        rand.nextBytes(macAddr);
        macAddr[0] = (byte) (macAddr[0] & (byte) 254);
        StringBuilder sb = new StringBuilder(18);

        for (byte b : macAddr) {
            if (sb.length() > 0) {
                sb.append(":");
            }
            sb.append(String.format("%02x", b));
        }
        MAC = sb.toString();
    }

    private static String genIP() {
        Random r = new Random();
        return r.nextInt(256) + "." + r.nextInt(256) + "." + r.nextInt(256) + "." + r.nextInt(256);
    }

    private static byte[] buildPacket(String source, String destination, byte[] payload) {
        byte[] s = source.getBytes();
        byte[] d = destination.getBytes();
        byte del = '#';
        byte[] packet = new byte[s.length + d.length + payload.length + 2];
        int j = 0;

        for (int i = 0; i < s.length; i++) {
            packet[i] = s[j];
            j++;
        }
        packet[s.length] = del;
        j = 0;
        for (int i = s.length + 1; i < s.length + d.length + 1; i++) {
            packet[i] = d[j];
            j++;
        }
        packet[s.length + d.length + 1] = del;
        j = 0;
        for (int i = s.length + d.length + 2; i < packet.length; i++) {
            packet[i] = payload[j];
            j++;
        }

        return packet;
    }

    // Splits packet into string array [Source, Destination, Payload]
    private static String[] parsePacket(byte[] packet) {
        return (new String(packet)).split("#");
    }
}
