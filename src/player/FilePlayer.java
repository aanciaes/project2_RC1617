package player;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import utils.HTTPUtilities;

public class FilePlayer {

    public static void main(String[] args) throws Exception {
        Map<String, List<Segment>> dataMap = new HashMap<>();

        String urlIndex = "http://localhost:8080";
        URL url = new URL(urlIndex);

        InetAddress serverAddr = InetAddress.getByName(url.getHost());

        Socket sock = new Socket(serverAddr, 8080);

        OutputStream toServer = sock.getOutputStream();
        InputStream fromServer = sock.getInputStream();
        //DataInputStream dis = new DataInputStream(fromServer);
        System.out.println("Connected to server");

        String request = String.format("GET %s HTTP/1.0\r\n" + "User-Agent: X-RC2016\r\n\r\n", "/finding-dory/index.dat");

        toServer.write(request.getBytes());
        System.out.println("Sent request: " + request);
        String answerLine = HTTPUtilities.readLine(fromServer);
        System.out.println("Got answer: " + answerLine);

        while (!answerLine.equals("")) {
            answerLine = HTTPUtilities.readLine(fromServer);
            System.out.println(answerLine);
        }

        BufferedReader in = new BufferedReader(new InputStreamReader(fromServer));
        String content = "";

        while ((content = in.readLine()) != null) {

            if (!content.startsWith(";") && !content.equals("")) {
                if (content.endsWith(".ts")) {
                    dataMap.put(content.trim(), new ArrayList());
                } else {
                    String[] httpReplySegmented = content.split("\\s");
                    List lst = dataMap.get(httpReplySegmented[0]);
                    lst.add(new Segment(httpReplySegmented[0], Integer.parseInt(httpReplySegmented[1]),
                            Integer.parseInt(httpReplySegmented[2]), Integer.parseInt(httpReplySegmented[3])));
                    dataMap.put(httpReplySegmented[0], lst);
                }
            }
        }
        
        /**
         * Player player = JavaFXMediaPlayer.getInstance().setSize(800,
         * 460).mute(false);
         *
         * while (!dis.readUTF().equals("eof")) { dis.readLong();
         * dis.readLong(); byte[] data = new byte[dis.readInt()];
         * dis.readFully(data); player.decode(data); } dis.close();
         */
    }

}
