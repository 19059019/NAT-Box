package networkaddresstranslation;

import java.io.PrintStream;
import java.util.ArrayList;
import java.net.Socket;
import java.net.ServerSocket;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.LinkedList;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

public class NATBox {

    private static ServerSocket server = null;
    private static Socket client = null;
    private static final int clientLimit = 10;
    private static long timeLimit = 300000;
    private static final clientInstance[] clientThreads = new clientInstance[clientLimit];
    private static PrintStream output = null;
    private static Boolean status = true;
    private static LinkedList<TableEntry> natTable = new LinkedList<TableEntry>();
    private static LinkedList<TableEntry> pool = new LinkedList<TableEntry>();
    private static final String MAC = genMac();
    private static final String IP = genIP();
    private static int counter = 0;
    private static boolean canTime = true;

    /**
     * @param args the command line arguments args[0] : time limit for each NAT
     * mapping in milliseconds
     */
    public static void main(String[] args) {
        int port = 8000;
        timeLimit = Long.parseLong(args[0]);
        Timer timer = new Timer();

        System.out.println("IP: " + IP);
        System.out.println("MAC: " + MAC);

        TimerTask timerTask1 = new TimerTask() {
            @Override
            public void run() {
                //clear NAT and other stuff
                natTable.remove();
                pool.get(counter).setExternalIP("0");
                try {
                    clientThreads[counter].client.close();
                    Thread.sleep(10);
                } catch (IOException e) {
                } catch (InterruptedException e) {
                }
                counter++;
                System.out.println("NAT Table (after timer): ");
                printTable(natTable);
                System.out.println("Pool (after timer): ");
                printTable(pool);
                canTime = true;
            }
        };

        TimerTask timerTask2 = new TimerTask() {
            @Override
            public void run() {
                //clear NAT and other stuff
                natTable.remove();
                pool.get(counter).setExternalIP("0");
                try {
                    clientThreads[counter].client.close();
                    Thread.sleep(10);
                } catch (IOException e) {
                } catch (InterruptedException e) {
                }
                counter++;
                System.out.println("NAT Table (after timer): ");
                printTable(natTable);
                System.out.println("Pool (after timer): ");
                printTable(pool);
                canTime = false;
            }
        };

        makePool(clientLimit);

        // open ServerSocket
        try {
            server = new ServerSocket(port);
        } catch (IOException e) {
            System.err.println(e);
        }

        // create new socket for each new client that attempts to connect
        while (status) {
            int i;

            dhcpServer(port);
            if (timeLimit < 30000) {
                if (canTime) {
                    timer.schedule(timerTask1, timeLimit);
                    canTime = false;
                } else {
                    timer.schedule(timerTask2, timeLimit);
                    canTime = true;
                }
            }
            System.out.println("NAT Table: ");
            printTable(natTable);
            try {
                client = server.accept();
                for (i = 0; i < clientLimit; i++) {
                    if (clientThreads[i] == null) {
                        clientThreads[i] = new clientInstance(client, clientThreads,
                                            natTable, natTable.get(natTable.size() - 1),
                                            pool);
                        clientThreads[i].start();
                        break;
                    }
                }
                // Message if too many clients have connected
                if (i == clientLimit) {
                    output = new PrintStream(client.getOutputStream());
                    output.println("Too many clients.");
                    output.close();
                    client.close();
                }
            } catch (IOException e) {
                System.err.println(e);
            }
        }
    }

    private static void dhcpServer(int port) {
        DatagramSocket socket = null;
        int pos = 0;
        String clientIP = "";
        int count = 0;
        int poolPos = 0;

        try {
            socket = new DatagramSocket(port);
            byte[] payload = new byte[100];
            DatagramPacket p = new DatagramPacket(payload, payload.length);
            boolean listen = true;

            while (listen) {
                socket.receive(p);
                byte[] buff = p.getData();
                int clientPort = p.getPort();
                InetAddress address = p.getAddress();

                if (buff[0] == 'e') {
                    System.out.println("\tExternal client");
                    //add to NAT table
                    for (int i = 1; i < buff.length; i++) {
                        count++;
                        if (buff[i] == '%') {
                            break;
                        }
                    }
                    clientIP = new String(buff, 1, count - 1);
                    TableEntry newClient = new TableEntry(clientIP, clientIP);
                    natTable.add(newClient);
                    listen = false;
                    socket.close();
                } else {
                    System.out.println("\tInternal client");
                }
                if (buff[0] == 'd') {
                    //send ip address to offer
                    //use a '%' to mark the end of the payload
                    //get the IP of the client
                    count = 0;

                    for (int i = 1; i < buff.length; i++) {
                        count++;
                        if (buff[i] == '%') {
                            break;
                        }
                    }
                    clientIP = new String(buff, 1, count - 1);
                    System.out.println("clientIP = " + clientIP);
                    byte[] intIP;
                    payload = new byte[100];
                    payload[0] = 'o';

                    //get from pool
                    count = 0;
                    for (int i = 0; i < pool.size(); i++) {
                        if (pool.get(i).getExternalIP().equals("0")) {
                            intIP = pool.get(i).getInternalIP().getBytes();
                            poolPos = i;
                            for (int j = 0; j < intIP.length; j++) {
                                payload[j + 1] = intIP[j];
                                count++;
                            }
                            payload[count + 1] = '%';
                            break;
                        }
                        System.out.println("Too many clients");
                    }
                    p = new DatagramPacket(payload, payload.length, address, clientPort);
                    socket.send(p);
                    System.out.println("\tsend offer");
                }

                if (buff[0] == 'r') {
                    //send ack 
                    //assign ip to client
                    String newIP = "";
                    count = 0;

                    for (int i = 1; i < buff.length; i++) {
                        count++;
                        if (buff[i] == '%') {
                            break;
                        }
                    }

                    newIP = new String(buff, 1, count - 1);
                    TableEntry newClient = new TableEntry(newIP, clientIP, timeLimit);
                    natTable.add(newClient);
                    pool.get(poolPos).setExternalIP(clientIP);
                    payload = new byte[100];
                    payload[0] = 'a';
                    payload[1] = '%';
                    p = new DatagramPacket(payload, payload.length, address, clientPort);
                    
                    socket.send(p);
                    System.out.println("\tsend ack");
                    listen = false;
                    socket.close();
                }
            }
        } catch (SocketException e) {
            System.err.println(e);
        } catch (IOException e) {
            System.err.println(e);
        }
    }

    private static String genMac() {
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

        return sb.toString();
    }

    private static void makePool(int clientLimit) {
        String prefix = "192.168.0.";

        for (int i = 0; i < clientLimit; i++) {
            String poolIP = prefix + i;
            TableEntry toPool = new TableEntry(poolIP, "0");
            pool.add(toPool);
        }
    }

    private static String genIP() {
        Random r = new Random();
        return r.nextInt(256) + "." + r.nextInt(256) + "." + r.nextInt(256) + "." + r.nextInt(256);
    }

    private static void printTable(LinkedList<TableEntry> table) {
        for (int i = 0; i < table.size(); i++) {
            System.out.println("\t" + table.get(i).toString());
        }
    }
}
