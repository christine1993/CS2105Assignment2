
// <Wang Hanyu A0105664H>

import java.net.*;
import java.io.*;
import java.util.*;
import java.util.zip.CRC32;
import java.nio.*;

class FileReceiver {

	public DatagramSocket socket;
	public DatagramPacket rcvedpkt;
	public final int WINDOW_SIZE = 10;
	public short nextSeqNo = 0; // the expected packet
	public short seqNum;
	public static ArrayList<Short> receivedSeqNo = new ArrayList<Short>();
	public static HashMap<Short, byte[]> buffer = new HashMap<Short, byte[]>();

	public static void main(String[] args) {
		// check if the number of command line argument is 1
		if (args.length != 1) {
			System.out.println("Usage: java FileReceiver port");
			System.exit(1);
		}
		try {
			FileReceiver fr=new FileReceiver();
			fr.run(args[0]);
		} catch (Exception ex) {
			System.out.println("Exception during " + ex.getClass().toString());
		}

	}

	public void run(String localPort) throws Exception {
		InetAddress serverAddress = InetAddress.getByName("localhost");
		int portNumber = Integer.parseInt(localPort);
		System.out.println("local port is " + portNumber);
		String fileName = new String("dummy");
		long fileSize = -1;
		byte[] rcvBuffer = new byte[1000];
		int count=0;
		int destPortNumber=0;
		rcvedpkt = new DatagramPacket(rcvBuffer, rcvBuffer.length);
		socket = new DatagramSocket(portNumber);

		FileOutputStream fos = new FileOutputStream(fileName.trim());
		BufferedOutputStream bos = new BufferedOutputStream(fos);
		int lastPackageLength = 0;
		int numberOfPackage=Integer.MAX_VALUE;

		while (count!=numberOfPackage) { //// end condition must secure all packages arrived
			socket.receive(rcvedpkt);
			serverAddress=rcvedpkt.getAddress();
			destPortNumber=rcvedpkt.getPort();
			// construct and send response
			byte[] responseByte = new byte[8];
			responseByte[0] = rcvBuffer[0];
			responseByte[1] = rcvBuffer[1];
			short checkSumCompute = (short) checkSum(Arrays.copyOfRange(rcvBuffer, 4, 1000));
			
			// to check whether the received packet is in order (not timed out
			// and retransmitted)
			seqNum = convertToShort(Arrays.copyOfRange(rcvBuffer, 0, 2));
			System.out.println("Received Sequence Number is : "+seqNum);
		    System.out.println("Next SeqNum expected is : "+nextSeqNo);
		    
			String response;
			
			byte[] rcvCheckSum = {rcvBuffer[2], rcvBuffer[3]};
			System.out.println("CHECKSUM RECEIVED: "+this.convertToShort(rcvCheckSum));
			System.out.println("CHECKSUM COMPUTED: "+checkSumCompute);
			
			if (rcvBuffer[2] == this.convertToBytes(checkSumCompute)[0]
					&& rcvBuffer[3] == this.convertToBytes(checkSumCompute)[1])
				response = "ACK";
			else
				response = "NAK";
			System.out.println("-----------------RECEIVE PACKET "+seqNum+" "+response+"--------------------");
			int l = response.getBytes().length;
			for (int i = 0; i < l; i++)
				responseByte[i + 2] = response.getBytes()[i];
			
			// unpack data
			if (seqNum == 0 && nextSeqNo == 0&&response.equals("ACK")) {
				fileName = new String(Arrays.copyOfRange(rcvBuffer, 4, 500));
				fileSize = convertToLong(Arrays.copyOfRange(rcvBuffer, 500, 508));
				fos = new FileOutputStream(fileName.trim());
				bos = new BufferedOutputStream(fos);
				numberOfPackage = (int) (Math.ceil(fileSize/996.0)+1);
				lastPackageLength = (int) (fileSize % 996 + 4);

				System.out.println("File Name is " + fileName);
				count++;
				nextSeqNo++;
				while (buffer.containsKey(nextSeqNo)) {
					bos.write(buffer.get(nextSeqNo), 0, buffer.get(nextSeqNo).length);
					buffer.remove(nextSeqNo);
					count++;
					nextSeqNo++;
				}
			} else if(response.equals("ACK")){
				if (seqNum == nextSeqNo) {
					System.out.println("-----------------Write PACKET "+seqNum+" --------------------");
					if (seqNum == numberOfPackage-1) bos.write(Arrays.copyOfRange(rcvBuffer, 4, lastPackageLength), 0, lastPackageLength-4);
					else bos.write(Arrays.copyOfRange(rcvBuffer, 4, 1000), 0, rcvedpkt.getLength()-4);
					count++;
					nextSeqNo++;
					// if before receiving this packet, some packets are
					// buffered
					while (buffer.containsKey(nextSeqNo)) {
						bos.write(buffer.get(nextSeqNo), 0, buffer.get(nextSeqNo).length);
						buffer.remove(nextSeqNo);
						count++;
						nextSeqNo++;
					}
				} else if (seqNum < nextSeqNo) {
				} else { // seqNum > nextSeqNum, save to buffer
					if (seqNum == numberOfPackage-1) buffer.put(seqNum, Arrays.copyOfRange(rcvBuffer, 4, lastPackageLength));
					else buffer.put(seqNum, Arrays.copyOfRange(rcvBuffer, 4, rcvedpkt.getLength()));
				}
			}
			// send response
			responseByte[5] = convertToBytes((short) checkSum(Arrays.copyOfRange(responseByte, 0, 5)))[0];
			responseByte[6] = convertToBytes((short) checkSum(Arrays.copyOfRange(responseByte, 0, 5)))[1];
			this.sendResponse(responseByte, serverAddress, destPortNumber);
			//Thread.sleep(1500)
		}
        //send FIN
		byte[] responseByte = new byte[8];
		responseByte[0] = 0;
		responseByte[1] = 0;
		for (int i = 0; i < "FIN".getBytes().length; i++)
			responseByte[i + 2] = "FIN".getBytes()[i];
		this.sendResponse(responseByte, serverAddress, destPortNumber);
		
		
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
		System.out.println("-----------------Sending Respond "+response+"--------------------");
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
	
	public  long convertToLong(byte[] b) {
	    long result = 0;
	    for (int i = 0; i < 8; i++) {
	        result <<= 8;
	        result |= (b[i] & 0xFF);
	    }
	    return result;
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
