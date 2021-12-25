package mafia;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.concurrent.BrokenBarrierException;

public class ServerReceive implements Runnable {
	private Socket clientSocket;
	private int playerId;
	public String receivedMsg;
	public boolean received;

	ServerReceive(Socket clientSocket, int id) {
		this.playerId = id;
		this.clientSocket = clientSocket;
		this.receivedMsg = "";
		this.received = false;
	}

	@Override
	public void run() {
		try {
			Server.barrier.await();
		} catch (InterruptedException | BrokenBarrierException e) {
			e.printStackTrace();
		}
		try (BufferedReader br = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));) {
			while (true) {
				this.receivedMsg = br.readLine();
				
				System.out.println("[Player" + playerId + "]: " + this.receivedMsg);
				this.received = true;
			}
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}
}
