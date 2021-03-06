package ser321.media.download;

import java.net.*;
import java.io.*;
import java.util.*;

public class DownloadClient extends Thread {

	private static final byte[] fileToClientBytes = "fileToClient^".getBytes();
	private String serverHost;
	private int aPort;
	private String filename = "potato.jpeg";
	private int byteCount;
	private int type;

	private static final boolean debugOn = true;

	public DownloadClient(String serverHost, int portToUse, String fPath, int type) {
		this.serverHost = serverHost;
		this.aPort = portToUse;
		this.filename = fPath;
		this.byteCount = 0;
		this.type = type;
	}

	public boolean downloadMedia() {
		byte[] okBytes = "OK".getBytes();
		Socket sock = null;
		InputStream inStream = null;
		OutputStream outStream = null;
		FileOutputStream fos = null;
		try {
			System.out.println("Connecting to MediaServer: "+serverHost+
			":"+aPort+" to receive Media.");
			sock = new Socket(serverHost, aPort);
			inStream = sock.getInputStream();
			outStream = sock.getOutputStream();

			// send GroupServer the string: fileToClient^
			debug("sending "+fileToClientBytes.length+" bytes in the string: "+
			(new String(fileToClientBytes,0,fileToClientBytes.length)));
			outStream.write(fileToClientBytes,0,fileToClientBytes.length);
			outStream.flush();

			// read the number of bytes in buffer followed by total file bytes
			byte[] bufCount = new byte[32];
			byte[] okCount = "OK".getBytes();
			int byteCount = 1;
			int soFar = 0;
			byte[] buf = new byte[1048576];
			String packet;
			
			//Get OK from server regarding connection
			debug("Waiting for Connect OK.");
			int okMess = inStream.read(okCount,0,okCount.length);
			String okString = "";
			if (okMess != -1) {
				okString = new String(okCount,0,okMess);
			}
			if(okString.equalsIgnoreCase("OK")) {
				debug("Got Connection OK.");
				
				//Send filename to server
				byte[] filenameBytes = filename.getBytes();
				debug("sending "+filenameBytes.length+" bytes in the string: "+
				(new String(filenameBytes,0,filenameBytes.length)));
				outStream.write(filenameBytes,0,filenameBytes.length);
				outStream.flush();
				
				//Get OK from server regarding file
				debug("Waiting for File OK.");
				okMess = inStream.read(okCount,0,okCount.length);
				okString = "";
				if (okMess != -1) {
					okString = new String(okCount,0,okMess);
				}
				if(!okString.equalsIgnoreCase("OK")) {
					return false;
				}
				debug("Got File OK.");
				// create file output stream for writing the serialized group
				if(type == 0)
					fos = new FileOutputStream("DataClient/Song.mp3");
				else
					fos = new FileOutputStream("DataClient/Video.mp4");
				int packetLength = 0;
				int digitCount = inStream.read(bufCount,0,bufCount.length);

				if(digitCount>0){
					packet = new String(bufCount, 0, digitCount);
					debug("read byte counts from server: "+packet);
					StringTokenizer st = new StringTokenizer(packet, "^");
					packetLength = Integer.parseInt(st.nextToken());
					byteCount = Integer.parseInt(st.nextToken());
					System.out.println("Beginning " + byteCount + " byte download of " + filename + ".");
					outStream.write(okBytes,0,okBytes.length);
					debug("wrote OK, next looking for "+packetLength+" bytes");
					while(soFar < byteCount) {
						// read the buffer of file bytes and write to the local file.
						int thisRead = inStream.read(buf,0,packetLength);
						int expect = packetLength;
						if (byteCount - soFar < packetLength)
							expect = byteCount - soFar;
						if (thisRead != expect) {
							debug("unexpected read buffer got "+thisRead+
							" expected "+expect+" bytes.");
							int gotBytes = 0;
							gotBytes = gotBytes + thisRead;
							while (gotBytes < expect) {
								soFar = soFar + thisRead;
								fos.write(buf,0,thisRead);
								thisRead = inStream.read(buf,0,expect);
								gotBytes = gotBytes + thisRead;
								debug("Received " + gotBytes + " of expected " + expect + " bytes.");
							}
						}
						soFar = soFar + thisRead;
						if (byteCount - soFar < packetLength)
							expect = byteCount - soFar;
						fos.write(buf,0,thisRead);
						okBytes = "OK".getBytes();
						outStream.write(okBytes,0,okBytes.length);
						if(soFar != byteCount) {
							debug("wrote OK, looking for next "+expect+" bytes");
						} else {
							debug("wrote OK, finalizing download.");
						}
						outStream.flush();
					}
				}
			}
			fos.close();
			inStream.close();
			sock.close();
			System.out.println("Finished downloading " + filename + " with " + soFar + " bytes.");
		} catch (Exception e) {
			e.printStackTrace();
		}
		return true;
	}

	private void debug(String message) {
		if (debugOn)
		System.out.println("debug: "+message);
	}
}
