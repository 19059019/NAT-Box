
package networkaddresstranslation;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.Socket;
import java.util.Arrays;
import java.util.LinkedList;

 @SuppressWarnings("deprecation")
class clientInstance extends Thread {

  private DataInputStream clientMessage = null;
  public DataOutputStream output = null;
  public PrintStream print = null;
  public TableEntry self = null;
  public Socket client = null;
  private final clientInstance[] clientThreads;
  public boolean running = true;
  private LinkedList<TableEntry> natTable = new LinkedList<TableEntry>();
  private LinkedList<TableEntry> pool = new LinkedList<TableEntry>();
  public  boolean INTERNAL;
  public String message;

  public clientInstance(Socket client, clientInstance[] clientThreads, LinkedList<TableEntry> natTable, TableEntry self, LinkedList<TableEntry> pool) {
    this.client = client;
    this.clientThreads = clientThreads;
    this.natTable = natTable;
    this.self = self;
    this.pool = pool;

    if (self.getInternalIP().equals(self.getExternalIP())) {
      INTERNAL = false;
    } else {
      INTERNAL = true;
    }
  }

  public void run() {
    clientInstance[] clientThreads = this.clientThreads;
    try {
      clientMessage = new DataInputStream(client.getInputStream());
      output = new DataOutputStream(client.getOutputStream());
      print = new PrintStream(client.getOutputStream());
      byte[] packet = null;

      while (running) {
        System.out.println("-----------------");
        message = clientMessage.readLine();
        if (message.equals("e")) {
          break;
        }

        packet = new byte[Integer.parseInt(message)];
        clientMessage.read(packet);
        String[] p = parsePacket(packet);
        String source = p[0];
        String dest = p[1];
        String payload = p[2];
        boolean sourceInt = false;
        boolean destInt = false;
        boolean intIP = false;

        for (clientInstance c : clientThreads) {
          if (c != null && (c.self.getInternalIP().equals(source))){
            sourceInt = c.INTERNAL;
          }

          if (c != null && (c.self.getExternalIP().equals(dest) || c.self.getInternalIP().equals(dest))) {
            destInt = c.INTERNAL;
            if (c.self.getInternalIP().equals(dest)) {
              intIP = true;
            }
          }
        }
        System.out.println("\nPacket recieved by NATBox:");
        System.out.println(Arrays.toString(p));
        System.out.println("\nSOURCE INTERNAL: "+sourceInt);
        System.out.println("DESTINATION INTERNAL: "+destInt + "\n");
        // NB: Internal packets can only send to internal clients through internal IP
        if (sourceInt) {
          if (destInt) {
            // Source and Dest INTERNAL
            // DO NOTHING TO PACKET IF VALID
            if (!intIP) {
              // Invalid
              System.out.println("DESTINATION INVALID: " + dest);
              dest = "-1";
            } else {
              System.out.println("No packet alteration.");
            }
          } else {
            // Source and Not Dest INTERNAL
            // TRANSLATE SOURCE
            for (clientInstance c : clientThreads) {
              if (c != null && c.self.getInternalIP().equals(source)){
                source = c.self.getExternalIP();
                System.out.println("Source translated.");
              }
            }
          }
        } else {
          if (destInt) {
            // Not Source and Dest INTERNAL
            // TRANSLATE DESTINATION
            for (clientInstance c : clientThreads) {
              if (c != null && c.self.getExternalIP().equals(dest)){
                dest = c.self.getInternalIP();
                System.out.println("Destination translated.");
              }
            }
          } else {
            // Not source and Not Dest INTERNAL
            System.out.println("**Packet Dropped**");
            for (clientInstance c : clientThreads) {
              if (c!= null && (c.self.getInternalIP().equals(source) || c.self.getExternalIP().equals(source))) {
                packet = (source + "#" + dest + "#" + payload + "#Packet Could not be delivered").getBytes();
                c.print.println(packet.length);
                c.output.write(packet);
                System.out.println("\nERROR PACKET SENT:");
                System.out.println("["+source + ", "+dest+", "+payload+", Packet Could not be delivered]");
                break;
              }
            }
            System.out.println("-----------------");        
            continue;
          }
        }
        packet = buildPacket(source, dest, payload.getBytes());
        // Send packet
        boolean sent = false;
        for (clientInstance c : clientThreads) {
          if (c!= null && c.self.getInternalIP().equals(dest)) {
            c.print.println(packet.length);
            c.output.write(packet);
            System.out.println("\nSENT PACKET:");
            System.out.println("["+source + ", "+dest+", "+payload+"]");
            sent = true;
            break;
          }
        }
        // Return error packet
        if (!sent) {
          System.out.println("PACKET COULD NOT BE DELIVERED:");
          for (clientInstance c : clientThreads) {
            if (c!= null && (c.self.getInternalIP().equals(source) || c.self.getExternalIP().equals(source))) {
              packet = (source + "#" + dest + "#" + payload + "#Packet Could not be delivered").getBytes();
              c.print.println(packet.length);
              c.output.write(packet);
              System.out.println("\nERROR PACKET SENT:");
              System.out.println("["+source + ", "+dest+", "+payload+", Packet Could not be delivered]");

              break;
            }
          }
        }
        System.out.println("-----------------");        
      }
      // remove from NAT table
      natTable.remove(self);
      //Release IP if we get that far
      for (TableEntry t : pool) {
        if (t.getExternalIP().equals(self.getExternalIP())) {
          t.setExternalIP("0");
        }
      }
      // Set client to null
      for (clientInstance c : clientThreads) {
        if (c == this) {
          System.out.println(c.self.toString() + " *** HAS DISCONNECTED *** ");
          c = null;
        }
      }
      System.out.println("NAT Table (after disconnect): ");
      printTable(natTable);
      System.out.println("Pool (after disconnect): ");
      printTable(pool);
      clientMessage.close();
      output.close();
      client.close();
    } catch (IOException e) {
      for (clientInstance c : clientThreads) {
        if (c == this) {
          System.out.println(c.self.toString() + " *** HAS TIMED OUT *** ");
          c = null;
        }
      }
    }

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

  private static void printTable(LinkedList<TableEntry> table) {
    for (int i = 0; i < table.size(); i++) {
        System.out.println("\t" + table.get(i).toString());
    }
  }
}

