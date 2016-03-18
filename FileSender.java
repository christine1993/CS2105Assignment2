
// kakak
// <Wang Hanyu A0105664H>

import java.net.*;
import java.util.zip.CRC32;

//import FileSender.ACKThread;

import java.io.*;
import java.util.*;

public class FileSender {

	public DatagramSocket socket;
	public DatagramPacket pkt;
	public int clientPort;
	public InetAddress clientAddress;
	public final int WINDOW_SIZE = 10;
	public int window_left = 10;
	public short baseIndex = 0;
	public static ArrayList<Short> receivedACKSeqNo = new ArrayList<Short>();
	public static ArrayList<Short> unACKedSeqNo = new ArrayList<Short>();
	public static HashMap<Short, byte[]> buffer = new HashMap<Short, byte[]>();

	public boolean isEnd = false;

	// ack thread
	class ACKThread extends Thread {
		ACKThread() {
			super("my extending thread");
			start();
		}

		public synchronized void run() {
			try {
				byte[] rcvedBuffer = new byte[8];
				DatagramPacket rcvedpkt = new DatagramPacket(rcvedBuffer, rcvedBuffer.length);
				while (true) { // when comes to the end: this thread is not
								// alive
					// should it be added only if it falls within the window?
					System.out.println("-----------------Inside thread loop--------------------");
					socket.receive(rcvedpkt);
					short seqNumReceived = convertToShort(Arrays.copyOfRange(rcvedBuffer, 0, 2));
					String response = new String(Arrays.copyOfRange(rcvedBuffer, 2, 8));
					System.out.println("-----------------RECEIVE PACKET "+seqNumReceived+" "+response+"--------------------");
					if (response == "ACK") {
						if (!receivedACKSeqNo.contains(seqNumReceived)) {
							if (seqNumReceived == unACKedSeqNo.get(0)) { // if
																			// seq
																			// is
																			// the
																			// smallest
																			// unACKed
																			// pkt,
																			// advance
																			// window
																			// base

								baseIndex = unACKedSeqNo.get(1);
							}
							unACKedSeqNo.remove(new Short(seqNumReceived));
							receivedACKSeqNo.add(new Short(seqNumReceived));
							FileSender.buffer.remove(seqNumReceived);
						}
					} else if (response == "NAK") {
						// resend the packet
						DatagramPacket resendpkt = new DatagramPacket(FileSender.buffer.get(seqNumReceived),
								FileSender.buffer.get(seqNumReceived).length, clientAddress, clientPort);
						socket.send(resendpkt);
					} else if (response == "FIN") {
						return;
					}

				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				System.out.println("IOException: " + e.getMessage());
			}
			System.out.println("My thread run is over");
		}
	}

	public static void main(String[] args) {

		// check if the number of command line argument is 4
		if (args.length != 4) {
			System.out.println("Usage: java FileSender <path/filename> " + "<rcvHostName> <rcvPort> <rcvFileName>");
			System.exit(1);
		}

		try {
			FileSender fs = new FileSender();
			fs.run(args[0], args[1], args[2], args[3]);
		} catch (Exception ex) {
			System.out.println("Exception during " + ex.getClass().toString()+"  "+ ex.getMessage());
		}
	}

	public synchronized void run(String fileToOpen, String host, String port, String rcvFileName) throws Exception {
		File file = new File(fileToOpen);
		long sizeOfFile = file.length();

		clientPort = Integer.parseInt(port);
		clientAddress = InetAddress.getByName(host);

		socket = new DatagramSocket();

		FileInputStream fis = new FileInputStream(fileToOpen);
		BufferedInputStream bis = new BufferedInputStream(fis);

		short seqNum = 0;
		byte[] packet = new byte[1000];
		byte[] sendData = new byte[996];
		int flag = 0;

		// packet used to receive response
		ACKThread ackThread = new ACKThread();
	    System.out.println("---------------------------------BEFORE START OF TREAD---------------------------------");
		//ackThread.start();
	    System.out.println("---------------------------------AFTER START OF TREAD---------------------------------");

		while (ackThread.isAlive()) {
			if (flag == 0) { // if this packet is first packet containing name
			    System.out.println("----------------------------THIS IS THE FIRST PACKET---------------------------------");

				// first 2: seq num
				// second 2: checksum
				sendData = rcvFileName.getBytes();
				packet[0] = convertToBytes(seqNum)[0];
				packet[1] = convertToBytes(seqNum)[1];
				for (int i = 0; i < sendData.length; i++) {
					packet[i + 4] = sendData[i];
				}
				// write the length of file from packet[500]
				for (int i = 0; i < convertToBytes(sizeOfFile).length; i++) {
					packet[500 + i] = convertToBytes(sizeOfFile)[i];
				}
				packet[2] = convertToBytes((short) checkSum(Arrays.copyOfRange(packet, 4, 1000)))[0];
				packet[3] = convertToBytes((short) checkSum(Arrays.copyOfRange(packet, 4, 1000)))[1];
			    System.out.println("-------------------------FILE NAME IS------"+sendData);	
				flag++;
			} else if (seqNum >= baseIndex && seqNum < (baseIndex + WINDOW_SIZE)) {
				System.out.println("-------------------------seq number inside window------ ");
				if ((bis.read(sendData)) >= 0) {
					packet = new byte[1000];
					packet[0] = convertToBytes(seqNum)[0];
					packet[1] = convertToBytes(seqNum)[1];
					for (int i = 0; i < sendData.length; i++) {
						packet[i + 4] = sendData[i];
					}
					packet[2] = convertToBytes((short) checkSum(sendData))[0];
					packet[3] = convertToBytes((short) checkSum(sendData))[1];
				}
			} else {
				continue;
			}
			System.out.println("-------------------------Sending packet number------ "+seqNum);
			unACKedSeqNo.add(new Short(seqNum));
			buffer.put(seqNum, packet);
			pkt = new DatagramPacket(packet, packet.length, clientAddress, clientPort);
			socket.send(pkt);
			seqNum++;
			Thread.sleep(1000);
		}

		System.out.println("File sent completed!");

		bis.close();
		System.exit(1);
	}

	public byte[] convertToBytes(short value) {
		byte[] byteArray = new byte[2];

		byteArray[0] = (byte) (value >> 8);
		byteArray[1] = (byte) value;
		return byteArray;
	}

	public short convertToShort(byte[] byteArray) {
		short value1 = (short) (byteArray[0] << 8);
		short value2 = (short) (byteArray[1]);
		return (short) (value1 | value2);
	}

	public long checkSum(byte[] bytes) {
		CRC32 crc = new CRC32();
		try {
			crc.update(bytes);
		} catch (Exception e) {
			System.out.println("IOException occurs! ");

		}
		return crc.getValue();

	}

	public byte[] convertToBytes(long n) {
		byte[] bytes = new byte[8];

		bytes[7] = (byte) (n);
		n >>>= 8;
		bytes[6] = (byte) (n);
		n >>>= 8;
		bytes[5] = (byte) (n);
		n >>>= 8;
		bytes[4] = (byte) (n);
		n >>>= 8;
		bytes[3] = (byte) (n);
		n >>>= 8;
		bytes[2] = (byte) (n);
		n >>>= 8;
		bytes[1] = (byte) (n);
		n >>>= 8;
		bytes[0] = (byte) (n);

		return bytes;
	}
}
