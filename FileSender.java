
// kakak
// <Wang Hanyu A0105664H>

import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.CRC32;
import java.nio.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class FileSender {

	public final int TIME_OUT_NS = 1000000000; // 1000ms
	public DatagramSocket socket;
	public int clientPort;
	public InetAddress clientAddress;
	public final int WINDOW_SIZE = 10;
	public int window_left = 10;
	public Short baseIndex = 0;
	public static ArrayList<Short> receivedACKSeqNo = new ArrayList<Short>();
	Map<String, String> map = Collections.synchronizedMap(
			  new LinkedHashMap<String, String>());
	public static Map<Short, byte[]> buffer = Collections.synchronizedMap(new LinkedHashMap<Short, byte[]>());
	public static Map<Short, Long> timer = new ConcurrentHashMap<Short, Long>();
	public boolean isEnd = false;
	
	// timer thread
	class TimerThread extends Thread {
		TimerThread() {
			super("my timer thread");
		}

		public synchronized void run() {
			try {
				System.out.println("-----------------Timer is running--------------------");
				while (!isEnd) {
					System.out.println("-----------------Now Timer size is "+FileSender.timer.size()+"--------------------");
					if (!timer.isEmpty()) {
						Iterator<Short> it = timer.keySet().iterator();
						if (it!=null){
						while(it.hasNext()) {
							System.out.println("---------it.next--------");
							Short seq = it.next();
							if (timer.containsKey(seq) && buffer.containsKey(seq)) {
								System.out.println("---------before timer.get(seq)--------");
								Long thisTime = timer.get(seq);
								System.out.println("---------after timer.get(seq)--------");
								if (thisTime != null) {
									Long timeDiff = System.nanoTime() - thisTime;
									System.out.println("----------------- Time Diff for seq"+seq+"is "+timeDiff+"!--------------------");
									if (timeDiff > TIME_OUT_NS) {
										System.out.println("----------------- Package number "+seq+" times out! Resending!--------------------");
										byte[] byteArray = FileSender.buffer.get(seq);
										if (byteArray != null) {
											DatagramPacket resendpkt = new DatagramPacket(byteArray, byteArray.length, clientAddress, clientPort);
											socket.send(resendpkt);
											if (buffer.containsKey(seq)) timer.put(seq, System.nanoTime());
										}
									}
								}
							}
						}
						}
					}
				}
			} catch (Exception e) {
				// TODO Auto-generated catch block
				System.out.println("TIMER tread Exception: " + e.getClass() + " " + e.getMessage() + e.getLocalizedMessage() + e.getCause());
			}
			System.out.println("Timer thread run is over");
		}
	}

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
					System.out.println("-----------------Inside thread loop, baseIndex is"+baseIndex+"--------------------");
					socket.receive(rcvedpkt);
					short seqNumReceived = convertToShort(Arrays.copyOfRange(rcvedBuffer, 0, 2));
					String response = new String(Arrays.copyOfRange(rcvedBuffer, 2, 5));
					System.out.println("-----------------RECEIVE PACKET "+seqNumReceived+" "+response+"--------------------");
					if (response.equals("ACK")) {
						System.out.println("-----------------ACK Received--------------------");
						if (!receivedACKSeqNo.contains(seqNumReceived)) {
							receivedACKSeqNo.add(new Short(seqNumReceived));
							while (receivedACKSeqNo.contains(baseIndex)) {
//								receivedACKSeqNo.remove((Short)baseIndex);
								FileSender.timer.remove((Short)baseIndex);
								FileSender.buffer.remove((Short)baseIndex);
								baseIndex++;
							} 	
						} else {
							if (!FileSender.buffer.isEmpty()) {
								System.out.println("-----------------Resending--------------------");
								System.out.println("-----------------Now Buffer size is "+FileSender.buffer.size()+"--------------------");
								byte[] resendPackage = FileSender.buffer.values().iterator().next();
								DatagramPacket resendpkt = new DatagramPacket(resendPackage, resendPackage.length, clientAddress, clientPort);
								socket.send(resendpkt);
							}
						}
					} else if (response.equals("NAK")) {
						// resend the packet
						System.out.println("-----------------NAK Received--------------------");
						if (!FileSender.buffer.isEmpty()) {
							System.out.println("-----------------Resending--------------------");
							System.out.println("-----------------Now Buffer size is "+FileSender.buffer.size()+"--------------------");
							byte[] resendPackage = FileSender.buffer.values().iterator().next();
							DatagramPacket resendpkt = new DatagramPacket(resendPackage, resendPackage.length, clientAddress, clientPort);
							socket.send(resendpkt);
							if (buffer.containsKey(baseIndex)) FileSender.timer.put((Short)baseIndex, System.nanoTime());
						}
					} else if (response.equals("FIN")) {
						System.out.println("-----------------FIN Received--------------------");
						isEnd = true;
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
		int numberOfPackages = (int) (Math.ceil(sizeOfFile/996.0)+1);
		int flag = 0;

		// packet used to receive response
		ACKThread ackThread = new ACKThread();
		
		// time-out thread
		 TimerThread timerThread = new TimerThread();
		 timerThread.start();

		while (ackThread.isAlive()) {
			byte[] sendData = new byte[996];
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
			} else if (seqNum >= baseIndex && seqNum < (baseIndex + WINDOW_SIZE) && seqNum < numberOfPackages) {
				System.out.println("-------------------------seq number inside window------ ");
				if ((bis.read(sendData)) >= 0) {
					packet = new byte[1000];
					packet[0] = convertToBytes(seqNum)[0];
					packet[1] = convertToBytes(seqNum)[1];
					for (int i = 0; i < sendData.length; i++) {
						packet[i + 4] = sendData[i];
					}
					packet[2] = convertToBytes((short) checkSum(Arrays.copyOfRange(packet, 4, 1000)))[0];
					packet[3] = convertToBytes((short) checkSum(Arrays.copyOfRange(packet, 4, 1000)))[1];
				}
			} else {
				continue;
			}
			System.out.println("-------------------------Sending packet number------ "+seqNum);
			buffer.put(seqNum, packet);
			timer.put(seqNum, System.nanoTime());
			DatagramPacket pkt = new DatagramPacket(packet, packet.length, clientAddress, clientPort);
			socket.send(pkt);
			seqNum++;
			//Thread.sleep(1000);
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
		short[] ans = new short[1];;
		ByteBuffer.wrap(byteArray).order(ByteOrder.BIG_ENDIAN).asShortBuffer().get(ans);
		return ans[0];
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
