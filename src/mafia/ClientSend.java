package mafia;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientSend implements Runnable {
	private Socket sock;

	ClientSend(Socket sock) {
		this.sock = sock;
	}

	@Override
	public void run() {
		try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
				PrintWriter out = new PrintWriter(sock.getOutputStream(), true);) {

			System.out.println("접속!"); 
			String str;
			
			while (true) {
				str = br.readLine();
				out.println(str); // to server
			}
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

}
