package mafia;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

public class ClientReceive implements Runnable {
	private Socket sock;

	ClientReceive(Socket sock) {
		this.sock = sock;
	}

	@Override
	public void run() {
		try (BufferedReader br = new BufferedReader(new InputStreamReader(sock.getInputStream()));) {
			String str;

			while (true) {
				str = br.readLine();
				System.out.println(str);
			}

		} catch (IOException ex) {
			ex.printStackTrace();
		}

	}

}