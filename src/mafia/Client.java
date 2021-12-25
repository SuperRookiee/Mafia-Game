package mafia;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;

public class Client {
	public static void main(String[] args) throws SocketException {
		System.out.println("기다려주세요 접속 중입니다...");
		try {
			InetAddress localAddress = InetAddress.getLocalHost();
			
//			localAddress
//			localhost
//s			"192.168.0.33"
//h			"192.168.0.19"
			try (Socket cSocket = new Socket("localhost", 10000);) { 

				ClientSend cs = new ClientSend(cSocket);
				ClientReceive cr = new ClientReceive(cSocket);
				Thread ts = new Thread(cs);
				Thread tr = new Thread(cr);

				ts.start();
				tr.start();
				
				
				while(true) {
				}
			}
		} catch (IOException ex) {
		}
		
	}
}
