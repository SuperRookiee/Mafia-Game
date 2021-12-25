package mafia;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
   private static Socket clientSocket;
   private static int playerNum; // 플레이어 수
   private static int mafia1Id, mafia2Id; // 마피아인 플레이어들의 id
   private static int doctorId;
   private static int policeId;
   private static int aliveNum; // 살아있는 플레이어의 수
   private static int mafiaNum; // 살아있는 마피아의 수
   static ArrayList<ServerSend> playerSend = new ArrayList<ServerSend>(); // 메시지 송신용 객체 모음
   static ArrayList<ServerReceive> playerReceive = new ArrayList<ServerReceive>(); // 메시지 수신용 객체 모음
   static HashMap<Integer, Boolean> alives = new HashMap<Integer, Boolean>(); // 각 플레이어가 살아있는지 확인용
   static CyclicBarrier barrier;
   static Scanner scv = new Scanner(System.in);

   public static void waiting_room(Connection conn) throws SQLException { // 플레이어 모집 & 역할 분배
      String sql = null;
      PreparedStatement pstmt = null;
      System.out.println("*** NEW GAME ***");
      System.out.print("게임에 참여할 인원 수를 입력하세요 (5~7): ");
      playerNum = scv.nextInt();
      System.out.print("Client를 " + playerNum + "번만큼 실행해주세요\n");

      for (int i = 0; i < playerNum; i++) {
         sql = "INSERT INTO Players(userName) values(?)";
         pstmt = conn.prepareStatement(sql);
         pstmt.setString(1, "Player" + i);
         pstmt.executeUpdate();
      }

      mafia2Id = -100;
      aliveNum = playerNum; // 살아있는 플레이어 수 초기화

      mafiaNum = (playerNum < 7) ? 1 : 2; // 마피아 수 결정 (7인 이상일 경우 마피아 2명)
      // 랜덤하게 역할 결정 (나머지는 시민)
      mafia1Id = (int) (Math.random() * (playerNum - 1)) + 1; // 범위: 0~playerNum-1
      doctorId = (int) (Math.random() * (playerNum - 1)) + 1; // 범위: 0~playerNum-1
      policeId = (int) (Math.random() * (playerNum - 1)) + 1;
      while (doctorId == mafia1Id) {
         doctorId = (int) (Math.random() * (playerNum - 1)) + 1;
      }
      while (policeId == mafia1Id || policeId == doctorId) {
         policeId = (int) (Math.random() * (playerNum - 1)) + 1;
      }

      if (mafiaNum == 2) { // 범위: 0~playerNum-1
         mafia2Id = (int) (Math.random() * (playerNum - 1)) + 1;
         while (policeId == mafia2Id || mafia1Id == mafia2Id || doctorId == mafia2Id) {
            mafia2Id = (int) (Math.random() * (playerNum - 1)) + 1;
         }

      }

      ExecutorService eService = Executors.newFixedThreadPool(playerNum * 2); // 각 플레이어마다 쓰레드 2개씩
      barrier = new CyclicBarrier(playerNum * 2, () -> System.out.println("*** GAME START ***"));
      try (ServerSocket sSocket = new ServerSocket(10000)) {
         for (int j = 0; j < playerNum; j++) { // 입력한 플레이어 수만큼 클라이언트 대기
            clientSocket = sSocket.accept();
            char role;
            if (j == mafia1Id || j == mafia2Id) { // 마피아
               sql = String.format("UPDATE Players SET Job = '마피아'  WHERE playerID = %d ", j + 1);
               pstmt = conn.prepareStatement(sql);
               pstmt.executeUpdate();
               role = 'm';
            } else if (j == doctorId) {// 의사
               sql = String.format("UPDATE Players SET Job = '의사'  WHERE playerID = %d ", j + 1);
               pstmt = conn.prepareStatement(sql);
               pstmt.executeUpdate();
               role = 'd';
            } else if (j == policeId) {// 경찰
               sql = String.format("UPDATE Players SET Job = '경찰'  WHERE playerID = %d ", j + 1);
               pstmt = conn.prepareStatement(sql);
               pstmt.executeUpdate();
               role = 'p';
            } else {
               sql = String.format("UPDATE Players SET Job = '시민'  WHERE playerID = %d ", j + 1);
               pstmt = conn.prepareStatement(sql);
               pstmt.executeUpdate();
               role = 'c';
            }

            alives.put(j, true);
            // 개별스레드 생성. 수락 했을 때의 client 주소와 id를 담는다.
            ServerSend ss = new ServerSend(clientSocket, j, role);
            ServerReceive sr = new ServerReceive(clientSocket, j);

            playerSend.add(ss);
            playerReceive.add(sr);
            eService.submit(ss);
            eService.submit(sr);
         }
      } catch (IOException e) {
         e.printStackTrace();
      }
   }

   public static void chatting(char who) { // a:all, m:mafia끼리만
      String msg;
      boolean running = true;
      if (who == 'a') {
         while (running) {
            for (int i = 0; i < playerNum; i++) {
               if (alives.get(i) == true && playerReceive.get(i).received == true) { // 메시지를 입력했다면
                  msg = "[player " + Integer.toString(i) + "]: " + playerReceive.get(i).receivedMsg; // 메시지를 가져오고
                  playerReceive.get(i).received = false; // '메시지 받음' 상태를 false로 바꾸고
                  send_message('a', msg);// 모두에게 전달
                  if (playerReceive.get(i).receivedMsg.equalsIgnoreCase("quit")) {
                     running = false;
                     break;
                  }
               }
            }
         }
      } else if (who == 'm') { // 마피아 두명끼리 대화. 죽은 플레이어는 지켜볼 수 있음
         while (running) {
            for (int i = 0; i < playerNum; i++) {
               if (playerSend.get(i).role == 'm' && alives.get(i) == true
                     && playerReceive.get(i).received == true) { // 마피아가 메시지를 입력했다면
                  msg = "[player " + Integer.toString(i) + "]: " + playerReceive.get(i).receivedMsg; // 메시지를 가져오고
                  playerReceive.get(i).received = false; // '메시지 받음' 상태를 false로 바꾸고
                  send_message('m', msg);// 마피아에게 전달
                  if (playerReceive.get(i).receivedMsg.equalsIgnoreCase("quit")) {
                     running = false;
                     break;
                  }
               }
            }
         }
      }

   }

   public static void voting(Connection conn, int cnt) throws SQLException {
      String sql = null;
      PreparedStatement pstmt = null;

      send_message('a', "\n\n[사회자]: 마피아라고 의심되는 플레이어 번호를 입력해주세요 (10초)");
      try {
         Thread.sleep(10000);
      } catch (InterruptedException e) {
         e.printStackTrace();
      }
      int v, maxidx = 0, max = 0;
      int[] intArr = { 0, 0, 0, 0, 0, 0, 0 };
      for (int i = 0; i < playerNum; i++) {
         if (alives.get(i) == true && playerReceive.get(i).received == true) {
            v = Integer.parseInt(playerReceive.get(i).receivedMsg);
            playerReceive.get(i).received = false;
            intArr[v]++;
         }
      }

      sql = String.format("alter table Players ADD COLUMN voteRecord%d INT NOT NULL", cnt);
      pstmt = conn.prepareStatement(sql);
      pstmt.executeUpdate();
      /*
       * 0 0 0 1 0
       */
      for (int i = 0; i < playerNum; i++) {
         System.out.println(intArr[i]); // test
         sql = String.format("UPDATE Players SET voteRecord%d = %d  WHERE playerID = %d ", cnt, intArr[i], i + 1);
         pstmt = conn.prepareStatement(sql);
         pstmt.executeUpdate();

         if (intArr[i] > max) {
            max = intArr[i];
            maxidx = i;
         }
      }

      cnt++;

      String msg = null;

      if (maxidx != 0) {
         if (playerSend.get(maxidx).role == 'm') {
            msg = "\n\n[사회자]: Player" + Integer.toString(maxidx) + " 는 마피아입니다.";
            mafiaNum--; // 마피아였다면, 살아있는 마피아 수를 줄임
         } else {
            msg = "\n\n[사회자]: Player" + Integer.toString(maxidx) + " 는 마피아가 아닙니다.";

         }
         aliveNum--; // 살아있는 플레이어 수를 줄이고
         alives.put(maxidx, false); // 죽은 상태로 만듦
      } else
         msg = "\n\n[사회자]: 아무도 죽지 않았습니다.";

      msg += "현재 남아있는 플레이어 수: " + Integer.toString(aliveNum) + ", \n현재 남아있는 마피아 수: " + Integer.toString(mafiaNum);
      send_message('a', msg);

      try { // 메시지를 보내고(send_message) 일정 시간 쉬어야 모두에게 온전히 전달됨
         Thread.sleep(3000);
      } catch (InterruptedException e) {
         e.printStackTrace();
      }
   }

   public static void send_message(char who, String msg) { // 사회자가 명시된 플레이어에게 메시지를 보냄
      // 메시지를 받고 나면 ServerSend에서 status를 알아서 w로 바꾼다.
      if (who == 'a') { // 살아있는 모두에게
         for (int i = 0; i < playerNum; i++) {
            playerSend.get(i).msg = msg;
            playerSend.get(i).status = 's';
         }
      } else if (who == 'm') { // 마피아와 죽은 사람들만 받는 메시지
         for (int i = 0; i < playerNum; i++) {
            if (alives.get(i) == false && playerSend.get(i).role != 'm') {
               playerSend.get(i).msg = msg;
               playerSend.get(i).status = 's';
            }
         }
         playerSend.get(mafia1Id).msg = msg;
         playerSend.get(mafia1Id).status = 's';
         playerSend.get(mafia2Id).msg = msg;
         playerSend.get(mafia2Id).status = 's';
         // 마피아가 2명일 때만 chatting 함수에서 send_message('m',..)을 호출하므로 인덱스 문제는 일어나지 않음
      } else if (who == 'p') { // 경찰
         for (int i = 0; i < playerNum; i++) {
            if (alives.get(i) == false && playerSend.get(i).role != 'p') {
               playerSend.get(i).msg = msg;
               playerSend.get(i).status = 's';
            }
         }
         playerSend.get(policeId).msg = msg;
         playerSend.get(policeId).status = 's';
      } else if (who == 'd') { // 의사
         for (int i = 0; i < playerNum; i++) {
            if (alives.get(i) == false && playerSend.get(i).role != 'd') {
               playerSend.get(i).msg = msg;
               playerSend.get(i).status = 's';
            }
         }
         playerSend.get(doctorId).msg = msg;
         playerSend.get(doctorId).status = 's';
      }

   }

   public static void day(Connection conn, int cnt) throws SQLException { // 낮-플레이어들이 채팅&투표로 용의자 지목

      send_message('a', "\n\n[사회자]: 낮이 되었습니다! \n토론을 통해서 마피아를 찾아보세요. \n'quit'을 누르면 채팅을 그만둡니다.");
      chatting('a');
      voting(conn, cnt);
   }

   public static void night() { // 밤-마피아의 살인 & 의사의 미션
      int mkill = 0; // 마피아가 죽임
      int dhill = 0; // 의사가 살림
      int kill_hill = 0; // 마피아가 죽인사람을 의사가 살림
      int killWho = -1;
      int findWho = -1;
      int saveWho = -2;

      if (mafiaNum == 1) { // 살아 있는 마피아가 한 명이면
         send_message('a', "\n\n[사회자]: 밤이 되었습니다! \n(Only Mafia) 죽일 플레이어 번호를 입력해주세요  (10초)");
         try {
            Thread.sleep(10000);
         } catch (InterruptedException e) {
            e.printStackTrace();
         }
         if (playerNum < 7) { // 원래 마피아가 한 명
            if (playerReceive.get(mafia1Id).received == true) {
               mkill = 1;
               killWho = Integer.parseInt(playerReceive.get(mafia1Id).receivedMsg);
               playerReceive.get(mafia1Id).received = false; // '메시지 받음' 상태를 false로 바꿈
            }
         } else { // 마피아 한 명이 죽은 상태
            if (alives.get(mafia1Id) == true && playerReceive.get(mafia1Id).received == true) {
               mkill = 1;
               killWho = Integer.parseInt(playerReceive.get(mafia1Id).receivedMsg);
               playerReceive.get(mafia1Id).received = false; // '메시지 받음' 상태를 false로
            } else if (alives.get(mafia2Id) == true && playerReceive.get(mafia2Id).received == true) {
               mkill = 1;
               killWho = Integer.parseInt(playerReceive.get(mafia2Id).receivedMsg);
               playerReceive.get(mafia2Id).received = false; // '메시지 받음' 상태를 false로
            }
         }
      } else { // 살아 있는 마피아가 두 명이면
         send_message('a',
               "\n\n[사회자]: 밤이 되었습니다. \n[사회자]: (Only Mafia) 마피아끼리 죽일 플레이어에 대하여 토론을 진행해 주세요. \n'quit'을 누르면 채팅을 그만둡니다.");
         chatting('m'); // 마피아끼리 대화. 마피아 & 죽은 플레이어(말은 하지 못함)만 대화를 볼 수 있음
         send_message('a', "\n\n[사회자]: 죽일 플레이어 번호를 마피아 중 '한 명'만 입력해주세요. (10초)");
         try {
            Thread.sleep(10000);
         } catch (InterruptedException e) {
            e.printStackTrace();
         }
         if (playerReceive.get(mafia1Id).received == true) { // 두 명의 마피아 중 한 명만 죽일 플레이어를 입력해도 됨
            mkill = 1;
            killWho = Integer.parseInt(playerReceive.get(mafia1Id).receivedMsg);
            playerReceive.get(mafia1Id).received = false;
         } else if (playerReceive.get(mafia2Id).received == true) {
            mkill = 1;
            killWho = Integer.parseInt(playerReceive.get(mafia2Id).receivedMsg);
            playerReceive.get(mafia2Id).received = false;
         }

      }

      if (killWho == -1) { // 시간 안에 입력하지 못하면
         send_message('m', "\n\n[사회자]: Time out. 아무도 죽이지 않았습니다.");
         try {
            Thread.sleep(3000);
         } catch (InterruptedException e) {
            e.printStackTrace();
         }

      }

      if (alives.get(policeId)) { // 경찰이 살아있다면, 미션
         send_message('a', "\n\n[사회자]: (Only police) 조사할 플레이어 번호를 입력해주세요 (10초)");
         try {
            Thread.sleep(10000);
         } catch (InterruptedException e) {
            e.printStackTrace();
         }
         if (playerReceive.get(policeId).received == true) { // 경찰로부터 메시지를 받아옴
            findWho = Integer.parseInt(playerReceive.get(policeId).receivedMsg);
            playerReceive.get(policeId).received = false;
         }
         if (findWho == mafia1Id || findWho == mafia2Id) { // 경찰 미션 성공
            send_message('p', "\n\n[사회자]: 선택한 플레이어는 마피아입니다.");
            try {
               Thread.sleep(3000);
            } catch (InterruptedException e) {
               e.printStackTrace();
            }
         } else {
            send_message('p', "\n\n[사회자]: 선택한 플레이어는 마피아가 아닙니다.");
            try {
               Thread.sleep(3000);
            } catch (InterruptedException e) {
               e.printStackTrace();
            }
         }
      }
      if (findWho == -1) { // 시간 안에 입력하지 못하면
         send_message('p', "\n\n[사회자]: Time out. ");
         try {
            Thread.sleep(3000);
         } catch (InterruptedException e) {
            e.printStackTrace();
         }
      }

      if (alives.get(doctorId)) { // 의사가 살아있다면, 미션
         send_message('a', "\n\n[사회자]: (Only Doctor) 살릴 플레이어 번호를 입력해주세요 (10초)");
         try {
            Thread.sleep(10000);
         } catch (InterruptedException e) {
            e.printStackTrace();
         }
         if (playerReceive.get(doctorId).received == true) { // 의사로부터 메시지를 받아옴
            dhill = 1;
            saveWho = Integer.parseInt(playerReceive.get(doctorId).receivedMsg);
            playerReceive.get(doctorId).received = false;
         }
         if (saveWho == killWho) { // 의사 미션 성공
            kill_hill = 1;
            // send_message('a', "\n\n[사회자]: 의사가 마피아가 지목한 플레이어를 살렸습니다. 아무도 죽지 않았습니다.");
            try {
               Thread.sleep(3000);
            } catch (InterruptedException e) {
               e.printStackTrace();
            }
         } else if (saveWho != killWho && saveWho > 0) { // 실패
            alives.put(killWho, false); // 마피아에게 지목된 플레이어를 죽은 상태로 바꿈
            aliveNum--;
            if (killWho == mafia1Id || killWho == mafia2Id)
               mafiaNum--;
//            String msg = "\n\n[사회자]: Player" + Integer.toString(killWho) + " 이 죽었습니다. \n현재 남아있는 플레이어 수 "
//                  + Integer.toString(aliveNum) + ", 현재 남아있는 마피아 수: " + Integer.toString(mafiaNum);
//            send_message('a', msg);
            try {
               Thread.sleep(3000);
            } catch (InterruptedException e) {
               e.printStackTrace();
            }
         }
      } else { // 의사가 이미 죽어있다면, 지목된 플레이어는 살아날 기회 없이 죽음
         alives.put(killWho, false); // 해당 플레이어를 죽은 상태로 바꿈
         aliveNum--;

         if (killWho == mafia1Id || killWho == mafia2Id)
            mafiaNum--;

//         String msg = "\n\n[사회자]: Player" + Integer.toString(killWho) + " 이 죽었습니다. \n현재 남아있는 플레이어 수 "
//               + Integer.toString(aliveNum) + ", 현재 남아있는 마피아 수: " + Integer.toString(mafiaNum);
//         send_message('a', msg);
         try {
            Thread.sleep(3000);
         } catch (InterruptedException e) {
            e.printStackTrace();
         }
      }
      if (saveWho == -2) { // 시간 안에 입력하지 못하면
         send_message('d', "\n\n[사회자]: Time out. ");
         try {
            Thread.sleep(10000);
         } catch (InterruptedException e) {
            e.printStackTrace();
         }
      }

      send_message('a', toString(mkill, dhill, kill_hill, killWho, saveWho));
      try { // 메시지를 보내고(send_message) 일정 시간 쉬어야 모두에게 온전히 전달됨
         Thread.sleep(3000);
      } catch (InterruptedException e) {
         e.printStackTrace();
      }
   }

   public static String toString(int mk, int dh, int kh, int k, int s) {
      String st = null;
      if (mk == 1) {
         if (kh == 1) {
            st = "\n\n[사회자]: 마피아가 시민을 살해하려 했지만 의사가 극적으로 살렸습니다.\n";
         } else if (dh == 1) {
            st = String.format("\n\n[사회자]: 전날 밤에 player%d가 마피아에 의하여 사망하였고\n", k);
            st += String.format("\t의사가 player%d를 살렸습니다.\n", s);
         } else {
            st = String.format("\n\n[사회자]: 전날 밤에 player%d가 마피아에 의하여 사망하였습니다.\n", k);
         }
      } else {
         st = "\n\n[사회자]전날 밤에 아무일도 일어나지 않았습니다.";
      }

      return st;

   }

   public static void main(String[] args) throws ClassNotFoundException, SQLException {
      String msg = null;
      String sql = null;
      ResultSet rs, rs1 = null;
      PreparedStatement pstmt = null;
      Class.forName("com.mysql.cj.jdbc.Driver");
      Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/mafiadb?useSSL=false", "mafia","mafia");
      int cnt = 1;

      sql = String.format("Select count(*) from Players");
      pstmt = conn.prepareStatement(sql);
      rs1 = pstmt.executeQuery(); // Players 테이블의 유무

      if (rs1.next() && rs1.getInt(1) > 0) {

         // CNT 테이블의 행의 개수 == players'N'의 테이블 넘버
         sql = String.format("INSERT INTO CNT(Plyaers_count) values(1)");
         pstmt = conn.prepareStatement(sql);
         pstmt.executeUpdate();

         sql = String.format("Select count(*) from CNT");
         pstmt = conn.prepareStatement(sql);
         rs = pstmt.executeQuery();

         if (rs.next()) {
            sql = String.format("CREATE TABLE Records%s (SELECT*FROM PLAYERS)", rs.getString(1));
            pstmt = conn.prepareStatement(sql);
            pstmt.executeUpdate();

            pstmt = conn.prepareStatement("DROP TABLE Players");
            pstmt.executeUpdate();

            sql = "CREATE TABLE Players (playerID INT NOT NULL AUTO_INCREMENT PRIMARY KEY,userName varchar(20),Job varchar(15))";
            pstmt = conn.prepareStatement(sql);
            pstmt.executeUpdate();

         }
      }

      waiting_room(conn);

      while (mafiaNum != 0 && mafiaNum * 2 != aliveNum) { // 게임 종료 조건) 마피아수=0 or 마피아수:시민수 = 1:1
         day(conn, cnt);
         if (mafiaNum == 0 || mafiaNum * 2 == aliveNum)
            break;
         night();
         cnt++;
      }

      msg = "\n\n[사회자]: 우승팀은 " + ((mafiaNum == 0) ? "시민팀(의사 포함)" : "마피아팀");
      System.out.println(msg);
      send_message('a', msg);

      // 마지막에 모든 유저의 직업을 공개

      int cnt_column = 0;

      pstmt = conn.prepareStatement("SELECT COUNT(*) FROM information_schema.columns WHERE table_name='Players'");
      rs = pstmt.executeQuery();
      if (rs.next()) {
         cnt_column = rs.getInt(1);
      }
      msg = "====================결과=================\n";
      send_message('a', msg);
      try {
         Thread.sleep(1000);
      } catch (InterruptedException e) {
         e.printStackTrace();
      }

      pstmt = conn.prepareStatement("SELECT * FROM Players");
      rs = pstmt.executeQuery();
      while (rs.next()) {
         msg="";
         for (int i = 1; i <= cnt_column; i++) {
            msg += rs.getString(i) + "\t";
         }
         msg+="\n";
         send_message('a', msg);
         try {
            Thread.sleep(500);
         } catch (InterruptedException e) {
            e.printStackTrace();
         }
      }
   }
}