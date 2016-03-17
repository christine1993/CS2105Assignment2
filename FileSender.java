
// <Wang Hanyu A0105664H>

import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.zip.CRC32;

import FileSender.ACKThread;

import java.io.*;
import java.util.*;

public class FileSender {

	public DatagramSocket socket;
	public DatagramPacket pkt;
	public int clientPort;
	public InetAddress clientAddress ;
	public final int WINDOW_SIZE = 10;
	public int window_left = 10;
	public short baseIndex = 0;
	public static ArrayList<Short> receivedACKSeqNo = new ArrayList<Short>();
	public static ArrayList<Short> unACKedSeqNo = new ArrayList<Short>();
	public static HashMap<Short, byte[]> buffer = new HashMap<Short, byte[]>();

	public short endPktNo;

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
				socket.receive(rcvedpkt);
				short seqNumReceived = convertToShort(Arrays.copyOfRange(rcvedBuffer, 0, 2));
				while (true&&seqNumReceived!=endPktNo) {   // when comes to the end: this thread is not alive
					// should it be added only if it falls within the window?
					String response = new String(Arrays.copyOfRange(rcvedBuffer, 2, 8));
					if (response == "ACK") {
						if (!receivedACKSeqNo.contains(seqNumReceived)) {
							if (seqNumReceived ==unACKedSeqNo.get(0)) { //if seq is the smallest unACKed pkt, advance window base to next unACKed seq
								baseIndex=unACKedSeqNo.get(1);
							} 
							unACKedSeqNo.remove(new Short(seqNumReceived));
							receivedACKSeqNo.add(new Short(seqNumReceived));
							FileSender.buffer.remove(seqNumReceived);
						}
					}else{       
					//resend the packet
						DatagramPacket resendpkt=new DatagramPacket(FileSender.buffer.get(seqNumReceived),FileSender.buffer.get(seqNumReceived).length,clientAddress, clientPort);
						socket.send(resendpkt);
					}
					socket.receive(rcvedpkt);

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
			new FileSender(args[0], args[1], args[2], args[3]);
		} catch (Exception ex) {
			System.out.println("Exception during " + ex.getMessage());
		}
	}

	public FileSender(String fileToOpen, String host, String port, String rcvFileName) throws Exception {
		File file =new File(fileToOpen);
		long sizeOfFile=file.length();
		
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
		ackThread.start();
		while (true && ackThread.isAlive()) {
			if (flag == 0) { // if this packet is first packet containing name
				// first 2: seq num
				// second 2: checksum
				sendData = rcvFileName.getBytes();
				packet[0] = convertToBytes(seqNum)[0];
				packet[1] = convertToBytes(seqNum)[1];
				packet[2] = convertToBytes((short) checkSum(rcvFileName.getBytes()))[0];
				packet[3] = convertToBytes((short) checkSum(rcvFileName.getBytes()))[1];
				for (int i = 0; i < sendData.length; i++) {
					packet[i + 4] = sendData[i];
				}  
				// write the length of file from packet[500]
				for(int i=0;i<convertToBytes(sizeOfFile).length;i++){
					packet[500+i] =convertToBytes(sizeOfFile)[i];
				}
				flag++;
			} else {
				if (seqNum >= baseIndex && seqNum < (baseIndex + WINDOW_SIZE)) {
					if ((bis.read(sendData)) >= 0) {
						packet = new byte[1000];
						packet[0] = convertToBytes(seqNum)[0];
						packet[1] = convertToBytes(seqNum)[1];
						packet[2] = convertToBytes((short) checkSum(sendData))[0];
						packet[3] = convertToBytes((short) checkSum(sendData))[1];
						for (int i = 0; i < sendData.length; i++) {
							packet[i + 4] = sendData[i];
						}

					} else { // if no data
//						sendData = "END".getBytes();
//						packet[0] = convertToBytes(seqNum)[0];
//						packet[1] = convertToBytes(seqNum)[1];
//						endPktNo = seqNum;
//						packet[2] = convertToBytes((short) checkSum(sendData))[0];
//						packet[3] = convertToBytes((short) checkSum(sendData))[1];
//						for (int i = 0; i < sendData.length; i++) {
//							packet[i + 4] = sendData[i];
//						}
						break;
					}
				}
			}
			unACKedSeqNo.add(new Short(seqNum));
			buffer.put(seqNum, packet);
			pkt = new DatagramPacket(packet, packet.length, clientAddress, clientPort);
			socket.send(pkt);
			seqNum++;
			Thread.sleep(1);
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

}
