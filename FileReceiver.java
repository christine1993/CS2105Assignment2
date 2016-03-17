
// <Wang Hanyu A0105664H>

import java.net.*;
import java.io.*;
import java.util.*;
import java.util.zip.CRC32;

class FileReceiver {

	public DatagramSocket socket;
	public DatagramPacket rcvedpkt;
	public final int WINDOW_SIZE = 10;
	public short nextSeqNo = 0; // the expected packet
	public short seqNum;
	public static ArrayList<Short> receivedACKSeqNo = new ArrayList<Short>();
	public static ArrayList<Short> unACKedSeqNo = new ArrayList<Short>();
	public static HashMap<Short, byte[]> buffer = new HashMap<Short, byte[]>();

	public static void main(String[] args) {
		// check if the number of command line argument is 1
		if (args.length != 1) {
			System.out.println("Usage: java FileReceiver port");
			System.exit(1);
		}
		try {
			new FileReceiver(args[0]);
		} catch (Exception ex) {
			System.out.println("Exception during " + ex.getClass().toString());
		}

	}

	public FileReceiver(String localPort) throws Exception {
		InetAddress serverAddress = InetAddress.getByName("localhost");
		int portNumber = Integer.parseInt(localPort);
		System.out.println("local port is " + portNumber);
		String fileName = new String();

		socket = new DatagramSocket(portNumber);

		int flag = 0;
		byte[] rcvBuffer = new byte[1000];
		rcvedpkt = new DatagramPacket(rcvBuffer, rcvBuffer.length);

		socket.receive(rcvedpkt);
		fileName = new String(Arrays.copyOfRange(rcvBuffer, 4, 1000));
		FileOutputStream fos = new FileOutputStream(fileName.trim());
		BufferedOutputStream bos = new BufferedOutputStream(fos);

		String str = new String(rcvedpkt.getData(), 0, rcvedpkt.getLength());
		while (!str.trim().equals("END")) {
			// construct and send response
			byte[] responseByte = new byte[8];
			short checkSumCompute = (short) checkSum(Arrays.copyOfRange(rcvBuffer, 4, 1000));
			responseByte[0] = rcvBuffer[0];
			responseByte[1] = rcvBuffer[1];
			// to check whether the received packet is in order (not timed out
			// and retransmitted)
			seqNum = convertToShort(Arrays.copyOfRange(rcvBuffer, 0, 2));
			receivedACKSeqNo.add(seqNum);

			String response;
			if (rcvBuffer[2] == this.convertToBytes(checkSumCompute)[0]
					&& rcvBuffer[3] == this.convertToBytes(checkSumCompute)[1])
				response = "ACK";
			else
				response = "NAK";

			for (int i = 0; i < response.getBytes().length; i++)
				responseByte[i + 2] = response.getBytes()[i];

			this.sendResponse(responseByte, serverAddress, portNumber); // send
																		// response

			// unpack data
			if (flag == 0) {
				System.out.println("File Name is " + fileName);
				flag++;
			} else {
				if (seqNum == nextSeqNo) {
					bos.write(Arrays.copyOfRange(rcvBuffer, 4, 1000), 0, rcvedpkt.getLength());
					nextSeqNo++;
					// if before receiving this packet, some packets are
					// buffered
					if (unACKedSeqNo.contains(seqNum)) {
						while (buffer.containsKey(nextSeqNo)) {
							bos.write(buffer.get(nextSeqNo), 0, buffer.get(nextSeqNo).length);
							buffer.remove(nextSeqNo);
							nextSeqNo++;
						}
						unACKedSeqNo.remove(seqNum);
					}
				} else {
					if (!unACKedSeqNo.contains(nextSeqNo))
						unACKedSeqNo.add(nextSeqNo);
					buffer.put(seqNum, Arrays.copyOfRange(rcvBuffer, 4, 1000));
				}
				socket.receive(rcvedpkt);
				str = new String(Arrays.copyOfRange(rcvBuffer, 4, 1000), 0, rcvedpkt.getLength());

			}
		}

		bos.flush();
		bos.close();
		socket.close();
		System.out.println("File transmission completed!");
		System.exit(1);

	}

	public void sendResponse(byte[] response, InetAddress IPAddress, int portNubmer)
			throws IOException, InterruptedException {
		DatagramPacket pkt = new DatagramPacket(response, response.length, IPAddress, portNubmer);
		socket.send(pkt);
		Thread.sleep(1);

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