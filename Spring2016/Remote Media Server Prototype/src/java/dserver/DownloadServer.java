package ser321.media.download;

import java.net.*;
import java.io.*;
import java.util.*;

/**
* Copyright (c) 2015 Tim Lindquist,
* Software Engineering,
* Arizona State University at the Polytechnic campus
* <p/>
* This program is free software; you can redistribute it and/or
* modify it under the terms of the GNU General Public License
* as published by the Free Software Foundation version 2
* of the License.
* <p/>
* This program is distributed in the hope that it will be useful,
* but without any warranty or fitness for a particular purpose.
* <p/>
* Please review the GNU General Public License at:
* http://www.gnu.org/licenses/gpl-2.0.html
* see also: https://www.gnu.org/licenses/gpl-faq.html
* so you are aware of the terms and your rights with regard to this software.
* Or, write to the Free Software
* Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301,USA
* <p/>
* Purpose:
* A threaded server providing download service for the Serialized Group
*
* <p/>
* Ser321 Principles of Distributed Software Systems
* @see <a href="http://pooh.poly.asu.edu/Ser321">Ser321 Home Page</a>
* @author Tim Lindquist (Tim.Lindquist@asu.edu) CIDSE - Software Engineering
*                       Ira Fulton Schools of Engineering, ASU Polytechnic
* @version October, 2015
* @license See above
*/
public class DownloadServer extends Thread {
	private static final boolean debugOn = true;
	private Socket conn;
	private int id;
	private int byteCount;

	public DownloadServer(Socket aSock, int connId) {
		this.conn = aSock;
		this.id = connId;
		this.byteCount = 0;
	}

	public void run (){
		try {
			OutputStream outStream = conn.getOutputStream();
			InputStream inStream = conn.getInputStream();
			String filename = "potato.jpeg";
			byte clientInput[] = new byte[4096]; 
			byte opnComplete[] = "complete".getBytes(); 
			int numr = inStream.read(clientInput,0,4096);
			long byteCount=0;
			if (numr != -1) {
				String clientString = new String(clientInput,0,numr);
				this.debug("Read from client number "+Integer.toString(id)+", "+
				Integer.toString(numr)+
				" bytes as the string: "+clientString+"\n");
				if(clientString.equalsIgnoreCase("filetoclient^")){
					System.out.println("Request to download media file");
					this.debug("Sending OK.");
					byte[] okBytes = "OK".getBytes();
					outStream.write(okBytes,0,okBytes.length);
					outStream.flush();
					this.debug("Waiting for filename.");
					byte fileInput[] = new byte[4096];  
					int filenum = inStream.read(fileInput,0,4096);
					if (filenum != -1) {
						filename = new String(fileInput,0,filenum);
						this.debug("Read from client number "+Integer.toString(id)+", "+
						Integer.toString(filenum)+
						" bytes as the string: "+filename+"\n");
						this.debug("Checking for existance.");
					}
					if(new File("DataServer/" + filename).exists()) {
						this.debug("Sending OK.");
						okBytes = "OK".getBytes();
						outStream.write(okBytes,0,okBytes.length);
						outStream.flush();
						byteCount = this.downloadToClient("DataServer/" + filename);
						System.out.println("Download complete. Transferred "+
						byteCount+" bytes.");
					} else {
						System.out.println("File " + filename + " does not exist in server.");
						byte[] noBytes = "NO".getBytes();
						outStream.write(noBytes,0,noBytes.length);
						outStream.flush();
						conn.close();
					}
				}
			}
		} catch (Exception e){
			e.printStackTrace();
		}
	}

	private long downloadToClient(String filename){
		long byteCount=0;
		try {
			// get the connections streams
			OutputStream outStream = conn.getOutputStream();
			InputStream inStream = conn.getInputStream();

			// open the input file containing serialized group
			File inFile = new File(filename);
			long len = inFile.length();
			FileInputStream fis = new FileInputStream(inFile);

			// send the file to the client. First send the number of bytes
			// to be transferred.
			// start by reading the serialized group into buf to find how
			// many bytes
			byte[] buf = new byte[1048576];
			int n = fis.read(buf);
			debug("read "+n+" bytes from the file");

			// send two integer byte counts: bufLengthThisRead^fileLength
			String thisBuf = String.valueOf(n);
			String numStr = thisBuf + "^" + String.valueOf(len) + "^";
			debug("sending number of data bytes to client "+numStr);
			Thread.sleep(20);
			outStream.write(numStr.getBytes(),0,numStr.getBytes().length);
			outStream.flush();

			// wait to be sure the client got it, by waiting for an OK.
			byte clientOK[] = new byte[4];
			Thread.sleep(20);
			int okTot = 0;
			int okNum = inStream.read(clientOK,0,4);
			debug("tried to read ok. Got "+ okNum + " bytes");
			okTot = okTot + ((okNum<0)?0:okNum);
			String okStr = new String(clientOK,0,okNum);
			if(okStr.contains("OK")){
				while(n > 0) {
					// if client got byte counts and replied OK then send buf
					outStream.write(buf,0,n);
					outStream.flush();
					debug("I sent bytes:" + Integer.toString(n));
					byteCount = byteCount + n;
					// now, make sure the client got the buffer and replied OK
					byte gotIT[] = new byte[7];
					Thread.sleep(20);
					int gotItTot = 0;
					int gotNum = inStream.read(gotIT,0,7);
					gotItTot = gotItTot + ((gotNum<0)?0:gotNum);
					String gotStr = new String(clientOK,0,okNum);
					if(gotStr.contains("OK")) {
						debug("got OK");
					} else {
						debug("breaking because did not get OK. Got "+gotStr);
					}
					n = fis.read(buf);
				}
			}
			debug("sending Done to client");
			outStream.write("Done".getBytes(),0,"Done".getBytes().length);
			outStream.flush();
			fis.close();
			outStream.flush();
			outStream.close();
			conn.close();
		} catch (Exception e){
			System.out.println("exception uploading to server: "+e.getMessage());
			e.printStackTrace();
		}
		return byteCount;
	}

	private static void debug(String message) {
		if (debugOn) {
			System.out.println("debug: "+message);
		}
	}

	/**
	* main method provides an infinte loop to accept connections from clients.
	* when a client connects, a new download thread is created to
	* read the file and send it to the client.
	*/
	public static void main (String args[]) {
		ServerSocket serv;
		int connects = 0;
		Socket sock;
		int id=0;
		int portNo = 3030;
		try {
			if (args.length < 1) {
				System.out.println(
				"Usage: java -cp classes DownloadServer"+
				" serverPortNum \n"+
				"java -cp classes DownloadServer 3030");
				System.exit(0);
			} else {
				portNo = Integer.parseInt(args[0]);
			}
			if (portNo <= 1024) {
				portNo=3030;
			}
			serv = new ServerSocket(portNo);
			while (true) {
				System.out.println("DownloadServer waiting for client connect "+
				connects + " on port "+Integer.toString(portNo)+"\n");
				sock = serv.accept();
				connects = connects +1;
				System.out.println("DownloadServer connected to client: "+connects);
				DownloadServer aServer = new DownloadServer(sock, connects++);
				aServer.start();
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
}
