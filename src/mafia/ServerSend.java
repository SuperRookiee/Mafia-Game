package mafia;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.BrokenBarrierException;

public class ServerSend implements Runnable {
	private Socket clientSocket;
	private int playerId;
	public char role;
	public String msg;
	public char status; // w:wait, s:send

	ServerSend(Socket clientSocket, int id, char r) {
		this.playerId = id;
		this.clientSocket = clientSocket;
		this.role = r;
		this.msg = "";
		this.status = 'w';
	}

	@Override
	public void run() {
		System.out.println("Player " + this.playerId + " 입장했습니다. ");
		try {
			Server.barrier.await();
		} catch (InterruptedException | BrokenBarrierException e) {
			e.printStackTrace();
		}
		try (PrintWriter out = new PrintWriter(this.clientSocket.getOutputStream(), true);) {
			out.print("Player " + playerId + " 당신의 직업은 ");
			if (this.role == 'm')
				out.println("마피아입니다.");
			else if (this.role == 'd')
				out.println("의사입니다.");
			else if (this.role == 'p')
				out.println("경찰입니다.");
			else
				out.println("시민입니다.");

			while (true) {
				if (status == 's') {
					out.println(this.msg);
					this.status = 'w';
				} else
					System.out.print("");
			}
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}
	

}
